package tachiyomi.data.history

import app.cash.sqldelight.async.coroutines.awaitAsList
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.InMemoryDatabase

class HistoryDeletionTest {

    private val db = InMemoryDatabase()
    private val repository = HistoryRepositoryImpl(db.database)

    @AfterEach
    fun close() {
        db.close()
    }

    @Test
    fun `a tombstone newer than the local read deletes the row`() = runTest {
        create()
        insertHistory(chapterId = 11, readAt = 1_700_000_050_000L)

        repository.deleteHistory(listOf(11L), at = 1_700_000_100L)

        remainingChapterIds().shouldBeEmpty()
    }

    @Test
    fun `a tombstone older than the local read keeps the row`() = runTest {
        create()
        insertHistory(chapterId = 11, readAt = 1_700_000_050_000L)

        repository.deleteHistory(listOf(11L), at = 1_700_000_000L)

        // the read happened after the removal: a device that had not uploaded it yet must not lose it
        remainingChapterIds().shouldContainExactly(listOf(11L))
    }

    @Test
    fun `a read in the tombstone's own second gives way to it`() = runTest {
        create()
        insertHistory(chapterId = 11, readAt = 1_700_000_000_900L)

        repository.deleteHistory(listOf(11L), at = 1_700_000_000L)

        // the server keeps whole seconds and drops the row when its second is the tombstone's, so must this
        remainingChapterIds().shouldBeEmpty()
    }

    @Test
    fun `a tombstone without an action time deletes unconditionally`() = runTest {
        create()
        insertHistory(chapterId = 11, readAt = 1_700_000_050_000L)

        repository.deleteHistory(listOf(11L))

        // 0 means the tombstone carries no time, not the epoch: it applies to every read it names
        remainingChapterIds().shouldBeEmpty()
    }

    @Test
    fun `a row with no read left is deleted by a timed tombstone`() = runTest {
        create()
        insertHistory(chapterId = 11, readAt = null)

        repository.deleteHistory(listOf(11L), at = 1_700_000_000L)

        // there is no read to compare, so nothing protects the row
        remainingChapterIds().shouldBeEmpty()
    }

    private suspend fun create() {
        db.create()
        db.execute(
            """
            INSERT INTO manga (
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy, remote_memo,
                user_favorite_at, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized
            ) VALUES (1, 1, '/m', 'm', 0, 0, X'7B7D', NULL, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
    }

    private suspend fun insertHistory(chapterId: Long, readAt: Long?) {
        db.execute(
            """
            INSERT INTO chapter (
                id, manga_id, remote_url, remote_name, remote_chapter_number, remote_order, remote_memo,
                user_read, user_bookmark, user_last_page_read, state_date_fetch
            ) VALUES ($chapterId, 1, '/m/ch$chapterId', 'ch$chapterId', 1.0, 1, X'7B7D', 1, 0, 0, 0)
            """.trimIndent(),
        )
        db.execute(
            "INSERT INTO history (manga_id, chapter_id, read_at, read_duration) " +
                "VALUES (1, $chapterId, ${readAt ?: "NULL"}, 0)",
        )
    }

    private suspend fun remainingChapterIds(): List<Long> =
        db.database.historyQueries
            .getHistoryByMangaId(1L) { _, chapterId, _, _ -> chapterId }
            .awaitAsList()
}
