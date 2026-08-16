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
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class SmartSearchViewModel(
    @Assisted sourceId: Long,
    @Assisted private val config: SourcesScreen.SmartSearchConfig,
    private val networkToLocalManga: NetworkToLocalManga,
    sourceManager: SourceManager,
) : ViewModel() {

    val state: StateFlow<SearchResults?>
        field = MutableStateFlow(null)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long, config: SourcesScreen.SmartSearchConfig): SmartSearchViewModel
    }
    private val smartSearchEngine = SmartSourceSearchEngine(null)

    val source = sourceManager.getOrStub(sourceId)

    init {
        viewModelScope.launchIO {
            val result = try {
                val resultManga = smartSearchEngine.deepSearch(source, config.origTitle)
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
