package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.metadata.metadata.RaisedSearchMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourcePagingSource

class SourceSearchPagingSource(
    source: suspend () -> Source,
    private val query: String,
    private val filters: FilterList,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    // SY -->
    override suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage {
        return source!!.getSearchManga(currentPage, query, filters)
    }
    // SY <--
}

class SourcePopularPagingSource(
    source: suspend () -> Source,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    // SY -->
    override suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage {
        return source!!.getPopularManga(currentPage)
    }
    // SY <--
}

class SourceLatestPagingSource(
    source: suspend () -> Source,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    // SY -->
    override suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage {
        return source!!.getLatestUpdates(currentPage)
    }
    // SY <--
}

abstract class BaseSourcePagingSource(
    // SY -->
    protected open val source: (suspend () -> Source)?,
    protected val networkToLocalManga: NetworkToLocalManga,
    // SY <--
) : SourcePagingSource() {

    private val seenManga = hashSetOf<String>()

    // SY -->
    abstract suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage
    // SY <--

    override suspend fun load(
        params: LoadParams<Long>,
    ): LoadResult<Long, /* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */> {
        val page = params.key ?: 1

        return try {
            // SY -->
            val source = source?.invoke()
            // SY <--
            val mangasPage = withContext(Dispatchers.IO) {
                requestNextPage(source, page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
            }

            // SY -->
            getPageLoadResult(source, params, mangasPage)
            // SY <--
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    // SY -->
    open suspend fun getPageLoadResult(
        source: Source?,
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, /* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */> {
        val page = params.key ?: 1

        // SY -->
        val metadata = if (mangasPage is MetadataMangasPage) {
            mangasPage.mangasMetadata
        } else {
            emptyList()
        }

        val manga = mangasPage.mangas.mapIndexed { index, sManga -> sManga.toDomainManga(source?.id ?: -1) to metadata.getOrNull(index) }
            .filter { seenManga.add(it.first.url) }
            .let { manga -> manga.zip(networkToLocalManga(manga.map { it.first })).map { it.second to it.first.second } }
        // SY <--

        return LoadResult.Page(
            data = manga,
            prevKey = null,
            nextKey = if (mangasPage.hasNextPage) page + 1 else null,
        )
    }
    // SY <--

    override fun getRefreshKey(
        state: PagingState<Long, /* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */>,
    ): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()
