package tachiyomi.data

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import exh.source.MERGED_SOURCE_ID
import tachiyomi.view.LibraryView

private val mapper = { cursor: SqlCursor ->
    LibraryView(
        id = cursor.getLong(0)!!,
        source_id = cursor.getLong(1)!!,
        remote_url = cursor.getString(2)!!,
        remote_title = cursor.getString(3)!!,
        remote_author = cursor.getString(4),
        remote_artist = cursor.getString(5),
        remote_description = cursor.getString(6),
        remote_genre = cursor.getString(7)?.let(StringListColumnAdapter::decode),
        remote_status = cursor.getLong(8)!!,
        remote_cover = cursor.getString(9),
        remote_update_strategy = UpdateStrategyColumnAdapter.decode(cursor.getLong(10)!!),
        remote_memo = MemoColumnAdapter.decode(cursor.getBytes(11)!!),
        // Entries read outside the library have no favorite time, which the view's type cannot hold
        user_favorite_at = InstantColumnAdapter.decode(cursor.getLong(12) ?: 0L),
        user_notes = cursor.getString(13)!!,
        user_reader_flags = cursor.getLong(14)!!,
        user_chapter_flags = cursor.getLong(15)!!,
        state_chapter_last_update = cursor.getLong(16)?.let(InstantColumnAdapter::decode),
        state_chapter_next_update = cursor.getLong(17)?.let(InstantColumnAdapter::decode),
        state_chapter_fetch_interval = cursor.getLong(18)!!,
        state_cover_last_modified = cursor.getLong(19)?.let(InstantColumnAdapter::decode),
        state_initialized = cursor.getLong(20)!! == 1L,
        // SY -->
        state_last_modified_at = cursor.getLong(21)!!,
        state_version = cursor.getLong(22)!!,
        state_is_syncing = cursor.getLong(23)!!,
        // SY <--
        totalCount = cursor.getLong(24)!!,
        readCount = cursor.getDouble(25)!!,
        latestUpload = cursor.getLong(26)?.let(InstantColumnAdapter::decode),
        chapterFetchedAt = cursor.getLong(27)?.let(InstantColumnAdapter::decode),
        lastRead = cursor.getLong(28)?.let(InstantColumnAdapter::decode),
        bookmarkCount = cursor.getDouble(29)!!,
        categories = cursor.getString(30)!!,
    )
}

fun getLibraryQuery(driver: SqlDriver, condition: String = "M.user_favorite_at IS NOT NULL"): LibraryQuery {
    return LibraryQuery(
        driver,
        condition,
    )
}

class LibraryQuery(
    val driver: SqlDriver,
    val condition: String = "M.user_favorite_at IS NOT NULL",
) : ExecutableQuery<LibraryView>(mapper) {

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
        return driver.executeQuery(
            null,
            """
            SELECT
                M.*,
                coalesce(C.total, 0) AS totalCount,
                coalesce(C.readCount, 0) AS readCount,
                C.latestUpload AS latestUpload,
                C.fetchedAt AS chapterFetchedAt,
                -- Joining history to every chapter is the slowest part of the query, and only entries with excluded
                -- scanlators need the chapters to leave theirs out
                CASE WHEN EXISTS (SELECT 0 FROM excluded_scanlator WHERE excluded_scanlator.manga_id = M.id)
                THEN (
                    SELECT max(history.read_at)
                    FROM history
                    JOIN chapter
                    ON chapter.id = history.chapter_id
                    WHERE history.manga_id = M.id
                    AND NOT EXISTS (
                        SELECT 0 FROM excluded_scanlator
                        WHERE excluded_scanlator.manga_id = M.id
                        AND excluded_scanlator.scanlator = chapter.remote_scanlator
                    )
                )
                ELSE (SELECT max(history.read_at) FROM history WHERE history.manga_id = M.id)
                END AS lastRead,
                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                coalesce(MC.categories, '0') AS categories
            FROM manga M
            LEFT JOIN (
                SELECT
                    chapter.manga_id,
                    count(*) AS total,
                    sum(user_read) AS readCount,
                    max(chapter.remote_date_upload) AS latestUpload,
                    max(chapter.state_date_fetch) AS fetchedAt,
                    sum(chapter.user_bookmark) AS bookmarkCount
                FROM chapter
                LEFT JOIN excluded_scanlator
                ON chapter.manga_id = excluded_scanlator.manga_id
                AND chapter.remote_scanlator = excluded_scanlator.scanlator
                WHERE excluded_scanlator.scanlator IS NULL
                GROUP BY chapter.manga_id
            ) AS C
            ON M.id = C.manga_id
            LEFT JOIN (
                SELECT manga_id, group_concat(category_id) AS categories
                FROM manga_category
                GROUP BY manga_id
            ) AS MC
            ON MC.manga_id = M.id
            WHERE $condition AND M.source_id <> $MERGED_SOURCE_ID
            UNION
            SELECT
                M.*,
                coalesce(C.total, 0) AS totalCount,
                coalesce(C.readCount, 0) AS readCount,
                C.latestUpload AS latestUpload,
                C.fetchedAt AS chapterFetchedAt,
                -- The history of the merged entries, read by entry rather than joined to every chapter
                (
                    SELECT max(history.read_at)
                    FROM history
                    JOIN chapter
                    ON chapter.id = history.chapter_id
                    WHERE history.manga_id IN (SELECT merged.manga_id FROM merged WHERE merged.merge_id = M.id)
                    AND NOT EXISTS (
                        SELECT 0 FROM excluded_scanlator
                        WHERE excluded_scanlator.manga_id = chapter.manga_id
                        AND excluded_scanlator.scanlator = chapter.remote_scanlator
                    )
                ) AS lastRead,
                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                coalesce(MC.categories, '0') AS categories
            FROM manga M
            LEFT JOIN (
                SELECT merged.manga_id,merged.merge_id
                FROM merged
                GROUP BY merged.merge_id
            ) as ME
            ON ME.merge_id = M.id
            LEFT JOIN (
                SELECT
                    ME.merge_id,
                    count(*) AS total,
                    sum(user_read) AS readCount,
                    max(chapter.remote_date_upload) AS latestUpload,
                    max(chapter.state_date_fetch) AS fetchedAt,
                    sum(chapter.user_bookmark) AS bookmarkCount
                FROM chapter
                LEFT JOIN excluded_scanlator
                ON chapter.manga_id = excluded_scanlator.manga_id
                AND chapter.remote_scanlator = excluded_scanlator.scanlator
                LEFT JOIN merged as ME
                ON ME.manga_id = chapter.manga_id
                WHERE excluded_scanlator.scanlator IS NULL
                GROUP BY ME.merge_id
            ) AS C
            ON ME.merge_id = C.merge_id
            LEFT JOIN (
                SELECT manga_id, group_concat(category_id) AS categories
                FROM manga_category
                GROUP BY manga_id
            ) AS MC
            ON MC.manga_id = M.id
            WHERE $condition AND M.source_id = $MERGED_SOURCE_ID;
            """.trimIndent(),
            mapper,
            parameters = 0,
        )
    }

    override fun toString(): String = "LibraryQuery.sq:get"
}
