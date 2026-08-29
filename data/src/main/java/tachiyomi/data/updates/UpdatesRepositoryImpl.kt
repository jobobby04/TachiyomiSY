package tachiyomi.data.updates

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.Database
import tachiyomi.data.getUpdatesQuery
import tachiyomi.data.subscribeToList
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository
import tachiyomi.view.UpdatesView

class UpdatesRepositoryImpl(
    private val database: Database,
) : UpdatesRepository {

    override suspend fun awaitWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): List<UpdatesWithRelations> {
        return database.updatesViewQueries
            .getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
            .awaitAsList()
    }

    override fun subscribeAll(
        after: Long,
        limit: Long,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean,
    ): Flow<List<UpdatesWithRelations>> {
        return database.updatesViewQueries
            .getRecentUpdatesWithFilters(
                after = after,
                limit = limit,
                read = unread?.let { !it },
                started = started?.toLong(),
                bookmarked = bookmarked,
                hideExcludedScanlators = hideExcludedScanlators.toLong(),
                mapper = ::mapUpdatesWithRelations,
            )
            .subscribeToList().map {
                getUpdatesQuery(
                    after = after,
                    limit = limit,
                    // invert because unread in Kotlin -> read column in SQL
                    read = unread?.let { !it },
                    started = started?.toLong(),
                    bookmarked = bookmarked,
                    hideExcludedScanlators = hideExcludedScanlators.toLong(),
                )
                    .awaitAsList()
                    .map(::mapUpdatesView)
            }
    }

    override fun subscribeWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): Flow<List<UpdatesWithRelations>> {
        return database.updatesViewQueries
            .getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
            .subscribeToList()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapUpdatesWithRelations(
        mangaId: Long,
        mangaTitle: String,
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        chapterUrl: String,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        dateUpload: Long,
        dateFetch: Long,
        excludedScanlator: String?,
    ): UpdatesWithRelations = UpdatesWithRelations(
        mangaId = mangaId,
        // SY -->
        ogMangaTitle = mangaTitle,
        // SY <--
        chapterId = chapterId,
        chapterName = chapterName,
        scanlator = scanlator,
        chapterUrl = chapterUrl,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = favorite,
            ogUrl = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )

    fun mapUpdatesView(updatesView: UpdatesView): UpdatesWithRelations {
        return UpdatesWithRelations(
            mangaId = updatesView.mangaId,
            ogMangaTitle = updatesView.mangaTitle,
            chapterId = updatesView.chapterId,
            chapterName = updatesView.chapterName,
            scanlator = updatesView.scanlator,
            chapterUrl = updatesView.chapterUrl,
            read = updatesView.read,
            bookmark = updatesView.bookmark,
            lastPageRead = updatesView.last_page_read,
            sourceId = updatesView.source,
            dateFetch = updatesView.datefetch,
            coverData = MangaCover(
                mangaId = updatesView.mangaId,
                sourceId = updatesView.source,
                isMangaFavorite = updatesView.favorite,
                ogUrl = updatesView.thumbnailUrl,
                lastModified = updatesView.coverLastModified,
            ),
        )
    }
}
