package exh.md.follows

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import tachiyomi.data.source.BaseSourcePagingSource
import tachiyomi.domain.manga.interactor.NetworkToLocalManga

/**
 * SY-specific file
 *
 * LatestUpdatesPager inherited from the general Pager.
 */
class MangaDexFollowsPagingSource(val mangadex: suspend () -> MangaDex, networkToLocalManga: NetworkToLocalManga) :
    BaseSourcePagingSource(mangadex, networkToLocalManga) {

    override suspend fun requestNextPage(source: Source?, currentPage: Int): MangasPage {
        return (source as MangaDex).fetchFollows(currentPage)
    }
}
