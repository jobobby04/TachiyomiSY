package exh.md.follows

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import tachiyomi.data.source.BaseSourcePagingSource
import tachiyomi.domain.manga.interactor.NetworkToLocalManga

/**
 * LatestUpdatesPager inherited from the general Pager.
 */
class MangaDexFollowsPagingSource(val mangadex: MangaDex, networkToLocalManga: NetworkToLocalManga) :
    BaseSourcePagingSource(mangadex, networkToLocalManga) {

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return mangadex.fetchFollows(currentPage)
    }
}
