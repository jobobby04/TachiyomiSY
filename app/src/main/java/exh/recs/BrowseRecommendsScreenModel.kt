package exh.recs

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.StaticResultPagingSource
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseRecommendsScreenModel(
    private val args: BrowseRecommendsScreen.Args,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourceScreenModel(
    when (args) {
        is BrowseRecommendsScreen.Args.SingleSourceManga -> args.sourceId
        is BrowseRecommendsScreen.Args.MergedSourceMangas -> args.results.recAssociatedSourceId ?: -1
    },
    null
) {
    val recommendationSource: RecommendationPagingSource
        get() = when (args) {
            is BrowseRecommendsScreen.Args.MergedSourceMangas -> StaticResultPagingSource(args.results)
            is BrowseRecommendsScreen.Args.SingleSourceManga -> RecommendationPagingSource.createSources(
                runBlocking { getManga.await(args.mangaId)!! },
                source as CatalogueSource
            ).first {
                it::class.qualifiedName == args.recommendationSourceName
            }
        }

    override fun createSourcePagingSource(query: String, filters: FilterList) = recommendationSource

    init {
        mutableState.update { it.copy(filterable = false) }
    }
}
