package com.niki914.zafiro.app.localai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.niki914.zafiro.app.R
import com.niki914.zafiro.chat.routing.ModelArtifact
import com.niki914.zafiro.chat.routing.ModelTransferPolicy
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

enum class LocalModelPrompt { Hidden, Install, Choose }

/** UI sends intents; downloads and verified atomic installation belong to this lifecycle owner. */
class LocalAiViewModel(application: Application) : AndroidViewModel(application) {
    private val directory = File(application.noBackupFilesDir, "local-models").apply { mkdirs() }
    private val jobs = mutableMapOf<String, Job>()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followSslRedirects(false).build()
    private val current = MutableStateFlow(ModelArtifact.downloadable.associate { artifact -> artifact.id to
        ModelDownloadState(part(artifact).length()) })
    val state = current.asStateFlow()
    private val preferences = application.getSharedPreferences("local-ai", 0)
    private val wifi = MutableStateFlow(preferences.getBoolean("wifi-only", true))
    val wifiOnly = wifi.asStateFlow()
    private val prompt = MutableStateFlow(LocalModelPrompt.Hidden)
    val startupPrompt = prompt.asStateFlow()
    private val scanning = MutableStateFlow(true)
    val discovering = scanning.asStateFlow()
    private val importMessage = MutableStateFlow<Int?>(null)
    val importStatus = importMessage.asStateFlow()
    private val settingsVisible = MutableStateFlow(false)
    val showStartupSettings = settingsVisible.asStateFlow()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            ModelArtifact.androidBrainArtifacts.forEach { artifact ->
                val installed = artifact.verify(model(artifact))
                withContext(Dispatchers.Main) { update(artifact, ModelDownloadState(part(artifact).length(), installed)) }
            }
        }
        viewModelScope.launch {
            val found = try { withContext(Dispatchers.IO) {
                val roots = listOf(directory, application.filesDir) + application.getExternalFilesDirs(null).filterNotNull()
                LocalModelDiscovery.discover(roots).also { discovered ->
                    for ((artifact, source) in discovered) {
                        if (source.canonicalPath != model(artifact).canonicalPath) {
                            if (!ModelTransferPolicy.storageAvailable(directory.usableSpace, 0, artifact.bytes)) continue
                            val temp = File(directory, "${artifact.id}.discovered")
                            source.copyTo(temp, overwrite = true)
                            if (artifact.verify(temp)) Files.move(temp.toPath(), model(artifact).toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                            else temp.delete()
                        }
                    }
                }
            } } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { importMessage.value = R.string.local_ai_discovery_failed; emptyMap() }
            ModelArtifact.candidates.forEach { artifact ->
                val verified = artifact in found && model(artifact).isFile
                update(artifact, ModelDownloadState(part(artifact).length(), verified))
                if (!verified && LocalCommandRuntime.selectedModel.value == artifact.id) LocalCommandRuntime.forget(artifact)
            }
            scanning.value = false
            if (!preferences.getBoolean("startup-model-choice-dismissed", false) &&
                LocalCommandRuntime.selectedModel.value == null) {
                prompt.value = if (current.value.values.any { it.installed }) LocalModelPrompt.Choose else LocalModelPrompt.Install
            }
        }
    }
    fun dismissStartupPrompt() {
        prompt.value = LocalModelPrompt.Hidden
        preferences.edit().putBoolean("startup-model-choice-dismissed", true).apply()
    }
    fun openStartupModels() {
        dismissStartupPrompt()
        settingsVisible.value = true
    }
    fun closeStartupModels() { settingsVisible.value = false }
    fun importModel(uri: Uri) {
        if (scanning.value || jobs.values.any { !it.isCompleted }) return
        jobs["import"] = viewModelScope.launch {
            importMessage.value = R.string.local_ai_importing
            val temp = File(directory, "selected-model.importing")
            try {
                val artifact = withContext(Dispatchers.IO) {
                    val largest = ModelArtifact.candidates.maxOf { it.bytes }
                    if (directory.usableSpace < largest + 64L * 1024 * 1024) throw StorageException()
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var count = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                count += size
                                if (count > largest) throw IntegrityException()
                                output.write(buffer, 0, size)
                            }
                            output.fd.sync()
                        }
                    } ?: throw IntegrityException()
                    val known = ModelArtifact.candidates.firstOrNull { it.bytes == temp.length() && it.verify(temp) }
                        ?: throw IntegrityException()
                    LocalCommandRuntime.forget(known)
                    Files.move(temp.toPath(), model(known).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    known
                }
                update(artifact, ModelDownloadState(artifact.bytes, true, status = R.string.local_ai_installed))
                importMessage.value = R.string.local_ai_imported
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                importMessage.value = if (failure is StorageException) R.string.local_ai_storage else R.string.local_ai_import_unsupported
            } finally { withContext(NonCancellable + Dispatchers.IO) { temp.delete() } }
        }
    }
    fun setWifiOnly(enabled: Boolean) { wifi.value = enabled; preferences.edit().putBoolean("wifi-only", enabled).apply() }
    fun downloadAndroidBrain() {
        if (jobs["android-brain-bundle"]?.isCompleted == false) return
        jobs["android-brain-bundle"] = viewModelScope.launch {
            for (artifact in ModelArtifact.androidBrainArtifacts) {
                if (current.value.getValue(artifact.id).installed) continue
                download(artifact)
                jobs[artifact.id]?.join()
                if (!current.value.getValue(artifact.id).installed) break
            }
        }
    }
    fun pauseAndroidBrain() {
        jobs["android-brain-bundle"]?.cancel()
        ModelArtifact.androidBrainArtifacts.forEach(::pause)
    }
    fun setAndroidBrain(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) GuiOwlRuntime.enable() else GuiOwlRuntime.disable()
        }
    }
    fun testAndroidBrain() {
        if (jobs["android-brain-test"]?.isCompleted == false) return
        jobs["android-brain-test"] = viewModelScope.launch {
            try {
                if (!GuiOwlRuntime.enable()) return@launch
                val image = com.niki914.zafiro.chat.agentic.accessibility.AccessibilityController.captureScreenImage().getOrNull()
                if (image == null) { GuiOwlRuntime.status.value = "SCREEN_UNAVAILABLE"; return@launch }
                try {
                    val targets = GuiOwlRuntime.detect(image)
                    GuiOwlRuntime.status.value = if (targets.isEmpty()) "OUTPUT_VALIDATION_FAILED" else "SMOKE_TEST_PASSED"
                } finally { image.recycle() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (GuiOwlRuntime.status.value == "READY") GuiOwlRuntime.status.value = "OUTPUT_VALIDATION_FAILED"
            }
        }
    }
    private fun model(a: ModelArtifact) = File(directory, "${a.id}.${a.extension}")
    private fun part(a: ModelArtifact) = File(directory, "${a.id}.partial")
    private fun update(a: ModelArtifact, value: ModelDownloadState) { current.value = current.value + (a.id to value) }

    fun pause(a: ModelArtifact) { jobs[a.id]?.cancel() }
    fun delete(a: ModelArtifact) {
        val previous = jobs[a.id]
        val deletion = viewModelScope.launch(start = CoroutineStart.LAZY) {
            previous?.cancelAndJoin()
            LocalCommandRuntime.forget(a)
            if (a in ModelArtifact.androidBrainArtifacts) GuiOwlRuntime.disable()
            withContext(Dispatchers.IO) { model(a).delete(); part(a).delete() }
            update(a, ModelDownloadState())
        }
        jobs[a.id] = deletion
        deletion.start()
    }
    fun setCloudFallback(enabled: Boolean) {
        LocalIntelligence.allowCloudFallback.value = enabled
        preferences.edit().putBoolean("cloud-fallback", enabled).apply()
    }
    fun setTrustedScripts(enabled: Boolean) {
        com.niki914.zafiro.chat.routing.NetworkPolicy.trustedScripts.value = enabled
        preferences.edit().putBoolean("trusted-scripts", enabled).apply()
    }
    fun setMessageSending(enabled: Boolean) {
        LocalIntelligence.allowMessageSending.value = enabled
        preferences.edit().putBoolean("allow-message-sending", enabled).apply()
    }
    fun setMode(mode: IntelligenceMode) {
        LocalIntelligence.mode.value = mode
        getApplication<Application>().getSharedPreferences("local-ai", 0).edit().putString("mode", mode.name).apply()
        if (mode == IntelligenceMode.FastLocal && !preferences.contains("cloud-fallback")) setCloudFallback(false)
        if (mode == IntelligenceMode.CloudQuality) LocalCommandRuntime.unload()
    }
    fun benchmark(a: ModelArtifact) {
        if (a !in ModelArtifact.candidates) return
        if (scanning.value || jobs[a.id]?.isCompleted == false || !model(a).isFile) return
        jobs[a.id] = viewModelScope.launch {
            update(a, current.value.getValue(a.id).copy(status = R.string.local_ai_benchmark_running))
            try {
                val result = LocalCommandRuntime.benchmark(getApplication(), a)
                update(a, current.value.getValue(a.id).copy(status = if (result.correct == result.total) R.string.local_ai_ready else R.string.local_ai_accuracy_failed, benchmark = result))
                if (result.correct == result.total) { setMode(IntelligenceMode.FastLocal); dismissStartupPrompt() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { update(a, current.value.getValue(a.id).copy(status = R.string.local_ai_benchmark_failed)) }
            catch (_: LinkageError) { update(a, current.value.getValue(a.id).copy(status = R.string.local_ai_benchmark_failed)) }
        }
    }
    @OptIn(InternalCoroutinesApi::class)
    fun download(a: ModelArtifact) {
        if (scanning.value || jobs[a.id]?.isCompleted == false || jobs["import"]?.isCompleted == false) return
        jobs[a.id] = viewModelScope.launch {
            update(a, ModelDownloadState(part(a).length(), model(a).isFile, true))
            try {
                withContext(Dispatchers.IO) {
                    if (a in ModelArtifact.androidBrainArtifacts) GuiOwlRuntime.disable()
                    if (wifi.value) {
                        val connectivity = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                        if (network?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true) throw WifiException()
                    }
                    val temp = part(a)
                    if (temp.length() > a.bytes) temp.delete()
                    if (!ModelTransferPolicy.storageAvailable(directory.usableSpace, temp.length(), a.bytes)) throw StorageException()
                    if (temp.length() < a.bytes) {
                        val offset = temp.length()
                        val call = client.newCall(Request.Builder().url(a.url).header("Range", "bytes=$offset-").build())
                        val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
                        try {
                            call.execute().use { response ->
                                if (!response.request.url.isHttps || (response.code != 200 && response.code != 206)) throw DownloadException()
                                val resumed = ModelTransferPolicy.resume(response.code, response.header("Content-Range"), offset, a.bytes)
                                    ?: throw DownloadException()
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
                jobs.remove(a.id)
                if (a in ModelArtifact.candidates) benchmark(a)
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
