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
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.InsertMergedReference
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.i18n.sy.SYMR
import kotlin.time.Clock

/**
 * SY-specific file
 */
@Inject
class SmartSearchMerge(
    private val context: Context,
    private val getManga: GetManga,
    private val getMergedReferencesById: GetMergedReferencesById,
    private val insertMergedReference: InsertMergedReference,
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
        val children = getMergedReferencesById.await(originalManga.id)
        if (children.any { it.mangaSourceId == manga.source && it.mangaUrl == manga.url }) {
            if (strict) {
                throw IllegalArgumentException(context.stringResource(SYMR.strings.merged_already))
            }
            return originalManga
        }

        val references = mutableListOf(
            MergedMangaReference(
                id = -1,
                isInfoManga = false,
                getChapterUpdates = true,
                chapterSortMode = 0,
                chapterPriority = 0,
                downloadChapters = true,
                mergeId = originalManga.id,
                mergeUrl = originalManga.url,
                mangaId = manga.id,
                mangaUrl = manga.url,
                mangaSourceId = manga.source,
            ),
        )

        // Add the main self-reference (holds merge settings) if the entry has none yet
        if (children.none { it.mangaSourceId == MERGED_SOURCE_ID }) {
            references += MergedMangaReference(
                id = -1,
                isInfoManga = false,
                getChapterUpdates = false,
                chapterSortMode = 0,
                chapterPriority = -1,
                downloadChapters = false,
                mergeId = originalManga.id,
                mergeUrl = originalManga.url,
                mangaId = originalManga.id,
                mangaUrl = originalManga.url,
                mangaSourceId = MERGED_SOURCE_ID,
            )
        }

        insertMergedReference.awaitAll(references)

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

        val existingReferences = if (reusingExisting) getMergedReferencesById.await(mergedManga.id) else emptyList()
        val categoryIds = getCategories.await(originalManga.id).map { it.id }
        val references = buildReferences(mergedManga, originalManga, manga, existingReferences)

        return applyMerge.await(staleMangaId, mergedManga, categoryIds, references)
    }

    // Builds the references to add, skipping ones already in [existingReferences]; mergeId is set by [ApplyMerge]
    private fun buildReferences(
        mergedManga: Manga,
        originalManga: Manga,
        manga: Manga,
        existingReferences: List<MergedMangaReference>,
    ): List<MergedMangaReference> {
        return buildList {
            if (existingReferences.none { it.mangaId == originalManga.id && it.mangaSourceId == originalManga.source }) {
                add(
                    MergedMangaReference(
                        id = -1,
                        isInfoManga = true,
                        getChapterUpdates = true,
                        chapterSortMode = 0,
                        chapterPriority = 0,
                        downloadChapters = true,
                        mergeId = null,
                        mergeUrl = mergedManga.url,
                        mangaId = originalManga.id,
                        mangaUrl = originalManga.url,
                        mangaSourceId = originalManga.source,
                    ),
                )
            }
            if (existingReferences.none { it.mangaId == manga.id && it.mangaSourceId == manga.source }) {
                add(
                    MergedMangaReference(
                        id = -1,
                        isInfoManga = false,
                        getChapterUpdates = true,
                        chapterSortMode = 0,
                        chapterPriority = 0,
                        downloadChapters = true,
                        mergeId = null,
                        mergeUrl = mergedManga.url,
                        mangaId = manga.id,
                        mangaUrl = manga.url,
                        mangaSourceId = manga.source,
                    ),
                )
            }
            if (existingReferences.none { it.mangaSourceId == MERGED_SOURCE_ID }) {
                add(
                    MergedMangaReference(
                        id = -1,
                        isInfoManga = false,
                        getChapterUpdates = false,
                        chapterSortMode = 0,
                        chapterPriority = -1,
                        downloadChapters = false,
                        mergeId = null,
                        mergeUrl = mergedManga.url,
                        mangaId = mergedManga.id,
                        mangaUrl = mergedManga.url,
                        mangaSourceId = MERGED_SOURCE_ID,
                    ),
                )
            }
        }
    }
}
