package eu.kanade.domain.manga.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.toSManga
import exh.source.MERGED_SOURCE_ID
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.ApplyMerge
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedEntriesById
import tachiyomi.domain.manga.interactor.InsertMergedEntry
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.i18n.sy.SYMR
import kotlin.time.Clock

/**
 * SY-specific file
 */
@Inject
class SmartSearchMerge(
    private val context: Context,
    private val getManga: GetManga,
    private val getMergedEntriesById: GetMergedEntriesById,
    private val insertMergedEntry: InsertMergedEntry,
    private val applyMerge: ApplyMerge,
    private val getCategories: GetCategories,
) {
    /**
     * Merges [manga] into the entry [originalMangaId] in a single transaction. With [strict]
     * (single merge) a no-op throws; otherwise (bulk merge) it returns/reuses so the chain continues.
     */
    suspend fun smartSearchMerge(manga: Manga, originalMangaId: Long, strict: Boolean = false): Manga {
        val originalManga = getManga.await(originalMangaId)
            ?: throw IllegalArgumentException(context.stringResource(SYMR.strings.merge_unknown_entry, originalMangaId))

        return if (originalManga.source == MERGED_SOURCE_ID) {
            mergeIntoMergedEntry(originalManga, manga, strict)
        } else {
            createMergedEntry(originalManga, manga, strict)
        }
    }

    private suspend fun mergeIntoMergedEntry(originalManga: Manga, manga: Manga, strict: Boolean): Manga {
        val children = getMergedEntriesById.await(originalManga.id)
        if (children.any { it.mangaId == manga.id }) {
            if (strict) {
                throw IllegalArgumentException(context.stringResource(SYMR.strings.merged_already))
            }
            return originalManga
        }

        insertMergedEntry.await(
            MergedEntry(
                id = -1,
                mergeId = originalManga.id,
                mangaId = manga.id,
                fetchChapterUpdates = true,
                downloadChapters = true,
                chapterPriority = children.maxOfOrNull { it.chapterPriority }?.plus(1) ?: 0,
            ),
        )

        return originalManga
    }

    private suspend fun createMergedEntry(originalManga: Manga, manga: Manga, strict: Boolean): Manga {
        if (manga.id == originalManga.id) {
            if (strict) {
                throw IllegalArgumentException(context.stringResource(SYMR.strings.merged_already))
            }
            return originalManga
        }

        var mergedManga = Manga.create()
            .copy(
                url = originalManga.url,
                ogTitle = originalManga.title,
                source = MERGED_SOURCE_ID,
            )
            .copyFrom(originalManga.toSManga())
            .copy(
                favoriteAt = Clock.System.now(),
                lastUpdate = originalManga.lastUpdate,
                viewerFlags = originalManga.viewerFlags,
                chapterFlags = originalManga.chapterFlags,
            )

        // Reuse an existing favorite merged entry for this URL; delete a stale (non-favorite) one
        val existingManga = getManga.await(mergedManga.url, mergedManga.source)
        val reusingExisting = existingManga?.favorite == true
        val staleMangaId = if (existingManga != null && !reusingExisting) existingManga.id else null
        if (existingManga != null && reusingExisting) {
            if (strict) {
                throw IllegalArgumentException(context.stringResource(SYMR.strings.merge_duplicate))
            }
            mergedManga = existingManga
        }

        val existingEntries = if (reusingExisting) getMergedEntriesById.await(mergedManga.id) else emptyList()
        val categoryIds = getCategories.await(originalManga.id).map { it.id }
        val entries = buildEntries(originalManga, manga, existingEntries)

        return applyMerge.await(
            staleMangaId = staleMangaId,
            mergedManga = mergedManga,
            categoryIds = categoryIds,
            chapterSortMode = MergeSettings.CHAPTER_SORT_NONE,
            infoMangaId = originalManga.id,
            entries = entries,
        )
    }

    // Builds the entries to add, skipping ones already in [existingEntries]; mergeId is set by [ApplyMerge]
    private fun buildEntries(
        originalManga: Manga,
        manga: Manga,
        existingEntries: List<MergedEntry>,
    ): List<MergedEntry> {
        val nextPriority = existingEntries.maxOfOrNull { it.chapterPriority }?.plus(1) ?: 0
        return buildList {
            if (existingEntries.none { it.mangaId == originalManga.id }) {
                add(
                    MergedEntry(
                        id = -1,
                        mergeId = 0,
                        mangaId = originalManga.id,
                        fetchChapterUpdates = true,
                        downloadChapters = true,
                        chapterPriority = 0,
                    ),
                )
            }
            if (existingEntries.none { it.mangaId == manga.id }) {
                add(
                    MergedEntry(
                        id = -1,
                        mergeId = 0,
                        mangaId = manga.id,
                        fetchChapterUpdates = true,
                        downloadChapters = true,
                        chapterPriority = nextPriority,
                    ),
                )
            }
        }
    }
}
