package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.metadata.metadata.RankedSearchMetadata
import exh.recs.batch.RankedSearchResults
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

class StaticResultPagingSource(
    val data: RankedSearchResults,
    source: CatalogueSource? = null,
) : RecommendationPagingSource(Manga.create(), source) {

    override val name: String get() = data.recSourceName

    override val category: StringResource get() = try {
        if (data.recSourceCategoryResId != 0) {
            StringResource(data.recSourceCategoryResId)
        } else {
            SYMR.strings.similar_titles
        }
    } catch (e: Exception) {
        SYMR.strings.similar_titles
    }

    override val associatedSourceId: Long? get() = data.recAssociatedSourceId

    override suspend fun requestNextPage(currentPage: Int): MangasPage =
        data.results
            .entries
            .chunked(PAGE_SIZE)
            .getOrElse(currentPage - 1) { emptyList() }
            .let { chunk ->
                MetadataMangasPage(
                    mangas = chunk.map { it.key },
                    hasNextPage = data.results.size > currentPage * PAGE_SIZE,
                    mangasMetadata = chunk
                        .map { it.value }
                        .map { count ->
                            RankedSearchMetadata().also { it.rank = count }
                        },
                )
            }

    companion object {
        const val PAGE_SIZE = 25
    }
}
