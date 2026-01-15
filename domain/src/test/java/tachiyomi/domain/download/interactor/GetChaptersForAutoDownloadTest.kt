package tachiyomi.domain.download.interactor

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import java.util.Date
import java.util.concurrent.TimeUnit

class GetChaptersForAutoDownloadTest {

    private lateinit var historyRepository: HistoryRepository
    private lateinit var getManga: GetManga
    private lateinit var getNextChapters: GetNextChapters
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var getChaptersForAutoDownload: GetChaptersForAutoDownload

    @BeforeEach
    fun setup() {
        // Initialize Koin for HistoryWithRelations dependency injection
        val mockGetCustomMangaInfo = mockk<GetCustomMangaInfo>()
        every { mockGetCustomMangaInfo.get(any()) } returns null

        startKoin {
            modules(
                module {
                    single { mockGetCustomMangaInfo }
                },
            )
        }

        historyRepository = mockk()
        getManga = mockk()
        getNextChapters = mockk()
        downloadPreferences = mockk()
        getChaptersForAutoDownload = GetChaptersForAutoDownload(
            historyRepository = historyRepository,
            getManga = getManga,
            getNextChapters = getNextChapters,
            downloadPreferences = downloadPreferences,
        )
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `returns empty list when auto download is disabled`() = runTest {
        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns false

        val result = getChaptersForAutoDownload.await()

        result.shouldBeEmpty()
        coVerify(exactly = 0) { historyRepository.getHistoryForAutoDownload(any()) }
    }

    @Test
    fun `returns empty list when no history entries`() = runTest {
        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns emptyList()

        val result = getChaptersForAutoDownload.await()

        result.shouldBeEmpty()
    }

    @Test
    fun `returns chapters for manga in reading history`() = runTest {
        val manga = createManga(id = 1L)
        val history = createHistory(mangaId = 1L, chapterId = 100L)
        val chapters = listOf(
            createChapter(id = 101L, mangaId = 1L, number = 2.0),
            createChapter(id = 102L, mangaId = 1L, number = 3.0),
        )

        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns listOf(history)
        coEvery { getManga.await(1L) } returns manga
        coEvery { getNextChapters.await(1L, 100L, true) } returns chapters

        val result = getChaptersForAutoDownload.await()

        result shouldHaveSize 1
        result[0].first shouldBe manga
        result[0].second shouldBe chapters
    }

    @Test
    fun `limits chapters per manga based on preference`() = runTest {
        val manga = createManga(id = 1L)
        val history = createHistory(mangaId = 1L, chapterId = 100L)
        val allChapters = (1..10).map { createChapter(id = 100L + it, mangaId = 1L, number = it.toDouble()) }

        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns listOf(history)
        coEvery { getManga.await(1L) } returns manga
        coEvery { getNextChapters.await(1L, 100L, true) } returns allChapters

        val result = getChaptersForAutoDownload.await()

        result shouldHaveSize 1
        result[0].second shouldHaveSize 3
        result[0].second[0].id shouldBe 101L
        result[0].second[1].id shouldBe 102L
        result[0].second[2].id shouldBe 103L
    }

    @Test
    fun `skips manga with no next chapters`() = runTest {
        val history = createHistory(mangaId = 1L, chapterId = 100L)

        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns listOf(history)
        coEvery { getManga.await(1L) } returns createManga(id = 1L)
        coEvery { getNextChapters.await(1L, 100L, true) } returns emptyList()

        val result = getChaptersForAutoDownload.await()

        result.shouldBeEmpty()
    }

    @Test
    fun `skips manga that no longer exists`() = runTest {
        val history = createHistory(mangaId = 1L, chapterId = 100L)

        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns listOf(history)
        coEvery { getManga.await(1L) } returns null

        val result = getChaptersForAutoDownload.await()

        result.shouldBeEmpty()
    }

    @Test
    fun `deduplicates manga by taking most recent history entry`() = runTest {
        val manga = createManga(id = 1L)
        val oldHistory = createHistory(mangaId = 1L, chapterId = 100L, readAt = Date(1000L))
        val newHistory = createHistory(mangaId = 1L, chapterId = 101L, readAt = Date(2000L))
        val chapters = listOf(createChapter(id = 102L, mangaId = 1L, number = 3.0))

        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns listOf(oldHistory, newHistory)
        coEvery { getManga.await(1L) } returns manga
        coEvery { getNextChapters.await(1L, 101L, true) } returns chapters

        val result = getChaptersForAutoDownload.await()

        result shouldHaveSize 1
        coVerify(exactly = 1) { getNextChapters.await(1L, 101L, true) }
        coVerify(exactly = 0) { getNextChapters.await(1L, 100L, true) }
    }

    @Test
    fun `handles multiple manga correctly`() = runTest {
        val manga1 = createManga(id = 1L)
        val manga2 = createManga(id = 2L)
        val history1 = createHistory(mangaId = 1L, chapterId = 100L)
        val history2 = createHistory(mangaId = 2L, chapterId = 200L)
        val chapters1 = listOf(createChapter(id = 101L, mangaId = 1L, number = 2.0))
        val chapters2 = listOf(createChapter(id = 201L, mangaId = 2L, number = 2.0))

        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns listOf(history1, history2)
        coEvery { getManga.await(1L) } returns manga1
        coEvery { getManga.await(2L) } returns manga2
        coEvery { getNextChapters.await(1L, 100L, true) } returns chapters1
        coEvery { getNextChapters.await(2L, 200L, true) } returns chapters2

        val result = getChaptersForAutoDownload.await()

        result shouldHaveSize 2
    }

    @Test
    fun `uses correct lookback period from preferences`() = runTest {
        val lookbackDays = 14
        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns lookbackDays
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns emptyList()

        getChaptersForAutoDownload.await()

        coVerify {
            historyRepository.getHistoryForAutoDownload(
                match { readAfter ->
                    val expectedTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(lookbackDays.toLong())
                    // Allow 1 second tolerance for test execution time
                    Math.abs(readAfter.time - expectedTime) < 1000
                },
            )
        }
    }

    @Test
    fun `coerces lookback days to minimum of 1`() = runTest {
        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 0
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 3
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns emptyList()

        getChaptersForAutoDownload.await()

        coVerify {
            historyRepository.getHistoryForAutoDownload(
                match { readAfter ->
                    val expectedTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1L)
                    Math.abs(readAfter.time - expectedTime) < 1000
                },
            )
        }
    }

    @Test
    fun `coerces chapters per manga to minimum of 1`() = runTest {
        val manga = createManga(id = 1L)
        val history = createHistory(mangaId = 1L, chapterId = 100L)
        val chapters = (1..5).map { createChapter(id = 100L + it, mangaId = 1L, number = it.toDouble()) }

        coEvery { downloadPreferences.autoDownloadFromReadingHistory().get() } returns true
        coEvery { downloadPreferences.autoDownloadReadingHistoryDays().get() } returns 7
        coEvery { downloadPreferences.autoDownloadWhileReading().get() } returns 0
        coEvery { historyRepository.getHistoryForAutoDownload(any()) } returns listOf(history)
        coEvery { getManga.await(1L) } returns manga
        coEvery { getNextChapters.await(1L, 100L, true) } returns chapters

        val result = getChaptersForAutoDownload.await()

        result shouldHaveSize 1
        result[0].second shouldHaveSize 1
    }

    private fun createManga(id: Long) = Manga.create().copy(id = id)

    private fun createHistory(mangaId: Long, chapterId: Long, readAt: Date = Date()) =
        HistoryWithRelations(
            id = 1L,
            chapterId = chapterId,
            mangaId = mangaId,
            ogTitle = "Test Manga",
            chapterNumber = 1.0,
            readAt = readAt,
            readDuration = 0L,
            coverData = MangaCover(
                mangaId = mangaId,
                sourceId = 1L,
                isMangaFavorite = false,
                ogUrl = "",
                lastModified = 0L,
            ),
        )

    private fun createChapter(id: Long, mangaId: Long, number: Double) =
        Chapter.create().copy(id = id, mangaId = mangaId, chapterNumber = number)
}
