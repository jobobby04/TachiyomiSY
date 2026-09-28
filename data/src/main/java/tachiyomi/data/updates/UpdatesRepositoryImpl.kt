package tachiyomi.data.updates

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.Database
import tachiyomi.data.getUpdatesQuery
import tachiyomi.data.subscribeToList
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository
import tachiyomi.view.UpdatesView

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class UpdatesRepositoryImpl(
    private val database: Database,
    private val driver: SqlDriver,
    private val getCustomMangaInfo: GetCustomMangaInfo,
) : UpdatesRepository {

    override suspend fun awaitWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): List<UpdatesWithRelations> {
        return getUpdatesByReadStatus(read = read, after = after, limit = limit).awaitAsList()
    }

    override fun subscribeAll(
        after: Long,
        limit: Long,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean,
        includedCategories: List<Long>,
        excludedCategories: List<Long>,
    ): Flow<List<UpdatesWithRelations>> {
        return database.updatesViewQueries
            .getRecentUpdatesWithFilters(
                after = after,
                limit = limit,
                read = unread?.let { !it },
                started = started?.toLong(),
                bookmarked = bookmarked,
                hideExcludedScanlators = hideExcludedScanlators.toLong(),
                includedEmpty = includedCategories.isEmpty(),
                excludedEmpty = excludedCategories.isEmpty(),
                includedCategories = includedCategories,
                excludedCategories = excludedCategories,
                mapper = ::mapUpdatesWithRelations,
            )
            .subscribeToList().map {
                getUpdatesQuery(
                    driver = driver,
                    after = after,
                    limit = limit,
                    // invert because unread in Kotlin -> read column in SQL
                    read = unread?.let { !it },
                    started = started?.toLong(),
                    bookmarked = bookmarked,
                    hideExcludedScanlators = hideExcludedScanlators.toLong(),
                    includedEmpty = includedCategories.isEmpty(),
                    excludedEmpty = excludedCategories.isEmpty(),
                    includedCategories = includedCategories,
                    excludedCategories = excludedCategories,
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
        return getUpdatesByReadStatus(read = read, after = after, limit = limit).subscribeToList()
    }

    private fun getUpdatesByReadStatus(read: Boolean, after: Long, limit: Long): Query<UpdatesWithRelations> {
        return if (read) {
            database.updatesViewQueries.getReadUpdates(
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        } else {
            database.updatesViewQueries.getUnreadUpdates(
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
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
    ): UpdatesWithRelations {
        val customMangaInfo = getCustomMangaInfo.get(mangaId)
        return UpdatesWithRelations(
            mangaId = mangaId,
            // SY -->
            ogMangaTitle = mangaTitle,
            customMangaInfo = customMangaInfo,
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
                customThumbnailUrl = if (favorite) customMangaInfo?.thumbnailUrl else null,
                lastModified = coverLastModified,
            ),
        )
    }

    fun mapUpdatesView(updatesView: UpdatesView): UpdatesWithRelations {
        val customMangaInfo = getCustomMangaInfo.get(updatesView.mangaId)
        return UpdatesWithRelations(
            mangaId = updatesView.mangaId,
            ogMangaTitle = updatesView.mangaTitle,
            customMangaInfo = customMangaInfo,
            chapterId = updatesView.chapterId,
            chapterName = updatesView.chapterName,
            scanlator = updatesView.scanlator,
            chapterUrl = updatesView.chapterUrl,
            read = updatesView.read,
            bookmark = updatesView.bookmark,
            lastPageRead = updatesView.lastPageRead,
            sourceId = updatesView.sourceId,
            dateFetch = updatesView.dateFetch,
            coverData = MangaCover(
                mangaId = updatesView.mangaId,
                sourceId = updatesView.sourceId,
                isMangaFavorite = updatesView.favorite,
                ogUrl = updatesView.thumbnailUrl,
                customThumbnailUrl = if (updatesView.favorite) customMangaInfo?.thumbnailUrl else null,
                lastModified = updatesView.coverLastModified,
            ),
        )
    }
}
