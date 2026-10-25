package exh.ui.smartsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * SY-specific file
 */
@AssistedInject
class SmartSearchViewModel(
    @Assisted sourceId: Long,
    @Assisted private val config: SourcesScreen.SmartSearchConfig,
    private val networkToLocalManga: NetworkToLocalManga,
    sourceManager: SourceManager,
) : ViewModel() {

    val source: StateFlow<Source?>
        field = MutableStateFlow(null)

    val state: StateFlow<SearchResults?>
        field = MutableStateFlow(null)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long, config: SourcesScreen.SmartSearchConfig): SmartSearchViewModel
    }
    private val smartSearchEngine = SmartSourceSearchEngine(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            source.value = sourceManager.getOrStub(sourceId)

            val result = try {
                val resultManga = smartSearchEngine.deepSearch(source.value!!, config.origTitle)
                if (resultManga != null) {
                    val localManga = networkToLocalManga(resultManga)
                    SearchResults.Found(localManga)
                } else {
                    SearchResults.NotFound
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                } else {
                    SearchResults.Error
                }
            }

            state.value = result
        }
    }

    sealed class SearchResults {
        data class Found(val manga: Manga) : SearchResults()
        data object NotFound : SearchResults()
        data object Error : SearchResults()
    }
}
