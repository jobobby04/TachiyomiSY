package tachiyomi.data

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

class InMemoryDatabase : AutoCloseable {

    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    val database = Database(
        driver = driver,
        historyAdapter = History.Adapter(read_atAdapter = InstantColumnAdapter),
        mangaAdapter = Manga.Adapter(
            remote_genreAdapter = StringListColumnAdapter,
            remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
            remote_memoAdapter = MemoColumnAdapter,
            user_favorite_atAdapter = InstantColumnAdapter,
            user_genreAdapter = StringListColumnAdapter,
            state_chapter_last_updateAdapter = InstantColumnAdapter,
            state_chapter_next_updateAdapter = InstantColumnAdapter,
            state_cover_last_modifiedAdapter = InstantColumnAdapter,
        ),
        chapterAdapter = Chapter.Adapter(
            remote_date_uploadAdapter = InstantColumnAdapter,
            remote_memoAdapter = MemoColumnAdapter,
            user_last_page_readAdapter = IntColumnAdapter,
            state_date_fetchAdapter = InstantColumnAdapter,
        ),
        manga_trackAdapter = Manga_track.Adapter(
            start_dateAdapter = InstantColumnAdapter,
            finish_dateAdapter = InstantColumnAdapter,
        ),
    )

    suspend fun create() {
        Database.Schema.awaitCreate(driver)
    }

    suspend fun execute(sql: String) {
        driver.execute(null, sql, 0).await()
    }

    override fun close() {
        driver.close()
    }
}
