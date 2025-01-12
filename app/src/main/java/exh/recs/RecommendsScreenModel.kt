package exh.recs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

open class RecommendsScreenModel(
    val mangaId: Long,
    val sourceId: Long,
    initialState: State = State(),
    sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<RecommendsScreenModel.State>(initialState) {

    val manga = runBlocking { getManga.await(mangaId) }!!

    private val source = sourceManager.getOrStub(sourceId) as CatalogueSource

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var findRecsJob: Job? = null

    private val sortComparator = { map: Map<RecommendsPagingSource, RecommendationItemResult> ->
        compareBy<RecommendsPagingSource>(
            { (map[it] as? RecommendationItemResult.Success)?.isEmpty ?: true },
            { it.api.name },
            { it.api.category.resourceId }
        )
    }

    init {
        findRecsJob?.cancel()

        val sources = RecommendationApi.apis
            .map { RecommendsPagingSource(source, manga, it) }

        updateItems(
            sources
                .associateWith { RecommendationItemResult.Loading }
                .toPersistentMap(),
        )

        findRecsJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is RecommendationItemResult.Loading) {
                        return@async
                    }

                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.requestNextPage(1)
                        }

                        if (isActive) {
                            updateItem(source, RecommendationItemResult.Success(page.mangas))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, RecommendationItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    private fun updateItems(items: PersistentMap<RecommendsPagingSource, RecommendationItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: RecommendsPagingSource, result: RecommendationItemResult) {
        val newItems = state.value.items.mutate {
            it[source] = result
        }
        updateItems(newItems)
    }

    @Immutable
    data class State(
        val items: PersistentMap<RecommendsPagingSource, RecommendationItemResult> = persistentMapOf(),
    ) {
        val progress: Int = items.count { it.value !is RecommendationItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(false) }
            .toImmutableMap()
    }
}

sealed interface RecommendationItemResult {
    data object Loading : RecommendationItemResult

    data class Error(
        val throwable: Throwable,
    ) : RecommendationItemResult

    data class Success(
        val result: List<SManga>,
    ) : RecommendationItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}

