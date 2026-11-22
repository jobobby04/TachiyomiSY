package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.InMemoryDatabase

class MangaSyncQueriesTest {

    private val db = InMemoryDatabase()
    private val database = db.database

    @AfterEach
    fun close() {
        db.close()
    }

    @Test
    fun `removing a manga from the library bumps its version`() = runTest {
        create()
        insertManga(id = 1, inLibrary = true)

        setInLibrary(id = 1, inLibrary = false)

        versionOf(1) shouldBe 1
    }

    @Test
    fun `adding a manga to the library bumps its version`() = runTest {
        create()
        insertManga(id = 1, inLibrary = false)

        setInLibrary(id = 1, inLibrary = true)

        versionOf(1) shouldBe 1
    }

    @Test
    fun `a library removal rides the delta upload`() = runTest {
        create()
        insertManga(id = 1, inLibrary = true)
        insertManga(id = 2, inLibrary = true)

        setInLibrary(id = 1, inLibrary = false)

        database.mangaQueries.getMangasModifiedSince(1, MangaMapper::mapManga).awaitAsList()
            .map { it.id to it.favorite } shouldContainExactly listOf(1L to false)
    }

    @Test
    fun `a library removal rides the full upload`() = runTest {
        create()
        insertManga(id = 1, inLibrary = true)
        insertManga(id = 2, inLibrary = true)

        setInLibrary(id = 1, inLibrary = false)

        database.mangaQueries.getMangasToSync(0, MangaMapper::mapManga).awaitAsList()
            .map { it.id to it.favorite } shouldContainExactly listOf(1L to false, 2L to true)
    }

    @Test
    fun `a removal older than the last push is not uploaded again`() = runTest {
        create()
        insertManga(id = 1, inLibrary = true)
        setInLibrary(id = 1, inLibrary = false)

        val afterRemoval = Long.MAX_VALUE

        database.mangaQueries.getMangasToSync(afterRemoval, MangaMapper::mapManga).awaitAsList().shouldBeEmpty()
    }

    @Test
    fun `a removal applied by sync is not uploaded back`() = runTest {
        create()
        insertManga(id = 1, inLibrary = true)

        db.execute("UPDATE manga SET user_favorite_at = NULL, state_is_syncing = 1 WHERE id = 1")
        db.execute("UPDATE manga SET state_is_syncing = 0 WHERE id = 1")

        database.mangaQueries.getMangasToSync(0, MangaMapper::mapManga).awaitAsList().shouldBeEmpty()
    }

    @Test
    fun `a manga never in the library is not uploaded`() = runTest {
        create()
        insertManga(id = 1, inLibrary = false)

        db.execute("UPDATE manga SET remote_description = 'new' WHERE id = 1")

        database.mangaQueries.getMangasToSync(0, MangaMapper::mapManga).awaitAsList().shouldBeEmpty()
        database.mangaQueries.getMangasModifiedSince(0, MangaMapper::mapManga).awaitAsList().shouldBeEmpty()
    }

    private suspend fun create() {
        db.create()
    }

    private suspend fun insertManga(id: Long, inLibrary: Boolean) {
        val favoriteAt = if (inLibrary) "1000" else "NULL"
        db.execute(
            """
            INSERT INTO manga (
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy, remote_memo,
                user_favorite_at, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized
            ) VALUES ($id, 1, '/m$id', 'm$id', 0, 0, X'7B7D', $favoriteAt, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
    }

    private suspend fun setInLibrary(id: Long, inLibrary: Boolean) {
        val favoriteAt = if (inLibrary) "2000" else "NULL"
        db.execute("UPDATE manga SET user_favorite_at = $favoriteAt WHERE id = $id")
    }

    private suspend fun versionOf(id: Long): Long {
        return database.mangaQueries.getMangaById(id, MangaMapper::mapManga).awaitAsOne().version
    }
}
