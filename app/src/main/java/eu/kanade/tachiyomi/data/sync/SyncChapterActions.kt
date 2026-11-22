package eu.kanade.tachiyomi.data.sync

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * SY-specific file
 *
 * Applies the download rules to chapters a sync changed, as if the change had happened on this device:
 * chapters read elsewhere are deleted following the "delete after read" settings, and chapters that arrived
 * through the sync are downloaded following the "download new chapters" settings.
 *
 * Merged entries own no chapters; their chapters and downloads belong to the merged children, which are
 * synced as entries of their own. Deleting works on the child like the reader does, while whether to
 * download is decided by the merged parent like a library update does.
 */
@Inject
class SyncChapterActions(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val mangaMergeRepository: MangaMergeRepository,
    private val downloadManager: DownloadManager,
    private val downloadPreferences: DownloadPreferences,
    private val filterChaptersForDownload: FilterChaptersForDownload,
) {

    /**
     * Records the chapters of the local entries about to be overwritten by [changedManga], to compare against
     * once the sync is applied. Entries this device does not have yet are left out; they have nothing to delete,
     * and a library update does not download the chapters of newly added entries either.
     */
    suspend fun snapshot(changedManga: List<BackupManga>): Map<Long, List<Chapter>> =
        changedManga
            .filter { it.source != MERGED_SOURCE_ID }
            .mapNotNull { mangaRepository.getMangaByUrlAndSourceId(it.url, it.source) }
            .associate { it.id to chapterRepository.getChapterByMangaId(it.id) }

    /** Deletes and downloads chapters by comparing the applied sync against [snapshot]'s [before]. */
    suspend fun apply(before: Map<Long, List<Chapter>>) {
        if (before.isEmpty()) return

        val removeAfterMarkedAsRead = downloadPreferences.removeAfterMarkedAsRead.get()
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots.get()
        val mergeEntriesByChild = mangaMergeRepository.getAllEntries().groupBy { it.mangaId }
        var hasDeletions = false

        before.forEach { (mangaId, chaptersBefore) ->
            try {
                val chapters = chapterRepository.getChapterByMangaId(mangaId)
                val manga = mangaRepository.getMangaById(mangaId)

                val toDelete = syncReadChaptersToDelete(
                    chapters = chapters,
                    readBySync = chaptersReadBySync(chaptersBefore, chapters),
                    removeAfterMarkedAsRead = removeAfterMarkedAsRead,
                    removeAfterReadSlots = removeAfterReadSlots,
                ).filter { it.isDownloaded(manga) }
                if (toDelete.isNotEmpty()) {
                    downloadManager.enqueueChaptersToDelete(toDelete, manga)
                    hasDeletions = true
                }

                val added = chaptersAddedBySync(chaptersBefore, chapters)
                if (added.isNotEmpty()) {
                    val toDownload = chaptersToDownload(manga, added, mergeEntriesByChild[mangaId].orEmpty())
                    if (toDownload.isNotEmpty()) downloadManager.downloadChapters(manga, toDownload)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to apply download rules to synced manga $mangaId" }
            }
        }

        if (hasDeletions) downloadManager.deletePendingChapters()
    }

    private suspend fun chaptersToDownload(
        manga: Manga,
        added: List<Chapter>,
        mergeEntries: List<MergedEntry>,
    ): List<Chapter> {
        val children = mergeEntries.filter { it.mergeId != it.mangaId }
        if (children.isEmpty()) return filterChaptersForDownload.await(manga, added)

        // a merged child is downloaded when any merge it belongs to wants its chapters
        return children
            .filter { it.downloadChapters }
            .flatMap { filterChaptersForDownload.await(mangaRepository.getMangaById(it.mergeId), added) }
            .distinctBy { it.id }
    }

    private fun Chapter.isDownloaded(manga: Manga) =
        downloadManager.isChapterDownloaded(name, scanlator, url, manga.ogTitle, manga.source)
}

/** Chapters that were unread before the sync and are read after it. */
internal fun chaptersReadBySync(before: List<Chapter>, after: List<Chapter>): List<Chapter> {
    val unreadBefore = before.filterNot { it.read }.mapTo(mutableSetOf()) { it.id }
    return after.filter { it.read && it.id in unreadBefore }
}

/**
 * Unread chapters that did not exist before the sync; ones already read elsewhere are not worth downloading.
 * An entry without chapters before the sync gets its whole chapter list at once, none of which is new.
 */
internal fun chaptersAddedBySync(before: List<Chapter>, after: List<Chapter>): List<Chapter> {
    if (before.isEmpty()) return emptyList()
    val idsBefore = before.mapTo(mutableSetOf()) { it.id }
    return after.filter { it.id !in idsBefore && !it.read }
}

/**
 * Which of the chapters read through a sync to delete. "Delete after marked as read" deletes all of them.
 * Otherwise "delete after read" mirrors the reader, which deletes the chapter [removeAfterReadSlots] places
 * behind each chapter finished: everything read up to that many chapters behind the furthest one is deleted.
 */
internal fun syncReadChaptersToDelete(
    chapters: List<Chapter>,
    readBySync: List<Chapter>,
    removeAfterMarkedAsRead: Boolean,
    removeAfterReadSlots: Int,
): List<Chapter> {
    if (readBySync.isEmpty()) return emptyList()
    if (removeAfterMarkedAsRead) return readBySync
    if (removeAfterReadSlots == -1) return emptyList()

    // reading order, oldest chapter first
    val positionById = chapters.sortedByDescending { it.sourceOrder }
        .withIndex()
        .associate { (index, chapter) -> chapter.id to index }
    val furthest = readBySync.maxOf { positionById.getValue(it.id) }
    return readBySync.filter { positionById.getValue(it.id) <= furthest - removeAfterReadSlots }
}
