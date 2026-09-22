package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga

/**
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
