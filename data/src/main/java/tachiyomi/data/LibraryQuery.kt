package tachiyomi.data

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import exh.source.MERGED_SOURCE_ID
import tachiyomi.view.LibraryView

/**
 * SY-specific file
 */
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
        state_last_modified_at = cursor.getLong(21)!!,
        state_version = cursor.getLong(22)!!,
        state_is_syncing = cursor.getLong(23)!!,
        user_title = cursor.getString(24),
        user_author = cursor.getString(25),
        user_artist = cursor.getString(26),
        user_thumbnail_url = cursor.getString(27),
        user_description = cursor.getString(28),
        user_genre = cursor.getString(29)?.let(StringListColumnAdapter::decode),
        user_status = cursor.getLong(30),
        totalCount = cursor.getLong(31)!!,
        readCount = cursor.getDouble(32)!!,
        latestUpload = cursor.getLong(33)?.let(InstantColumnAdapter::decode),
        chapterFetchedAt = cursor.getLong(34)?.let(InstantColumnAdapter::decode),
        lastRead = cursor.getLong(35)?.let(InstantColumnAdapter::decode),
        bookmarkCount = cursor.getDouble(36)!!,
        categories = cursor.getString(37)!!,
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
            WITH merged_base AS MATERIALIZED (
                SELECT
                    chapter.id,
                    chapter.manga_id,
                    chapter.remote_chapter_number,
                    chapter.remote_date_upload,
                    chapter.state_date_fetch,
                    chapter.user_read,
                    chapter.user_bookmark,
                    ME.merge_id,
                    ME.chapter_priority,
                    MG.chapter_sort_mode AS sort_mode
                FROM chapter
                LEFT JOIN excluded_scanlator
                ON chapter.manga_id = excluded_scanlator.manga_id
                AND chapter.remote_scanlator = excluded_scanlator.scanlator
                JOIN merged_entry AS ME
                ON ME.manga_id = chapter.manga_id
                JOIN merge AS MG
                ON MG.manga_id = ME.merge_id
                WHERE excluded_scanlator.scanlator IS NULL
            )
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
                    WHERE history.manga_id IN (SELECT merged_entry.manga_id FROM merged_entry WHERE merged_entry.merge_id = M.id)
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
                SELECT
                    base.merge_id,
                    count(*) AS total,
                    sum(base.user_read) AS readCount,
                    max(base.remote_date_upload) AS latestUpload,
                    max(base.state_date_fetch) AS fetchedAt,
                    sum(base.user_bookmark) AS bookmarkCount
                FROM merged_base AS base
                WHERE base.sort_mode IN (0, 1)
                OR (base.sort_mode = 2 AND (
                    base.remote_chapter_number < 0
                    OR NOT EXISTS (
                        SELECT 1 FROM merged_base AS base2
                        WHERE base2.merge_id = base.merge_id
                        AND base2.remote_chapter_number = base.remote_chapter_number
                        AND base2.manga_id != base.manga_id
                        AND (
                            base2.chapter_priority < base.chapter_priority
                            OR (base2.chapter_priority = base.chapter_priority AND base2.manga_id < base.manga_id)
                        )
                    )
                ))
                OR (base.sort_mode = 3 AND base.manga_id = (
                    SELECT base2.manga_id FROM merged_base AS base2
                    WHERE base2.merge_id = base.merge_id
                    GROUP BY base2.manga_id
                    ORDER BY count(*) DESC, base2.manga_id
                    LIMIT 1
                ))
                OR (base.sort_mode = 4 AND base.manga_id = (
                    SELECT base2.manga_id FROM merged_base AS base2
                    WHERE base2.merge_id = base.merge_id
                    AND base2.remote_chapter_number >= 0
                    GROUP BY base2.manga_id
                    ORDER BY max(base2.remote_chapter_number) DESC, base2.manga_id
                    LIMIT 1
                ))
                GROUP BY base.merge_id
            ) AS C
            ON C.merge_id = M.id
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
