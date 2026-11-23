package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Guards the migration that introduces the history tombstone queue: `49.sqm` has to produce exactly
 * the table `history.sq` declares, since an upgraded install keeps the migrated one for good.
 *
 * The migration is replayed on its own instead of through `Database.Schema.migrate`, whose older
 * steps depend on state the schema snapshot alone does not carry (and which `verifySqlDelightMigration`
 * cannot replay either).
 */
class PendingHistoryDeletionMigrationTest {

    private val migration: String = Files.readString(
        Path.of("src/main/sqldelight/tachiyomi/migrations/49.sqm"),
    )

    @Test
    fun `the migration creates the queue the schema declares`() {
        runBlocking {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Database.Schema.create(driver).await()
                val declared = driver.columns("pending_history_deletion")
                declared shouldBe listOf("id", "source_id", "manga_url", "chapter_url", "removed_at_ms")

                driver.execute(null, "DROP TABLE pending_history_deletion", 0).await()
                driver.execute(null, migration, 0).await()

                // same columns, in the same order, as the schema a fresh install gets
                driver.columns("pending_history_deletion") shouldBe declared
                driver.insertQueuedEntry(mangaUrl = "/m", chapterUrl = "/m/ch1", removedAtMs = 1_000L)
                // one removal per entry: a repeated removal of the same entry is the same tombstone,
                // which the send clears by its id rather than sending twice
                driver.execute(
                    null,
                    "INSERT OR IGNORE INTO pending_history_deletion(source_id, manga_url, chapter_url, removed_at_ms) " +
                        "VALUES (1, '/m', '/m/ch1', 2000)",
                    0,
                ).await()
                driver.scalarLong("SELECT count(*) FROM pending_history_deletion") shouldBe 1L
                driver.scalarLong("SELECT removed_at_ms FROM pending_history_deletion") shouldBe 1_000L
                // nothing is queued by the upgrade itself: the preference queue it replaces was never released
                driver.execute(null, "DELETE FROM pending_history_deletion", 0).await()
                driver.scalarLong("SELECT count(*) FROM pending_history_deletion") shouldBe 0L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `the queue holds no key into the history it came from`() {
        runBlocking {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Database.Schema.create(driver).await()

                // a queued removal has to outlive its history row, whose chapter and manga cascade
                driver.foreignKeys("pending_history_deletion").shouldBeEmpty()
                driver.insertQueuedEntry(mangaUrl = "/m", chapterUrl = "/m/ch1", removedAtMs = 1_000L)
                // the same chapter url of another manga is a different tombstone
                driver.insertQueuedEntry(mangaUrl = "/m2", chapterUrl = "/m/ch1", removedAtMs = 1_000L)
                driver.scalarLong("SELECT count(*) FROM pending_history_deletion") shouldBe 2L
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun SqlDriver.insertQueuedEntry(mangaUrl: String, chapterUrl: String, removedAtMs: Long) {
        execute(
            null,
            """
            INSERT INTO pending_history_deletion(source_id, manga_url, chapter_url, removed_at_ms)
            VALUES (1, '$mangaUrl', '$chapterUrl', $removedAtMs)
            """.trimIndent(),
            0,
        ).await()
    }

    private fun SqlDriver.columns(table: String): List<String> =
        strings("SELECT name FROM pragma_table_info('$table') ORDER BY cid")

    private fun SqlDriver.foreignKeys(table: String): List<String> =
        strings("SELECT `table` FROM pragma_foreign_key_list('$table')")

    private fun SqlDriver.strings(sql: String): List<String> =
        executeQuery(null, sql, { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += cursor.getString(0).orEmpty()
            QueryResult.Value(rows)
        }, 0).value

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value
}
