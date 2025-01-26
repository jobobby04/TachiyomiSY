package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.model.MangasPage
import exh.recs.batch.RankedSearchResults
import tachiyomi.domain.manga.model.Manga

class StaticResultPagingSource(
    val data: RankedSearchResults,
    private val useVirtualPaging: Boolean = false // TODO always enable paging? might reduce slowdowns on RecommendsScreen
) : RecommendationPagingSource(Manga.create()) {

    override val name: String get() = data.recSourceName
    override val category: StringResource get() = StringResource(data.recSourceCategoryResId)
    override val associatedSourceId: Long? get() = data.recAssociatedSourceId

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val mangas = data.results.keys.toList()

        if(!useVirtualPaging)
            return MangasPage(mangas, false)

        // Better performance for large lists
        return mangas
            .chunked(PAGE_SIZE)
            .getOrNull(currentPage - 1)
            .let {
                MangasPage(it ?: listOf(), data.results.size > currentPage * PAGE_SIZE)
            }
    }

    companion object {
        const val PAGE_SIZE = 25
    }
}
