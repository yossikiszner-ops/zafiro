package com.niki914.zafiro.app.localai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.niki914.zafiro.app.R
import com.niki914.zafiro.chat.routing.ModelArtifact
import com.niki914.zafiro.chat.routing.IntelligenceMode
import com.niki914.zafiro.chat.routing.LocalIntelligence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

data class ModelDownloadState(val completed: Long = 0, val installed: Boolean = false,
    val downloading: Boolean = false, val status: Int? = null, val benchmark: LocalBenchmark? = null)

/** UI sends intents; downloads and verified atomic installation belong to this lifecycle owner. */
class LocalAiViewModel(application: Application) : AndroidViewModel(application) {
    private val directory = File(application.noBackupFilesDir, "local-models").apply { mkdirs() }
    private val jobs = mutableMapOf<String, Job>()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followSslRedirects(false).build()
    private val current = MutableStateFlow(ModelArtifact.candidates.associate { artifact -> artifact.id to
        ModelDownloadState(part(artifact).length(), model(artifact).isFile) })
    val state = current.asStateFlow()
    private val preferences = application.getSharedPreferences("local-ai", 0)
    private val wifi = MutableStateFlow(preferences.getBoolean("wifi-only", true))
    val wifiOnly = wifi.asStateFlow()
    fun setWifiOnly(enabled: Boolean) { wifi.value = enabled; preferences.edit().putBoolean("wifi-only", enabled).apply() }
    private fun model(a: ModelArtifact) = File(directory, "${a.id}.litertlm")
    private fun part(a: ModelArtifact) = File(directory, "${a.id}.partial")
    private fun update(a: ModelArtifact, value: ModelDownloadState) { current.value = current.value + (a.id to value) }

    fun pause(a: ModelArtifact) { jobs.remove(a.id)?.cancel() }
    fun delete(a: ModelArtifact) {
        viewModelScope.launch {
            jobs.remove(a.id)?.cancelAndJoin()
            LocalCommandRuntime.unload()
            withContext(Dispatchers.IO) { model(a).delete(); part(a).delete() }
            update(a, ModelDownloadState())
        }
    }
    fun setMode(mode: IntelligenceMode) {
        LocalIntelligence.mode.value = mode
        getApplication<Application>().getSharedPreferences("local-ai", 0).edit().putString("mode", mode.name).apply()
        if (mode == IntelligenceMode.CloudQuality) LocalCommandRuntime.unload()
    }
    fun benchmark(a: ModelArtifact) {
        if (jobs[a.id]?.isActive == true || !model(a).isFile) return
        jobs[a.id] = viewModelScope.launch {
            update(a, current.value.getValue(a.id).copy(status = R.string.local_ai_benchmark_running))
            try {
                val result = LocalCommandRuntime.benchmark(getApplication(), a)
                update(a, current.value.getValue(a.id).copy(status = null, benchmark = result))
                if (result.correct == result.total && result.warmMs <= 1500) setMode(IntelligenceMode.FastLocal)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { update(a, current.value.getValue(a.id).copy(status = R.string.local_ai_benchmark_failed)) }
            catch (_: LinkageError) { update(a, current.value.getValue(a.id).copy(status = R.string.local_ai_benchmark_failed)) }
        }
    }
    @OptIn(InternalCoroutinesApi::class)
    fun download(a: ModelArtifact) {
        if (jobs[a.id]?.isActive == true) return
        jobs[a.id] = viewModelScope.launch {
            update(a, ModelDownloadState(part(a).length(), model(a).isFile, true))
            try {
                withContext(Dispatchers.IO) {
                    if (wifi.value) {
                        val connectivity = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                        if (network?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true) throw WifiException()
                    }
                    val temp = part(a)
                    if (temp.length() > a.bytes) temp.delete()
                    if (directory.usableSpace < a.bytes - temp.length() + 64 * 1024 * 1024L) throw StorageException()
                    if (temp.length() < a.bytes) {
                        val offset = temp.length()
                        val call = client.newCall(Request.Builder().url(a.url).header("Range", "bytes=$offset-").build())
                        val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
                        try {
                            call.execute().use { response ->
                                if (!response.request.url.isHttps || (response.code != 200 && response.code != 206)) throw DownloadException()
                                val resumed = response.code == 206
                                if (resumed && response.header("Content-Range")?.startsWith("bytes $offset-") != true) throw DownloadException()
                                var count = if (resumed) offset else 0L
                                response.body?.byteStream()?.use { input ->
                                    FileOutputStream(temp, resumed).use { output ->
                                        val buffer = ByteArray(128 * 1024)
                                        var lastProgress = 0L
                                        while (true) {
                                            currentCoroutineContext().ensureActive()
                                            val read = input.read(buffer)
                                            if (read < 0) break
                                            count += read
                                            if (count > a.bytes) { temp.delete(); throw DownloadException() }
                                            output.write(buffer, 0, read)
                                            if (count - lastProgress >= 1024 * 1024) {
                                                lastProgress = count
                                                withContext(Dispatchers.Main) { update(a, ModelDownloadState(count, model(a).isFile, true)) }
                                            }
                                        }
                                        output.fd.sync()
                                    }
                                } ?: throw DownloadException()
                            }
                        } finally { cancellation.dispose() }
                    }
                    currentCoroutineContext().ensureActive()
                    if (!a.verify(temp)) { temp.delete(); throw IntegrityException() }
                    currentCoroutineContext().ensureActive()
                    Files.move(temp.toPath(), model(a).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }
                update(a, ModelDownloadState(a.bytes, true, status = R.string.local_ai_installed))
            } catch (cancel: CancellationException) {
                update(a, ModelDownloadState(part(a).length(), model(a).isFile, status = R.string.local_ai_paused))
                throw cancel
            } catch (error: Exception) {
                update(a, ModelDownloadState(part(a).length(), model(a).isFile, status = when(error) {
                    is WifiException -> R.string.local_ai_wifi_required
                    is StorageException -> R.string.local_ai_storage
                    is IntegrityException -> R.string.local_ai_integrity
                    else -> R.string.local_ai_download_error
                }))
            }
        }
    }
    private class WifiException : Exception()
    private class StorageException : Exception()
    private class IntegrityException : Exception()
    private class DownloadException : Exception()
}
