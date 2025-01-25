package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.model.MangasPage
import exh.recs.batch.SearchResults
import tachiyomi.domain.manga.model.Manga

class StaticResultPagingSource(
    val results: SearchResults
) : RecommendationPagingSource(Manga.create()) {
    override val name: String get() = results.recSourceName
    override val category: StringResource get() = results.recSourceCategory
    override val associatedSourceId: Long? get() = results.recAssociatedSourceId

    override suspend fun requestNextPage(currentPage: Int) = MangasPage(results.recommendations, false)
}
