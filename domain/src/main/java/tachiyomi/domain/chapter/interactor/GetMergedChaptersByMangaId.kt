package tachiyomi.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.model.MergedMangaReference

/**
 * SY-specific file
 */
@Inject
class GetMergedChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
    private val getMergedReferencesById: GetMergedReferencesById,
) {

    suspend fun await(
        mangaId: Long,
        dedupe: Boolean = true,
        applyScanlatorFilter: Boolean = false,
    ): List<Chapter> {
        return try {
            val sortMode = if (dedupe) sortModeOf(getMergedReferencesById.await(mangaId)) else MergedMangaReference.CHAPTER_SORT_NONE
            val chapters = chapterRepository.getDedupedMergedChapters(mangaId, sortMode, applyScanlatorFilter)
            applyPriorityOrder(chapters, dedupe, sortMode)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(
        mangaId: Long,
        dedupe: Boolean = true,
        applyScanlatorFilter: Boolean = false,
    ): Flow<List<Chapter>> {
        return getMergedReferencesById.subscribe(mangaId)
            .map { references -> if (dedupe) sortModeOf(references) else MergedMangaReference.CHAPTER_SORT_NONE }
            .distinctUntilChanged()
            .flatMapLatest { sortMode ->
                chapterRepository.getDedupedMergedChaptersAsFlow(mangaId, sortMode, applyScanlatorFilter)
                    .map { chapters -> applyPriorityOrder(chapters, dedupe, sortMode) }
            }
            .catch { e ->
                logcat(LogPriority.ERROR, e)
                emit(emptyList())
            }
    }

    private fun sortModeOf(references: List<MergedMangaReference>): Int =
        references.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }
            ?.chapterSortMode
            ?: MergedMangaReference.CHAPTER_SORT_NONE

    private fun applyPriorityOrder(chapters: List<Chapter>, dedupe: Boolean, sortMode: Int): List<Chapter> {
        return if (!dedupe || sortMode != MergedMangaReference.CHAPTER_SORT_PRIORITY) {
            chapters
        } else {
            // Orders by chapter number; an unrecognized chapter stays after the number preceding it in its own source.
            val keyed = chapters.groupBy { it.mangaId }.flatMap { (_, sourceChapters) ->
                var anchor = Double.NEGATIVE_INFINITY
                var sinceAnchor = 0
                sourceChapters.sortedBy { it.sourceOrder }.map { chapter ->
                    val key = if (chapter.isRecognizedNumber) {
                        anchor = chapter.chapterNumber
                        sinceAnchor = 0
                        chapter.chapterNumber to 0
                    } else {
                        anchor to (1 + sinceAnchor++)
                    }
                    chapter to key
                }
            }
            keyed
                .sortedWith(compareBy({ it.second.first }, { it.second.second }))
                .map { it.first }
                .reversed()
                .mapIndexed { index, chapter -> chapter.copy(sourceOrder = index.toLong()) }
        }
    }
}
