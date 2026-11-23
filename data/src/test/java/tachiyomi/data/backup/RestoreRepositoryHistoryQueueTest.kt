package tachiyomi.data.backup

import app.cash.sqldelight.async.coroutines.awaitAsList
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.InMemoryDatabase
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.data.manga.MangaMetadataRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.time.Instant

class RestoreRepositoryHistoryQueueTest {

    private val db = InMemoryDatabase()
    private val mangaRepository = MangaRepositoryImpl(db.database, db.driver)
    private val chapterRepository = ChapterRepositoryImpl(db.database)
    private val historyRepository = HistoryRepositoryImpl(db.database)
    private val restoreRepository = RestoreRepositoryImpl(
        database = db.database,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        trackRepository = TrackRepositoryImpl(db.database),
        mangaMergeRepository = MangaMergeRepositoryImpl(db.database),
        mangaMetadataRepository = MangaMetadataRepositoryImpl(db.database),
    )

    @BeforeEach
    fun create() = runTest {
        db.create()
    }

    @AfterEach
    fun close() {
        db.close()
    }

    @Test
    fun `a restore read is refused over a queued removal in either branch`() = runTest {
        restore(readAt = 1_000L, chapterUrls = URLS)
        val manga = requireNotNull(mangaRepository.getMangaByUrlAndSourceId(URL, SOURCE))
        val chapterIds = chapterIdsByUrl(manga.id)
        // /c1 and /c2 were removed here and their tombstones are still owed to the server; /c3 was not
        historyRepository.resetHistoryByMangaId(
            mangaId = manga.id,
            tombstones = historyRepository.getHistoryTombstoneKeys(manga.id).filter { it.chapterUrl != "/c3" },
            removedAtMs = 5_000L,
        )
        // "clear database" takes a reset row, so /c1's removal has no history row in front of it at all
        db.execute("DELETE FROM history WHERE chapter_id = ${chapterIds.getValue("/c1")}")
        historyRepository.getPendingHistoryDeletions().map { it.chapterUrl }.shouldContainExactly("/c1", "/c2")

        restore(readAt = 9_000_000L, chapterUrls = URLS)

        // only a local read cancels a queued removal: no branch of the restore writes a read over one
        readAtOf(chapterIds.getValue("/c1")) shouldBe null
        readAtOf(chapterIds.getValue("/c2")) shouldBe null
        historyRepository.getPendingHistoryDeletions().map { it.chapterUrl }.shouldContainExactly("/c1", "/c2")
        // the untouched entry is restored, so the guard is not refusing the whole payload
        readAtOf(chapterIds.getValue("/c3")) shouldBe Instant.fromEpochMilliseconds(9_000_000L)
    }

    @Test
    fun `a restore restores every read when nothing was removed locally`() = runTest {
        restore(readAt = 1_000L, chapterUrls = URLS)

        restore(readAt = 9_000_000L, chapterUrls = URLS)

        historyRepository.getPendingHistoryDeletions().shouldBeEmpty()
        readAtOf(chapterIdsByUrl(mangaId()).getValue("/c1")) shouldBe Instant.fromEpochMilliseconds(9_000_000L)
    }

    private suspend fun restore(readAt: Long, chapterUrls: List<String>) {
        val chapters = chapterUrls.map {
            Chapter.create().copy(url = it, name = it, read = true, lastPageRead = 1, version = 1)
        }
        val entry = RestoredManga(
            manga = Manga.create().copy(
                source = SOURCE,
                url = URL,
                ogTitle = "m",
                favoriteAt = Instant.fromEpochSeconds(1_000),
                version = 1,
            ),
            chapters = chapters,
            categoryIds = emptyList(),
            history = chapterUrls.map {
                RestoredHistory(chapterUrl = it, readAt = Instant.fromEpochMilliseconds(readAt), readDuration = 10)
            },
            tracks = emptyList(),
            excludedScanlators = emptyList(),
        )

        restoreRepository.restoreManga(listOf(entry), isSync = false) { MangaUpdate(it.id) {} }
        mangaRepository.resetIsSyncing()
        chapterRepository.resetIsSyncing()
    }

    private suspend fun mangaId(): Long = requireNotNull(mangaRepository.getMangaByUrlAndSourceId(URL, SOURCE)).id

    private suspend fun chapterIdsByUrl(mangaId: Long): Map<String, Long> =
        chapterRepository.getChapterByMangaId(mangaId).associate { it.url to it.id }

    private suspend fun readAtOf(chapterId: Long): Instant? =
        db.database.historyQueries
            .getHistoryByMangaId(mangaId()) { _, chapterId_, readAt, _ -> chapterId_ to readAt }
            .awaitAsList()
            .singleOrNull { it.first == chapterId }
            ?.second

    private companion object {
        const val SOURCE = 1L
        const val URL = "/m"
        val URLS = listOf("/c1", "/c2", "/c3")
    }
}
