package com.niki914.zafiro.app.ui.content

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.niki914.zafiro.app.R
import com.niki914.zafiro.app.voice.GlassAppearanceSettings
import com.niki914.zafiro.app.overlay.AgentCursorSettings
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.niki914.uikit.base.BaseTheme
import com.niki914.uikit.infra.ProvideLiquidScreenContentForPreview
import com.niki914.uikit.infra.component.settings.SettingsPageSpec
import com.niki914.uikit.infra.component.settings.SettingsRowAction
import com.niki914.uikit.infra.component.settings.SettingsRowSpec
import com.niki914.uikit.infra.component.settings.SettingsSectionLayout
import com.niki914.uikit.infra.component.settings.SettingsSectionSpec
import com.niki914.uikit.infra.component.settings.SettingsSpecPageContent
import com.niki914.uikit.infra.nav.pageViewModel
import com.niki914.zafiro.app.ui.model.SettingsUiState
import com.niki914.zafiro.app.ui.model.SettingsViewModel
import com.niki914.zafiro.app.ui.model.buildSettingsUiState
import com.niki914.zafiro.app.ui.nav.ZafiroSettingsGroup

@Composable
fun SettingsHomePageContent(
    onOpenGroup: (ZafiroSettingsGroup) -> Unit,
) {
    val viewModel = pageViewModel<SettingsViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    SettingsHomePageContentBody(
        uiState = uiState,
        onOpenGroup = onOpenGroup,
    )
}

@Composable
private fun SettingsHomePageContentBody(
    uiState: SettingsUiState,
    onOpenGroup: (ZafiroSettingsGroup) -> Unit,
) {
    var showVoice by remember { mutableStateOf(false) }
    if (showVoice) com.niki914.zafiro.app.voice.HomeVoiceControls({}, {}, settingsOnly = true, onSettingsDismiss = { showVoice = false })
    var showUpdate by remember { mutableStateOf(false) }
    if (showUpdate) AppUpdateSettings { showUpdate = false }
    var showCursor by remember { mutableStateOf(false) }
    if (showCursor) AgentCursorSettings { showCursor = false }
    var showGlass by remember { mutableStateOf(false) }
    if (showGlass) GlassAppearanceSettings { showGlass = false }
    val groupsById = uiState.sections
        .flatMap { it.groups }
        .associateBy { it.name }

    SettingsSpecPageContent(
        spec = settingsHomePageSpec(uiState),
        onAction = { action ->
            when (action) {
                is SettingsRowAction.Navigate -> when (action.id) {
                    "zafiro-voice" -> showVoice = true
                    "zafiro-glass" -> showGlass = true
                    "zafiro-cursor" -> showCursor = true
                    "zafiro-update" -> showUpdate = true
                    else -> groupsById[action.id]?.let(onOpenGroup)
                }
                is SettingsRowAction.Click -> Unit
                is SettingsRowAction.ToggleChanged -> Unit
            }
        },
    )
}

@Composable
private fun settingsHomePageSpec(
    uiState: SettingsUiState,
): SettingsPageSpec {
    return SettingsPageSpec(
        sections = listOf(SettingsSectionSpec(
            title = "Zafiro",
            layout = SettingsSectionLayout.GroupedCard,
            rows = listOf(
                SettingsRowSpec.Navigation(id = "zafiro-voice", title = stringResource(R.string.voice_settings)),
                SettingsRowSpec.Navigation(id = "zafiro-glass", title = stringResource(R.string.glass_studio)),
                SettingsRowSpec.Navigation(id = "zafiro-cursor", title = stringResource(R.string.cursor_settings)),
                SettingsRowSpec.Navigation(id = "zafiro-update", title = stringResource(R.string.app_update_title)),
            ),
        )) + uiState.sections.map { section ->
            SettingsSectionSpec(
                title = stringResource(section.titleRes),
                layout = SettingsSectionLayout.GroupedCard,
                rows = section.groups.map { group ->
                    val summary = stringResource(group.summaryRes)
                    SettingsRowSpec.Navigation(
                        id = group.name,
                        title = stringResource(group.titleRes),
                        summary = summary.takeIf { it.isNotBlank() },
                    )
                },
            )
        },
    )
}

@Preview(name = "Settings Home", showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun SettingsHomePageContentPreview() {
    BaseTheme(darkTheme = false, dynamicColor = false) {
        Surface {
            ProvideLiquidScreenContentForPreview(topPadding = 0.dp) {
                SettingsHomePageContentBody(
                    uiState = buildSettingsUiState(hiddenGroups = emptySet()),
                    onOpenGroup = {},
                )
            }
        }
    }
}
