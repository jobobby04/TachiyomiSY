package exh.ui.smartsearch

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartSearchScreenModel(
    sourceId: Long,
    private val config: SourcesScreen.SmartSearchConfig,
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<SmartSearchScreenModel.State>(State(searchQuery = config.origTitle)) {

    @Immutable
    data class State(
        val searchQuery: String = "",
        val searchResults: SearchResults? = null,
        val isSearching: Boolean = false,
    )

    private val smartSearchEngine = SmartSourceSearchEngine(null)

    val source = sourceManager.get(sourceId) as CatalogueSource

    fun updateSearchQuery(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun performSearch() {
        mutableState.update { it.copy(isSearching = true, searchResults = null) }
        screenModelScope.launchIO {
            val result = try {
                val resultMangas = smartSearchEngine.regularSearchResults(source, mutableState.value.searchQuery)
                if (resultMangas.isEmpty()) {
                    SearchResults.NotFound
                } else {
                    SearchResults.Results(
                        resultMangas
                            .map { networkToLocalManga(it) }
                            .distinctBy(Manga::id),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                } else {
                    SearchResults.Error
                }
            }

            mutableState.update { it.copy(searchResults = result, isSearching = false) }
        }
    }

    sealed class SearchResults {
        data class Found(val manga: Manga) : SearchResults()
        data class Results(val mangas: List<Manga>) : SearchResults()
        data object NotFound : SearchResults()
        data object Error : SearchResults()
    }
}
