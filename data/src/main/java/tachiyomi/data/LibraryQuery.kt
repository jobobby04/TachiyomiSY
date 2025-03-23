package tachiyomi.data

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import exh.source.MERGED_SOURCE_ID
import tachiyomi.view.LibraryView

private val mapper = { cursor: SqlCursor ->
    LibraryView(
        _id = cursor.getLong(0)!!,
        source = cursor.getLong(1)!!,
        url = cursor.getString(2)!!,
        artist = cursor.getString(3),
        author = cursor.getString(4),
        description = cursor.getString(5),
        genre = cursor.getString(6)?.let(StringListColumnAdapter::decode),
        title = cursor.getString(7)!!,
        status = cursor.getLong(8)!!,
        thumbnail_url = cursor.getString(9),
        favorite = cursor.getLong(10)!! == 1L,
        last_update = cursor.getLong(11),
        next_update = cursor.getLong(12),
        initialized = cursor.getLong(13)!! == 1L,
        viewer = cursor.getLong(14)!!,
        chapter_flags = cursor.getLong(15)!!,
        cover_last_modified = cursor.getLong(16)!!,
        date_added = cursor.getLong(17)!!,
        filtered_scanlators = null,
        update_strategy = UpdateStrategyColumnAdapter.decode(cursor.getLong(19)!!),
        calculate_interval = cursor.getLong(20)!!,
        last_modified_at = cursor.getLong(21)!!,
        favorite_modified_at = cursor.getLong(22),
        version = cursor.getLong(23)!!,
        is_syncing = cursor.getLong(24)!!,
        totalCount = cursor.getDouble(25)!!,
        uniqueChapterCount = cursor.getLong(26)!!,
        readCount = cursor.getDouble(27)!!,
        uniqueChapterReadCount = cursor.getDouble(28)!!,
        latestUpload = cursor.getLong(29)!!,
        chapterFetchedAt = cursor.getLong(30)!!,
        lastRead = cursor.getLong(31)!!,
        bookmarkCount = cursor.getDouble(32)!!,
        category = cursor.getLong(33)!!,
    )
}

class LibraryQuery(
    val driver: SqlDriver,
    val condition: String = "M.favorite = 1",
) : ExecutableQuery<LibraryView>(mapper) {

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
        return driver.executeQuery(
            null,
            """
            SELECT
                M.*,
                coalesce(sum(C.total), 0) AS totalCount,
                count(*) AS uniqueChapterCount,
                coalesce(sum(C.readCount), 0) AS readCount,
                coalesce(sum(C.chapterRead), 0) AS uniqueChapterReadCount,
                coalesce(max(C.latestUpload), 0) AS latestUpload,
                coalesce(max(C.fetchedAt), 0) AS chapterFetchedAt,
                coalesce(max(C.lastRead), 0) AS lastRead,
                coalesce(sum(C.bookmarkCount), 0) AS bookmarkCount,
                coalesce(MC.category_id, 0) AS category
            FROM mangas M
            LEFT JOIN(
                SELECT
                    chapters.manga_id,
                    count(*) AS total,
                    sum(read) AS readCount,
                    max(read) AS chapterRead,
                    coalesce(max(chapters.date_upload), 0) AS latestUpload,
                    coalesce(max(history.last_read), 0) AS lastRead,
                    coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                    sum(chapters.bookmark) AS bookmarkCount
                FROM chapters
                LEFT JOIN excluded_scanlators
                ON chapters.manga_id = excluded_scanlators.manga_id
                AND chapters.scanlator = excluded_scanlators.scanlator
                LEFT JOIN history
                ON chapters._id = history.chapter_id
                WHERE excluded_scanlators.scanlator IS NULL
                GROUP BY chapters.manga_id, chapters.chapter_number
            ) AS C
            ON M._id = C.manga_id
            LEFT JOIN mangas_categories AS MC
            ON MC.manga_id = M._id
            WHERE $condition AND M.source <> $MERGED_SOURCE_ID
            GROUP BY M._id
            UNION
            SELECT
                M.*,
                coalesce(sum(C.total), 0) AS totalCount,
                count(*) AS uniqueChapterCount,
                coalesce(sum(C.readCount), 0) AS readCount,
                coalesce(sum(C.chapterRead), 0) AS uniqueChapterReadCount,
                coalesce(max(C.latestUpload), 0) AS latestUpload,
                coalesce(max(C.fetchedAt), 0) AS chapterFetchedAt,
                coalesce(max(C.lastRead), 0) AS lastRead,
                coalesce(sum(C.bookmarkCount), 0) AS bookmarkCount,
                coalesce(MC.category_id, 0) AS category
            FROM mangas M
            LEFT JOIN (
                SELECT merged.manga_id,merged.merge_id
                FROM merged
                GROUP BY merged.merge_id
            ) as ME
            ON ME.merge_id = M._id
            LEFT JOIN(
                SELECT
                    ME.merge_id,
                    count(*) AS total,
                    sum(read) AS readCount,
                    max(read) AS chapterRead,
                    coalesce(max(chapters.date_upload), 0) AS latestUpload,
                    coalesce(max(history.last_read), 0) AS lastRead,
                    coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                    sum(chapters.bookmark) AS bookmarkCount
                FROM chapters
                LEFT JOIN excluded_scanlators
                ON chapters.manga_id = excluded_scanlators.manga_id
                AND chapters.scanlator = excluded_scanlators.scanlator
                LEFT JOIN history
                ON chapters._id = history.chapter_id
                LEFT JOIN merged as ME
                ON ME.manga_id = chapters.manga_id
                WHERE excluded_scanlators.scanlator IS NULL
                GROUP BY ME.merge_id, chapters.chapter_number
            ) AS C
            ON ME.merge_id = C.merge_id
            LEFT JOIN mangas_categories AS MC
            ON MC.manga_id = M._id
            WHERE $condition AND M.source = $MERGED_SOURCE_ID
            GROUP BY M._id;
            """.trimIndent(),
            mapper,
            parameters = 0,
        )
    }

    override fun toString(): String = "LibraryQuery.sq:get"
}
