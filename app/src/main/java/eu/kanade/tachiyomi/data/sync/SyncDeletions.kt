package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupDeletedItem
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * SY-specific file
 *
 * Applies the explicit tombstone list a SyncYomi v2 delta carries in `Backup.deletions` (field 11000). Keys
 * are the server's merge keys, so they must stay byte for byte compatible with `mangaTombstoneKey` and
 * `chapterTombstoneKey` above.
 */

private const val LOG_TAG = "SyncDeletions"
private const val MANGA_KEY_SEPARATOR = '|'
private const val CHAPTER_KEY_SEPARATOR = '\u001f'
private const val CATEGORY_UID_PREFIX = "uid:"
private const val CATEGORY_NAME_PREFIX = "name:"

/** Sync item kinds this client deletes locally. */
internal const val TOMBSTONE_MANGA = "manga"
internal const val TOMBSTONE_CHAPTER = "chapter"
internal const val TOMBSTONE_HISTORY = "history"
internal const val TOMBSTONE_CATEGORY = "category"

/**
 * Kinds a delta may tombstone that this client never applies: every one of them names a settings section, and
 * dropping a local setting because the server tombstoned a section key would invent settings-deletion
 * behaviour. They are reported instead of applied.
 */
internal val unappliedTombstoneKinds = setOf(
    "source",
    "app_pref",
    "source_pref",
    "ext_store",
    "saved_search",
    "extra",
)

/** A chapter or history tombstone: `<sourceId>|<mangaUrl>\u001f<chapterUrl>`. */
internal data class ChapterTombstone(
    val sourceId: Long,
    val mangaUrl: String,
    val chapterUrl: String,
)

/** A category tombstone: `uid:<uid>` or `name:<name>`, whichever identity the deleting device knew. */
internal data class CategoryTombstone(
    val uid: Long? = null,
    val name: String? = null,
)

/** The local deletions [deletionPlan] derived from a delta, one entry per row the server tombstoned. */
internal class DeletionPlan(
    val manga: List<Pair<Long, String>>,
    val chapters: List<ChapterTombstone>,
    val history: List<ChapterTombstone>,
    val categories: List<CategoryTombstone>,
    val skippedKinds: Set<String>,
)

/** `sourceId|mangaUrl`, or null when the key is not one this client understands. */
internal fun mangaKeyOf(key: String): Pair<Long, String>? {
    val separator = key.indexOf(MANGA_KEY_SEPARATOR)
    if (separator <= 0 || separator == key.lastIndex) return null
    val sourceId = key.substring(0, separator).toLongOrNull() ?: return null
    return sourceId to key.substring(separator + 1)
}

/** `<sourceId>|<mangaUrl>\u001f<chapterUrl>`, or null when the key is not one this client understands. */
internal fun chapterKeyOf(key: String): ChapterTombstone? {
    val separator = key.indexOf(CHAPTER_KEY_SEPARATOR)
    if (separator < 0 || separator == key.lastIndex) return null
    val (sourceId, mangaUrl) = mangaKeyOf(key.substring(0, separator)) ?: return null
    return ChapterTombstone(sourceId, mangaUrl, key.substring(separator + 1))
}

/** `uid:<uid>` or `name:<name>`, or null when the key is not one this client understands. */
internal fun categoryKeyOf(key: String): CategoryTombstone? = when {
    key.startsWith(CATEGORY_UID_PREFIX) ->
        key.removePrefix(CATEGORY_UID_PREFIX).toLongOrNull()?.let { CategoryTombstone(uid = it) }

    key.startsWith(CATEGORY_NAME_PREFIX) ->
        key.removePrefix(CATEGORY_NAME_PREFIX).takeIf { it.isNotEmpty() }?.let { CategoryTombstone(name = it) }

    else -> null
}

/**
 * Splits a delta's tombstones into the deletions this client can apply. A key of a known kind that cannot be
 * read is dropped with a warning; a kind this client does not apply is reported in [DeletionPlan.skippedKinds].
 */
internal fun deletionPlan(deletions: List<BackupDeletedItem>): DeletionPlan {
    val manga = mutableListOf<Pair<Long, String>>()
    val chapters = mutableListOf<ChapterTombstone>()
    val history = mutableListOf<ChapterTombstone>()
    val categories = mutableListOf<CategoryTombstone>()
    val skipped = mutableSetOf<String>()

    deletions.forEach { item ->
        when (item.kind) {
            TOMBSTONE_MANGA -> mangaKeyOf(item.key)?.let { manga += it } ?: unreadableKey(item)
            TOMBSTONE_CHAPTER -> chapterKeyOf(item.key)?.let { chapters += it } ?: unreadableKey(item)
            TOMBSTONE_HISTORY -> chapterKeyOf(item.key)?.let { history += it } ?: unreadableKey(item)
            TOMBSTONE_CATEGORY -> categoryKeyOf(item.key)?.let { categories += it } ?: unreadableKey(item)
            else -> {
                skipped += item.kind
                if (item.kind !in unappliedTombstoneKinds) {
                    logcat(LOG_TAG, LogPriority.WARN) { "Unknown tombstone kind: ${item.kind}" }
                }
            }
        }
    }

    return DeletionPlan(manga, chapters, history, categories, skipped)
}

private fun unreadableKey(item: BackupDeletedItem) {
    logcat(LOG_TAG, LogPriority.WARN) { "Ignoring ${item.kind} tombstone with an unreadable key: ${item.key}" }
}

/**
 * Deletes the local rows [plan] names: a manga takes its chapters and history with it (the foreign keys
 * cascade), a chapter is a single row, a history entry is keyed by its chapter, and a category is matched by
 * uid or name. A key with no local row is a no-op, so a tombstone of something this device never had is free.
 *
 * [librarySyncEnabled] and [historySyncEnabled] honour the same user settings the snapshot fallback honours:
 * a section the user disabled is not deleted on the server's word. Returns the kinds that were not applied.
 */
internal suspend fun applyDeletions(
    plan: DeletionPlan,
    mangaRepository: MangaRepository,
    chapterRepository: ChapterRepository,
    historyRepository: HistoryRepository,
    categoryRepository: CategoryRepository,
    librarySyncEnabled: Boolean = true,
    historySyncEnabled: Boolean = true,
): Set<String> {
    val skipped = plan.skippedKinds.toMutableSet()

    if (librarySyncEnabled) {
        plan.manga.forEach { (sourceId, mangaUrl) ->
            val manga = mangaRepository.getMangaByUrlAndSourceId(mangaUrl, sourceId) ?: return@forEach
            logcat(LOG_TAG, LogPriority.DEBUG) { "Deleting ${manga.url}: the delta tombstoned it" }
            mangaRepository.deleteManga(manga.id)
        }
    } else if (plan.manga.isNotEmpty()) {
        skipped += TOMBSTONE_MANGA
    }

    plan.chapters.forEach { key ->
        val chapterId = localChapter(key, mangaRepository, chapterRepository) ?: return@forEach
        logcat(LOG_TAG, LogPriority.DEBUG) { "Deleting chapter ${key.chapterUrl}: the delta tombstoned it" }
        chapterRepository.removeChaptersWithIds(listOf(chapterId))
    }

    if (historySyncEnabled) {
        val chapterIds = plan.history.mapNotNull { key ->
            localChapter(key, mangaRepository, chapterRepository)
        }
        if (chapterIds.isNotEmpty()) {
            logcat(LOG_TAG, LogPriority.DEBUG) { "Deleting ${chapterIds.size} history entries the delta tombstoned" }
            historyRepository.deleteHistory(chapterIds)
        }
    } else if (plan.history.isNotEmpty()) {
        skipped += TOMBSTONE_HISTORY
    }

    if (plan.categories.isNotEmpty()) {
        // one read for the whole list; local ids and uids only agree by accident, so match on both identities
        val localCategories = categoryRepository.getAll().filterNot { it.id == 0L }
        val categoryIds = plan.categories.mapNotNull { tombstone ->
            localCategories.firstOrNull { category ->
                when {
                    tombstone.uid != null -> category.uid == tombstone.uid
                    tombstone.name != null -> category.name == tombstone.name
                    else -> false
                }
            }
        }.map { it.id }.distinct()
        if (categoryIds.isNotEmpty()) {
            logcat(LOG_TAG, LogPriority.DEBUG) { "Deleting ${categoryIds.size} categories the delta tombstoned" }
            categoryRepository.deleteAll(categoryIds)
        }
    }

    return skipped
}

/** The local id of [key]'s chapter, resolved through its manga because the chapter key carries no id. */
private suspend fun localChapter(
    key: ChapterTombstone,
    mangaRepository: MangaRepository,
    chapterRepository: ChapterRepository,
): Long? {
    val manga = mangaRepository.getMangaByUrlAndSourceId(key.mangaUrl, key.sourceId) ?: return null
    return chapterRepository.getChapterByUrlAndMangaId(key.chapterUrl, manga.id)?.id
}

/**
 * Whether a response carries nothing this client has to apply: the server echoed our own backup, or a v2 delta
 * reports no change and lists no tombstone. A delta whose only change is a deletion reports `changed = false`
 * and would otherwise be skipped.
 */
internal fun skipRemoteResponse(
    ownBackup: Boolean,
    protocolV2: Boolean,
    changed: Boolean,
    deletions: List<BackupDeletedItem>,
): Boolean = ownBackup || (protocolV2 && !changed && deletions.isEmpty())
