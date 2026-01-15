package tachiyomi.domain.download.interactor

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import java.util.Date
import java.util.concurrent.TimeUnit

class GetChaptersForAutoDownload(
    private val historyRepository: HistoryRepository,
    private val getManga: GetManga,
    private val getNextChapters: GetNextChapters,
    private val downloadPreferences: DownloadPreferences,
) {

    /**
     * Compute mangas and their unread chapters to auto-download based on recent reading history and download preferences.
     *
     * Respects the user's settings for enabling auto-download, the lookback window for recent reads, and the maximum
     * chapters to download per manga; mangas with no available unread chapters or missing metadata are excluded.
     *
     * @return A list of pairs where each pair contains a `Manga` and the corresponding list of unread `Chapter` objects
     *         selected for auto-download.
     */
    suspend fun await(): List<Pair<Manga, List<Chapter>>> {
        if (!downloadPreferences.autoDownloadFromReadingHistory().get()) return emptyList()

        val lookbackDays = downloadPreferences.autoDownloadReadingHistoryDays().get().coerceAtLeast(1)
        val readAfter = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(lookbackDays.toLong()))
        val chaptersPerManga = downloadPreferences.autoDownloadWhileReading().get().coerceAtLeast(1)

        return historyRepository.getHistoryForAutoDownload(readAfter)
            .sortedByDescending { it.readAt }
            .distinctBy { it.mangaId }
            .mapNotNull { history ->
                val manga = getManga.await(history.mangaId) ?: return@mapNotNull null
                val chapters = getNextChapters.await(
                    mangaId = history.mangaId,
                    fromChapterId = history.chapterId,
                    onlyUnread = true,
                ).take(chaptersPerManga)
                if (chapters.isEmpty()) null else manga to chapters
            }
    }
}
