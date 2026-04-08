package exh.recs

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import exh.metadata.metadata.RaisedSearchMetadata
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.StaticResultPagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseRecommendsScreenModel(
    private val args: BrowseRecommendsScreen.Args,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourceScreenModel(
    sourceId = when (args) {
        is BrowseRecommendsScreen.Args.SingleSourceManga -> args.sourceId
        is BrowseRecommendsScreen.Args.MergedSourceMangas -> args.results.recAssociatedSourceId ?: -1L
    },
    listingQuery = null,
) {
    // IMPORTANTE: Para evitar o NullPointerException, nunca retorne null aqui.
    // Se a fonte não for uma CatalogueSource real, usamos o StaticResultPagingSource.
    override fun createSourcePagingSource(query: String, filters: FilterList): RecommendationPagingSource {
        return try {
            when (args) {
                is BrowseRecommendsScreen.Args.MergedSourceMangas -> StaticResultPagingSource(args.results)
                is BrowseRecommendsScreen.Args.SingleSourceManga -> {
                    val manga = runBlocking { getManga.await(args.mangaId) }
                    val currentSource = source as? CatalogueSource
                    if (manga != null && currentSource != null) {
                        RecommendationPagingSource.createSources(manga, currentSource).firstOrNull {
                            it::class.qualifiedName == args.recommendationSourceName
                        } ?: StaticResultPagingSource(exh.recs.batch.RankedSearchResults("Recomendações", args.sourceId.toInt(), null, emptyMap()))
                    } else {
                        // Fallback seguro se o mangá ou fonte sumirem
                        StaticResultPagingSource(exh.recs.batch.RankedSearchResults("Recomendações", -1, null, emptyMap()))
                    }
                }
            }
        } catch (e: Exception) {
            // Última linha de defesa contra o NullPointerException
            StaticResultPagingSource(exh.recs.batch.RankedSearchResults("Erro ao carregar", -1, null, emptyMap()))
        }
    }

    override fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        return flatMapLatest { manga -> flowOf(manga to metadata) }
    }

    init {
        mutableState.update { it.copy(filterable = false) }
    }
}
