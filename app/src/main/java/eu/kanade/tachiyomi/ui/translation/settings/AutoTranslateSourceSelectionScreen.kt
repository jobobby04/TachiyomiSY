package eu.kanade.tachiyomi.ui.translation.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.InfoWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.translation.settings.AutoTranslateSourceSelectionScreenModel.Companion.ALL_LANGUAGES_KEY
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class AutoTranslateSourceSelectionScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { AutoTranslateSourceSelectionScreenModel() }
        val state by screenModel.state.collectAsState()

        when (val currentState = state) {
            is AutoTranslateSourceSelectionScreenModel.State.Loading -> LoadingScreen()
            is AutoTranslateSourceSelectionScreenModel.State.Error -> {
                LaunchedEffect(Unit) {
                    context.toast(MR.strings.internal_error)
                    navigator.pop()
                }
            }
            is AutoTranslateSourceSelectionScreenModel.State.Success -> {
                AutoTranslateSourceSelectionScreenContent(
                    state = currentState,
                    navigateUp = navigator::pop,
                    onClickLanguageFilter = screenModel::setLanguageFilter,
                    onClickLanguageToggle = screenModel::toggleLanguage,
                    onClickSource = screenModel::toggleSource,
                )
            }
        }
    }
}

@Composable
private fun AutoTranslateSourceSelectionScreenContent(
    state: AutoTranslateSourceSelectionScreenModel.State.Success,
    navigateUp: () -> Unit,
    onClickLanguageFilter: (String) -> Unit,
    onClickLanguageToggle: (String) -> Unit,
    onClickSource: (Source) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.pref_translation_auto_translate_sources_title),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.source_filter_empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        val languageEntries = linkedMapOf(
            ALL_LANGUAGES_KEY to stringResource(MR.strings.pref_translation_auto_translate_sources_all_languages),
        ) + state.allItems.keys.associateWith {
            LocaleHelper.getSourceDisplayName(it, LocalContext.current)
        }

        FastScrollLazyColumn(contentPadding = contentPadding) {
            item(key = "language-filter", contentType = "language-filter") {
                ListPreferenceWidget(
                    value = state.selectedLanguage,
                    title = stringResource(MR.strings.pref_translation_auto_translate_sources_filter_language),
                    subtitle = languageEntries[state.selectedLanguage],
                    icon = null,
                    entries = languageEntries,
                    onValueChange = onClickLanguageFilter,
                )
            }

            if (state.usingDefaultSelection) {
                item(key = "default-info", contentType = "default-info") {
                    InfoWidget(
                        text = stringResource(MR.strings.pref_translation_auto_translate_sources_default_summary),
                    )
                }
            }

            autoTranslateSourceItems(
                contentPadding = contentPadding,
                state = state,
                onClickLanguageToggle = onClickLanguageToggle,
                onClickSource = onClickSource,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.autoTranslateSourceItems(
    contentPadding: PaddingValues,
    state: AutoTranslateSourceSelectionScreenModel.State.Success,
    onClickLanguageToggle: (String) -> Unit,
    onClickSource: (Source) -> Unit,
) {
    state.displayedItems.forEach { (language, sources) ->
        item(key = "language-$language", contentType = "language-header") {
            val enabledCount = sources.count { it.id.toString() in state.selectedSourceIds }
            SwitchPreferenceWidget(
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                title = LocaleHelper.getSourceDisplayName(language, LocalContext.current),
                subtitle = stringResource(
                    MR.strings.pref_translation_auto_translate_sources_language_count,
                    enabledCount,
                    sources.size,
                ),
                checked = enabledCount == sources.size && sources.isNotEmpty(),
                onCheckedChanged = { onClickLanguageToggle(language) },
            )
        }

        items(
            items = sources,
            key = { "translation-source-${it.key()}" },
            contentType = { "translation-source-item" },
        ) { source ->
            BaseSourceItem(
                source = source,
                showLanguageInContent = false,
                onClickItem = { onClickSource(source) },
                action = {
                    Checkbox(
                        checked = source.id.toString() in state.selectedSourceIds,
                        onCheckedChange = null,
                    )
                },
            )
        }
    }
}
