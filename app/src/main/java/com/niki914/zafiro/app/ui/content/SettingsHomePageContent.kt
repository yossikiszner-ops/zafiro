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
    var showGlass by remember { mutableStateOf(false) }
    if (showGlass) GlassAppearanceSettings { showGlass = false }
    val groupsById = uiState.sections
        .flatMap { it.groups }
        .associateBy { it.name }

    SettingsSpecPageContent(
        spec = settingsHomePageSpec(uiState),
        onAction = { action ->
            when (action) {
                is SettingsRowAction.Navigate -> if (action.id == "zafiro-glass") showGlass = true else groupsById[action.id]?.let(onOpenGroup)
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
            title = "ZAFIRO GLASS",
            layout = SettingsSectionLayout.GroupedCard,
            rows = listOf(SettingsRowSpec.Navigation(id = "zafiro-glass", title = stringResource(R.string.glass_studio))),
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
