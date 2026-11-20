package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.Source
import exh.source.getMainSource
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga

/**
 * SY-specific file
 */
@Inject
class GetPagePreviews(
    private val pagePreviewCache: PagePreviewCache,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun await(manga: Manga, source: Source, page: Int, refresh: Boolean = false): Result {
        @Suppress("NAME_SHADOWING")
        val source = source.getMainSource<PagePreviewSource>() ?: return Result.Unused
        val chapters = getChaptersByMangaId.await(manga.id).sortedByDescending { it.sourceOrder }
        val chapterIds = chapters.map { it.id }
        return try {
            val pagePreviews = if (refresh) {
                source.getPagePreviewList(manga.toSManga(), chapters.map { it.toSChapter() }, page).also {
                    pagePreviewCache.putPageListToCache(manga, chapterIds, it)
                }
            } else {
                try {
                    pagePreviewCache.getPageListFromCache(manga, chapterIds, page)
                } catch (_: Exception) {
                    source.getPagePreviewList(manga.toSManga(), chapters.map { it.toSChapter() }, page).also {
                        pagePreviewCache.putPageListToCache(manga, chapterIds, it)
                    }
                }
            }
            Result.Success(
                pagePreviews.pagePreviews.map {
                    PagePreview(it.index, it.imageUrl, source.id, it.cacheKey)
                },
                pagePreviews.hasNextPage,
                pagePreviews.pagePreviewPages,
            )
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed class Result {
        data object Unused : Result()
        data class Success(
            val pagePreviews: List<PagePreview>,
            val hasNextPage: Boolean,
            val pageCount: Int?,
        ) : Result()
        data class Error(val error: Throwable) : Result()
    }
}
