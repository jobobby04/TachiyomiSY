package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.model.MangasPage
import exh.recs.batch.SearchResults
import tachiyomi.domain.manga.model.Manga

class StaticResultPagingSource(
    val results: SearchResults,
    private val useVirtualPaging: Boolean = false
) : RecommendationPagingSource(Manga.create()) {
    override val name: String get() = results.recSourceName
    override val category: StringResource get() = StringResource(results.recSourceCategoryResId)
    override val associatedSourceId: Long? get() = results.recAssociatedSourceId

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        if(!useVirtualPaging)
            return MangasPage(results.recommendations, false)

        // Better performance for large lists
        return results.recommendations
            .chunked(PAGE_SIZE)
            .getOrNull(currentPage - 1)
            .let {
                MangasPage(it ?: listOf(), results.recommendations.size > currentPage * PAGE_SIZE)
            }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
