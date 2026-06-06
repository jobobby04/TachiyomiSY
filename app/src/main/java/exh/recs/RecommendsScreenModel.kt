package exh.recs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.StaticResultPagingSource
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class RecommendsScreenModel(
    private val args: RecommendsScreen.Args,
    sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateScreenModel<RecommendsScreenModel.State>(State()) {

    private val getLibraryManga: GetLibraryManga = Injekt.get()
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(10)

    // DEPOIS
    private val sourceOrder = listOf("AniList", "MyAnimeList", "MangaUpdates", "Kitsu", "Shikimori", "Bangumi")

    private val sortComparator = { map: Map<RecommendationPagingSource, RecommendationItemResult> ->
        compareBy<RecommendationPagingSource>(
            { (map[it] as? RecommendationItemResult.Success)?.isEmpty ?: true },
            { sourceOrder.indexOf(it.name).let { i -> if (i == -1) Int.MAX_VALUE else i } },
            { it.name },
            { it.category.resourceId },
        )
    }


    init {
        ioCoroutineScope.launch(SupervisorJob()) {
            val recommendationSources = when (args) {
                is RecommendsScreen.Args.SingleSourceManga -> {
                    val manga = getManga.await(args.mangaId)!!
                    mutableState.update { it.copy(title = manga.title) }

                    RecommendationPagingSource.createSources(
                        manga,
                        sourceManager.getOrStub(args.sourceId) as CatalogueSource,
                    )
                }
                is RecommendsScreen.Args.MergedSourceMangas -> {
                    args.mergedResults.map(::StaticResultPagingSource)
                }
            }

            val initialItems = recommendationSources
                .associateWith { RecommendationItemResult.Loading as RecommendationItemResult }
                .let { it.toSortedMap(sortComparator(it)).toPersistentMap() }

            mutableState.update { it.copy(items = initialItems) }

            // FILTRO DE ELITE: Pegamos títulos da biblioteca limpando TUDO (espaços, pontos, símbolos)
            val libraryTitles = getLibraryManga.await().map {
                it.manga.ogTitle.lowercase().replace("[^a-z0-9]".toRegex(), "")
            }.toSet()

            recommendationSources.map { recSource ->
                async {
                    try {
                        val page = withContext(coroutineDispatcher) {
                            recSource.requestNextPage(1)
                        }

                        val titles = page.mangas.map {
                            val recSourceId = recSource.associatedSourceId
                            if (recSourceId != null) {
                                networkToLocalManga(it.toDomainManga(recSourceId))
                            } else {
                                it.toDomainManga(-1)
                            }
                        }.filterNot { manga ->
                            // FILTRO: Remove se já for favorito OU se o título limpo bater com a biblioteca
                            val cleanTitle = manga.ogTitle.lowercase().replace("[^a-z0-9]".toRegex(), "")
                            manga.favorite || libraryTitles.contains(cleanTitle)
                        }

                        if (isActive) {
                            updateItem(recSource, RecommendationItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(recSource, RecommendationItemResult.Error(e))
                        }
                    }
                }
            }.awaitAll()
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

    private fun updateItem(source: RecommendationPagingSource, result: RecommendationItemResult) {
        mutableState.update { currentState ->
            val newItems = currentState.items.mutate {
                it[source] = result
            }
            val sortedItems = newItems.toSortedMap(sortComparator(newItems)).toPersistentMap()
            currentState.copy(items = sortedItems)
        }
    }

    @Immutable
    data class State(
        val title: String? = null,
        val items: PersistentMap<RecommendationPagingSource, RecommendationItemResult> = persistentMapOf(),
    ) {
        val progress: Int = items.count { it.value !is RecommendationItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (source, result) -> result.isVisible(false) }
            .toImmutableMap()
    }
}

sealed interface RecommendationItemResult {
    data object Loading : RecommendationItemResult

    data class Error(
        val throwable: Throwable,
    ) : RecommendationItemResult

    data class Success(
        val result: List<Manga>,
    ) : RecommendationItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
