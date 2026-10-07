package com.niki914.zafiro.app.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.niki914.zafiro.app.BuildConfig
import com.niki914.zafiro.app.R
import com.niki914.zafiro.repo.AppUpdateInstaller
import com.niki914.zafiro.repo.UpdateCheckHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun AppUpdateSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by UpdateCheckHolder.result.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.app_update_title)) }, text = {
        Column {
            Text(BuildConfig.VERSION_NAME)
            Text(stringResource(when {
                busy -> R.string.app_update_busy
                failed -> R.string.app_update_failed
                file != null -> R.string.app_update_ready
                result?.hasUpdate == true && result?.apkUrl != null -> R.string.app_update_available
                checked -> R.string.app_update_no_release
                else -> R.string.app_update_hint
            }))
            result?.remoteVersion?.let { Text(it) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true; failed = false
                try {
                    val ready = file
                    if (ready != null) {
                        if (AppUpdateInstaller.canInstall(context)) AppUpdateInstaller.install(context, ready)
                        else AppUpdateInstaller.allowInstallation(context)
                    } else if (checked && result?.hasUpdate == true && result?.apkUrl != null) {
                        file = AppUpdateInstaller.downloadVerified(context, result!!.apkUrl!!)
                    } else {
                        UpdateCheckHolder.refresh(BuildConfig.VERSION_NAME); checked = true
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(stringResource(when {
            file != null -> R.string.app_update_install
            checked && result?.hasUpdate == true && result?.apkUrl != null -> R.string.app_update_download
            else -> R.string.app_update_check
        })) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_dialog_cancel)) } })
}
