package com.niki914.zafiro.app.localai

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import com.niki914.uikit.infra.LiquidDialog
import com.niki914.uikit.infra.nav.pageViewModel
import com.niki914.zafiro.app.R

@Composable
internal fun rememberLocalAiModel(): LocalAiViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { ViewModelProvider.AndroidViewModelFactory(application) }
    return pageViewModel(factory = factory)
}

@Composable
fun LocalAiStartup() {
    val model = rememberLocalAiModel()
    val prompt by model.startupPrompt.collectAsState()
    val settings by model.showStartupSettings.collectAsState()
    LiquidDialog(
        visible = prompt != LocalModelPrompt.Hidden,
        onDismissRequest = model::dismissStartupPrompt,
        title = { Text(stringResource(R.string.local_ai_startup_title)) },
        text = { Text(stringResource(if (prompt == LocalModelPrompt.Choose)
            R.string.local_ai_startup_found else R.string.local_ai_startup_missing)) },
        actions = {
            TextButton(onClick = model::openStartupModels) {
                Text(stringResource(if (prompt == LocalModelPrompt.Choose) R.string.local_ai_choose_model else R.string.local_ai_install_model))
            }
            TextButton(onClick = model::dismissStartupPrompt) { Text(stringResource(R.string.local_ai_later)) }
        },
    )
    if (settings) LocalAiSettings(model::closeStartupModels)
}
