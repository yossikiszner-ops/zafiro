package com.niki914.zafiro.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

internal object UpdatePackagePolicy {
    fun accepts(packageName: String, installedPackage: String, version: Long, installedVersion: Long,
                signers: Set<String>, installedSigners: Set<String>): Boolean =
        packageName == installedPackage && version > installedVersion && signers.isNotEmpty() && signers == installedSigners
}

/** Downloads to the existing private cache; only Android's installer can perform the update. */
internal object AppUpdateInstaller {
    suspend fun downloadVerified(context: Context, url: String): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://github.com/yossikiszner-ops/zafiro/releases/download/"))
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "zafiro-update.apk")
        val temporary = File(directory, "zafiro-update.part")
        try {
            SharedHttp.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful)
                val body = checkNotNull(response.body)
                check(body.contentLength() <= 200L * 1024 * 1024)
                body.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(32768); var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        total += count; check(total <= 200L * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                } }
            }
            coroutineContext.ensureActive()
            check(verified(context, temporary)) { "Update package identity, version or signature does not match" }
            if (target.exists()) check(target.delete())
            check(temporary.renameTo(target))
            target
        } finally { temporary.delete() }
    }
    @Suppress("DEPRECATION")
    private fun verified(context: Context, file: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun signers(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        fun version(info: android.content.pm.PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return UpdatePackagePolicy.accepts(archive.packageName, context.packageName, version(archive), version(installed), signers(archive), signers(installed))
    }
    fun canInstall(context: Context) = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    fun allowInstallation(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
    }
    fun install(context: Context, file: File) {
        check(verified(context, file))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
