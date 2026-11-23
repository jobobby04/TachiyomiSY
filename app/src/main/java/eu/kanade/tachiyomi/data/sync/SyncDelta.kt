package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * SY-specific file
 *
 * Keeps only manga (and their chapters) modified at or after the last successful upload, which is all a
 * SyncYomi protocol v2 server needs. `lastModifiedAt` is in epoch seconds, set by the database triggers.
 *
 * A manga kept only because of a chapter change is sent with just its changed chapters; the server treats a
 * manga without chapters as "nothing to say about chapters", never "no chapters".
 *
 * Mutates the kept [BackupManga] instances (the backup models are plain classes); callers pass a freshly
 * built backup that is only used for the upload.
 */
internal fun changedSince(mangas: List<BackupManga>, since: Long): List<BackupManga> =
    mangas
        .filter { it.lastModifiedAt >= since || it.chapters.any { chapter -> chapter.lastModifiedAt >= since } }
        .onEach { manga -> manga.chapters = manga.chapters.filter { it.lastModifiedAt >= since } }

/** [BackupCategory] has identity equality, and local ids and timestamps differ between devices. */
internal fun categoriesDiffer(local: List<BackupCategory>, remote: List<BackupCategory>): Boolean =
    local.map { it.syncIdentity() }.toSet() != remote.map { it.syncIdentity() }.toSet()

private fun BackupCategory.syncIdentity() = listOf(uid, name, order, flags, version)

internal fun extensionStoresDiffer(local: List<BackupExtensionStore>, remote: List<BackupExtensionStore>): Boolean =
    local.map { it.syncIdentity() }.toSet() != remote.map { it.syncIdentity() }.toSet()

private fun BackupExtensionStore.syncIdentity() =
    listOf(indexUrl, name, badgeLabel, signingKey, contactWebsite, contactDiscord, isLegacy, extensionListUrl)

/** Must match the SyncYomi server's chapter key byte for byte, or the server tombstones nothing. */
internal fun chapterTombstoneKey(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
    "$sourceId|$mangaUrl\u001f$chapterUrl"

/** Must match the SyncYomi server's manga key byte for byte, or the server tombstones nothing. */
internal fun mangaTombstoneKey(sourceId: Long, mangaUrl: String): String =
    "$sourceId|$mangaUrl"

/** A history row is keyed exactly like its chapter. */
internal fun historyTombstoneKey(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
    chapterTombstoneKey(sourceId, mangaUrl, chapterUrl)

/**
 * The `(sourceId, url)` of library manga this device uploaded that a full snapshot no longer holds: the
 * server can only have dropped them because it tombstones them.
 *
 * A manga this device did not upload is never a candidate, so a row the server never heard of survives. A
 * `favorite = false` entry in the snapshot is the server's own record of a library removal, so it is still
 * present and never deleted, and a snapshot with no manga at all says nothing about the library.
 */
internal fun mangaKeysToDelete(
    uploaded: List<BackupManga>,
    remote: List<BackupManga>,
    full: Boolean,
    protocolV2: Boolean,
    librarySyncEnabled: Boolean,
): Set<Pair<Long, String>> {
    if (!full || !protocolV2 || !librarySyncEnabled || remote.isEmpty()) return emptySet()
    val remoteKeys = remote.mapTo(mutableSetOf()) { it.source to it.url }
    return uploaded
        .filter { it.favorite }
        .map { it.source to it.url }
        .filterNot { it in remoteKeys }
        .toSet()
}

/**
 * The local history rows of [payload]'s manga that [payload] no longer lists.
 *
 * The server sends the complete remaining history of every manga it sends, so a missing row was removed
 * elsewhere. A payload without any history says nothing about history, and a chapter whose URL is unknown
 * locally cannot be matched, so neither is ever deleted.
 */
internal fun historyChapterIdsToDelete(
    payload: BackupManga,
    historySyncEnabled: Boolean,
    localHistoryChapterIds: Collection<Long>,
    chapterUrlById: Map<Long, String>,
): Set<Long> {
    if (!historySyncEnabled || payload.history.isEmpty()) return emptySet()
    val keptUrls = payload.history.mapTo(mutableSetOf()) { it.url }
    return localHistoryChapterIds
        .mapNotNull { chapterId -> chapterUrlById[chapterId]?.let { chapterId to it } }
        .filterNot { (_, chapterUrl) -> chapterUrl in keptUrls }
        .mapTo(mutableSetOf()) { (chapterId, _) -> chapterId }
}

internal class LocalMangaState(
    val manga: Manga,
    val chapters: List<Chapter>,
    val categoryOrders: Set<Long>,
    val lastReadByChapterUrl: Map<String, Long>,
)

internal fun needsRestore(local: LocalMangaState, remote: BackupManga): Boolean =
    local.manga.favorite != remote.favorite ||
        local.manga.version != remote.version ||
        chaptersDiffer(local.chapters, remote.chapters) ||
        local.categoryOrders != remote.categories.toSet() ||
        remote.history.any { it.lastRead > (local.lastReadByChapterUrl[it.url] ?: 0L) }

private fun chaptersDiffer(local: List<Chapter>, remote: List<BackupChapter>): Boolean {
    val remoteVersionByUrl = remote.associate { it.url to it.version }
    return local.size != remoteVersionByUrl.size ||
        local.any { remoteVersionByUrl[it.url] != it.version }
}
