package tachiyomi.data.source

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.metadata.metadata.RaisedSearchMetadata
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga

/**
 * SY-specific file
 */
abstract class EHentaiPagingSource(
    override val source: suspend () -> Source,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {

    override suspend fun getPageLoadResult(
        source: Source?,
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, Pair<Manga, RaisedSearchMetadata?>> {
        source!!
        mangasPage as MetadataMangasPage
        val metadata = mangasPage.mangasMetadata

        val manga = mangasPage.mangas.map { it.toDomainManga(source.id) }
            .let { networkToLocalManga(it) }

        return LoadResult.Page(
            data = manga
                .mapIndexed { index, sManga -> sManga to metadata.getOrNull(index) },
            prevKey = null,
            nextKey = mangasPage.nextKey,
        )
    }
}

class EHentaiSearchPagingSource(
    source: suspend () -> Source,
    val query: String,
    val filters: FilterList,
    networkToLocalManga: NetworkToLocalManga,
) : EHentaiPagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage {
        return source!!.getSearchManga(currentPage, query, filters)
    }
}

class EHentaiPopularPagingSource(source: suspend () -> Source, networkToLocalManga: NetworkToLocalManga) :
    EHentaiPagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage {
        return source!!.getPopularManga(currentPage)
    }
}

class EHentaiLatestPagingSource(source: suspend () -> Source, networkToLocalManga: NetworkToLocalManga) :
    EHentaiPagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage {
        return source!!.getLatestUpdates(currentPage)
    }
}
