package eu.kanade.tachiyomi.ui.translation.settings

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.SortedMap
import java.util.TreeMap

class AutoTranslateSourceSelectionScreenModel(
    private val preferences: TranslationPreferences = Injekt.get(),
    private val getLanguagesWithSources: GetLanguagesWithSources = Injekt.get(),
) : StateScreenModel<AutoTranslateSourceSelectionScreenModel.State>(State.Loading) {

    private val languageFilter = MutableStateFlow(ALL_LANGUAGES_KEY)

    init {
        screenModelScope.launch {
            combine(
                getLanguagesWithSources.subscribe(),
                preferences.autoTranslateSelectedSourceIds().changes(),
                preferences.autoTranslateSourceSelectionCustomized().changes(),
                languageFilter,
            ) { items, selectedIds, customized, filter ->
                val effectiveSelectedSourceIds = effectiveSelectedSourceIds(items, selectedIds, customized)
                State.Success(
                    allItems = items,
                    displayedItems = items.filteredBy(filter),
                    selectedLanguage = filter,
                    selectedSourceIds = effectiveSelectedSourceIds,
                    usingDefaultSelection = !customized,
                )
            }
                .catch { throwable ->
                    mutableState.update { State.Error(throwable) }
                }
                .collectLatest { mutableState.value = it }
        }
    }

    fun setLanguageFilter(language: String) {
        languageFilter.value = language
    }

    fun toggleLanguage(language: String) {
        val state = state.value as? State.Success ?: return
        val sources = state.allItems[language].orEmpty()
        val enable = sources.any { it.id.toString() !in state.selectedSourceIds }
        toggleSources(enable, sources)
    }

    fun toggleSource(source: Source) {
        val state = state.value as? State.Success ?: return
        val updated = state.selectedSourceIds.toMutableSet().apply {
            val sourceId = source.id.toString()
            if (!add(sourceId)) {
                remove(sourceId)
            }
        }
        persistSelection(updated)
    }

    fun toggleSources(enable: Boolean, sources: List<Source>) {
        val state = state.value as? State.Success ?: return
        val updated = state.selectedSourceIds.toMutableSet().apply {
            val sourceIds = sources.map { it.id.toString() }
            if (enable) {
                addAll(sourceIds)
            } else {
                removeAll(sourceIds.toSet())
            }
        }
        persistSelection(updated)
    }

    private fun persistSelection(selectedSourceIds: Set<String>) {
        screenModelScope.launch {
            preferences.autoTranslateSelectedSourceIds().set(selectedSourceIds)
            preferences.autoTranslateSourceSelectionCustomized().set(true)
        }
    }

    private fun effectiveSelectedSourceIds(
        items: SortedMap<String, List<Source>>,
        selectedIds: Set<String>,
        customized: Boolean,
    ): Set<String> {
        return if (customized) {
            selectedIds
        } else {
            items.values
                .flatten()
                .asSequence()
                .filter { it.lang == DEFAULT_AUTO_TRANSLATE_LANGUAGE }
                .map { it.id.toString() }
                .toSet()
        }
    }

    private fun SortedMap<String, List<Source>>.filteredBy(language: String): SortedMap<String, List<Source>> {
        if (language == ALL_LANGUAGES_KEY) return this

        val filtered = TreeMap<String, List<Source>>(comparator())
        this[language]?.let { filtered[language] = it }
        return filtered
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Error(val throwable: Throwable) : State

        @Immutable
        data class Success(
            val allItems: SortedMap<String, List<Source>>,
            val displayedItems: SortedMap<String, List<Source>>,
            val selectedLanguage: String,
            val selectedSourceIds: Set<String>,
            val usingDefaultSelection: Boolean,
        ) : State {
            val isEmpty: Boolean
                get() = allItems.isEmpty()
        }
    }

    companion object {
        const val ALL_LANGUAGES_KEY = "*"
        const val DEFAULT_AUTO_TRANSLATE_LANGUAGE = "ja"
    }
}
