package tachiyomi.data.history

import app.cash.sqldelight.async.coroutines.awaitAsList
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.InMemoryDatabase
import tachiyomi.domain.history.model.HistoryTombstoneKey
import tachiyomi.domain.history.model.HistoryUpdate
import kotlin.time.Instant

class HistoryPendingDeletionTest {

    private val db = InMemoryDatabase()
    private val repository = HistoryRepositoryImpl(db.database)

    @AfterEach
    fun close() {
        db.close()
    }

    @Test
    fun `a per-entry removal queues one row`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)

        repository.resetHistory(historyId = historyId(11), tombstones = listOf(key(11, 1, "/m")), removedAtMs = 1_000L)

        repository.getPendingHistoryDeletions().map { it.sourceId to it.chapterUrl }.shouldContainExactly(1L to "/m/ch11")
        // the moment of the removal is kept with the row, in milliseconds like `read_at`
        repository.getPendingHistoryDeletions().single().removedAtMs shouldBe 1_000L
        // and the removal itself is the reset it always was
        remainingChapterIds(1).shouldContainExactly(listOf(11L))
        readAtOf(11) shouldBe null
    }

    @Test
    fun `a per-manga removal queues one row per entry of that manga only`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)
        insertHistory(chapterId = 12, mangaId = 1, readAt = 500_000L)
        insertHistory(chapterId = 21, mangaId = 2, readAt = 500_000L)

        repository.resetHistoryByMangaId(
            mangaId = 1,
            tombstones = listOf(key(11, 1, "/m"), key(12, 1, "/m")),
            removedAtMs = 1_000L,
        )

        repository.getPendingHistoryDeletions().map { it.mangaUrl to it.chapterUrl }
            .shouldContainExactly("/m" to "/m/ch11", "/m" to "/m/ch12")
        // one removal stamps every row it queues with the same moment
        repository.getPendingHistoryDeletions().map { it.removedAtMs }.toSet() shouldBe setOf(1_000L)
        readAtOf(21) shouldBe Instant.fromEpochMilliseconds(500_000L)
    }

    @Test
    fun `clear all queues every entry and keeps the queue`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)
        insertHistory(chapterId = 12, mangaId = 1, readAt = 500_000L)

        repository.deleteAllHistory(
            tombstones = listOf(key(11, 1, "/m"), key(12, 1, "/m")),
            removedAtMs = 1_000L,
        ) shouldBe true

        // "clear all" drops the history rows; the tombstones the server still has to hear about are a
        // table of their own, so they outlive the rows that made them
        remainingChapterIds(1).shouldBeEmpty()
        repository.getPendingHistoryDeletions().map { it.chapterUrl }.shouldContainExactly("/m/ch11", "/m/ch12")
    }

    @Test
    fun `a queued row survives its chapter and its manga being deleted`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)
        repository.resetHistory(historyId = historyId(11), tombstones = listOf(key(11, 1, "/m")), removedAtMs = 1_000L)

        // a source dropping the chapter, and then the user deleting the manga
        db.execute("DELETE FROM chapter WHERE id = 11")
        db.execute("DELETE FROM manga WHERE id = 1")

        remainingChapterIds(1).shouldBeEmpty()
        // the queue holds no foreign key, so the removal is still owed to the server
        repository.getPendingHistoryDeletions().map { it.chapterUrl }.shouldContainExactly("/m/ch11")
    }

    @Test
    fun `a re-read drops the queued entry`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)
        repository.resetHistory(historyId = historyId(11), tombstones = listOf(key(11, 1, "/m")), removedAtMs = 1_000L)

        repository.upsertHistory(
            HistoryUpdate(chapterId = 11, readAt = Instant.fromEpochMilliseconds(2_000L), sessionReadDuration = 5),
        )

        // the read supersedes the removal, so the tombstone must not be sent over it
        repository.getPendingHistoryDeletions().shouldBeEmpty()
        readAtOf(11) shouldBe Instant.fromEpochMilliseconds(2_000L)
    }

    @Test
    fun `a repeated removal keeps one row and its first moment`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)
        repository.resetHistory(historyId = historyId(11), tombstones = listOf(key(11, 1, "/m")), removedAtMs = 1_000L)

        repository.resetHistory(historyId = historyId(11), tombstones = listOf(key(11, 1, "/m")), removedAtMs = 9_000L)

        // the entry was removed at 1000: a second "removal" of the same row is the same tombstone
        repository.getPendingHistoryDeletions().single().removedAtMs shouldBe 1_000L
    }

    @Test
    fun `a removal nothing can deliver queues nothing`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)

        // 0 is the caller saying the configured sync cannot send a tombstone
        repository.resetHistory(historyId = historyId(11), tombstones = listOf(key(11, 1, "/m")))

        repository.getPendingHistoryDeletions().shouldBeEmpty()
        readAtOf(11) shouldBe null
    }

    @Test
    fun `clearing drops the confirmed rows and keeps the rest`() = runTest {
        create()
        insertHistory(chapterId = 11, mangaId = 1, readAt = 500_000L)
        insertHistory(chapterId = 12, mangaId = 1, readAt = 500_000L)
        repository.resetHistoryByMangaId(
            mangaId = 1,
            tombstones = listOf(key(11, 1, "/m"), key(12, 1, "/m")),
            removedAtMs = 1_000L,
        )
        val queued = repository.getPendingHistoryDeletions()

        repository.clearPendingHistoryDeletion(listOf(queued.first().id))

        repository.getPendingHistoryDeletions().map { it.id }.shouldContainExactly(queued.last().id)
    }

    private fun key(chapterId: Long, mangaId: Long, mangaUrl: String = "/m") = HistoryTombstoneKey(
        chapterId = chapterId,
        mangaSourceId = if (mangaId == 1L) 1L else 2L,
        mangaUrl = mangaUrl,
        chapterUrl = "${if (mangaId == 1L) "/m" else "/m2"}/ch$chapterId",
    )

    private suspend fun create() {
        db.create()
        // the chapter and manga of a history row cascade, as they do on the app's own database
        db.execute("PRAGMA foreign_keys = ON")
        db.execute(
            """
            INSERT INTO manga (
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy, remote_memo,
                user_favorite_at, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized
            ) VALUES (1, 1, '/m', 'm', 0, 0, X'7B7D', NULL, '', 0, 0, 0, 0),
                     (2, 2, '/m2', 'm2', 0, 0, X'7B7D', NULL, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
    }

    private suspend fun insertHistory(chapterId: Long, mangaId: Long, readAt: Long?) {
        db.execute(
            """
            INSERT INTO chapter (
                id, manga_id, remote_url, remote_name, remote_chapter_number, remote_order, remote_memo,
                user_read, user_bookmark, user_last_page_read, state_date_fetch
            ) VALUES (
                $chapterId, $mangaId,
                '${if (mangaId == 1L) "/m" else "/m2"}/ch$chapterId',
                'ch$chapterId', 1.0, 1, X'7B7D', 1, 0, 0, 0
            )
            """.trimIndent(),
        )
        db.execute(
            "INSERT INTO history (manga_id, chapter_id, read_at, read_duration) " +
                "VALUES ($mangaId, $chapterId, ${readAt ?: "NULL"}, 0)",
        )
    }

    private suspend fun historyId(chapterId: Long): Long =
        db.database.historyQueries
            .getHistoryByMangaId(1L) { id, chapterId, _, _ -> id to chapterId }
            .awaitAsList()
            .single { it.second == chapterId }
            .first

    private suspend fun readAtOf(chapterId: Long): Instant? =
        db.database.historyQueries
            .getHistoryByMangaId(if (chapterId > 20) 2L else 1L) { id, chapterId, readAt, _ -> Triple(id, chapterId, readAt) }
            .awaitAsList()
            .singleOrNull { it.second == chapterId }
            ?.third

    private suspend fun remainingChapterIds(mangaId: Long): List<Long> =
        db.database.historyQueries
            .getHistoryByMangaId(mangaId) { _, chapterId, _, _ -> chapterId }
            .awaitAsList()
}
