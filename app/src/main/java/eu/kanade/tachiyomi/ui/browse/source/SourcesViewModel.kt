package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import java.util.TreeMap
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class SourcesViewModel(
    private val getEnabledSources: GetEnabledSources,
    private val toggleSource: ToggleSource,
    private val toggleSourcePin: ToggleSourcePin,
    // SY -->
    private val uiPreferences: UiPreferences,
    private val getSourceCategories: GetSourceCategories,
    private val getShowLatest: GetShowLatest,
    private val toggleExcludeFromDataSaver: ToggleExcludeFromDataSaver,
    private val setSourceCategories: SetSourceCategories,
    private val sourcePreferences: SourcePreferences,
    @Assisted val smartSearchConfig: SourcesScreen.SmartSearchConfig?,
    // SY <--
) : ViewModel() {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val enabledSources = getEnabledSources.subscribe()
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(Event.FailedFetchingSources)
        }
        .map(::toSourceUiModels)

    // SY -->
    private val sourceCategories = getSourceCategories.subscribe()
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(Event.FailedFetchingSources)
        }
    // SY <--

    val state: StateFlow<State> = combine(
        enabledSources,
        dialog,
        // SY -->
        getShowLatest.subscribe(smartSearchConfig != null),
        combine(
            sourceCategories,
            sourcePreferences.dataSaver.changes(),
            uiPreferences.useNewSourceNavigation.changes(),
        ) { categories, dataSaver, useNewSourceNavigation ->
            Triple(categories, dataSaver != DataSaver.NONE, useNewSourceNavigation)
        },
        // SY <--
    ) { items, dialog, showLatest, (categories, dataSaverEnabled, useNewSourceNavigation) ->
        State(
            dialog = dialog,
            isLoading = false,
            items = items,
            // SY -->
            showLatest = showLatest,
            categories = categories,
            dataSaverEnabled = dataSaverEnabled,
            showPin = smartSearchConfig == null,
            useNewSourceNavigation = useNewSourceNavigation,
            // SY <--
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun toSourceUiModels(sources: List<Source>): List<SourceUiModel> {
        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            // Sources without a lang defined will be placed at the end
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                // SY -->
                d1.startsWith(CATEGORY_KEY_PREFIX) && !d2.startsWith(CATEGORY_KEY_PREFIX) -> -1
                d2.startsWith(CATEGORY_KEY_PREFIX) && !d1.startsWith(CATEGORY_KEY_PREFIX) -> 1
                // SY <--
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = sources.groupByTo(map) {
            when {
                // SY -->
                it.category != null -> "$CATEGORY_KEY_PREFIX${it.category}"
                // SY <--
                it.isUsedLast -> LAST_USED_KEY
                Pin.Actual in it.pin -> PINNED_KEY
                else -> it.lang
            }
        }

        return byLang.flatMap {
            listOf(
                SourceUiModel.Header(
                    // SY -->
                    it.key.removePrefix(CATEGORY_KEY_PREFIX),
                    it.value.firstOrNull()?.category != null,
                    // SY <--
                ),
                *it.value.map { source ->
                    SourceUiModel.Item(source)
                }.toTypedArray(),
            )
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    // SY -->
    fun toggleExcludeFromDataSaver(source: Source) {
        toggleExcludeFromDataSaver.await(source)
    }

    fun setSourceCategories(source: Source, categories: List<String>) {
        setSourceCategories.await(source, categories)
    }

    fun showSourceCategoriesDialog(source: Source) {
        dialog.update { Dialog.SourceCategories(source) }
    }
    // SY <--

    fun showSourceDialog(source: Source) {
        dialog.update {
            // SY -->
            Dialog.SourceLongClick(source)
            // SY <--
        }
    }

    fun closeDialog() {
        dialog.update { null }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    // SY -->
    sealed class Dialog {
        data class SourceLongClick(val source: Source) : Dialog()
        data class SourceCategories(val source: Source) : Dialog()
    }
    // SY <--

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: List<SourceUiModel> = emptyList(),
        // SY -->
        val categories: List<String> = emptyList(),
        val showPin: Boolean = true,
        val showLatest: Boolean = false,
        val dataSaverEnabled: Boolean = false,
        val useNewSourceNavigation: Boolean = false,
        // SY <--
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"

        // SY -->
        const val CATEGORY_KEY_PREFIX = "category-"
        // SY <--
    }

    // SY -->
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(smartSearchConfig: SourcesScreen.SmartSearchConfig? = null): SourcesViewModel
    }
    // SY <--
}
