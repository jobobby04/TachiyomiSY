package tachiyomi.data

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import exh.source.MERGED_SOURCE_ID
import tachiyomi.view.UpdatesView

private val mapper = { cursor: SqlCursor ->
    UpdatesView(
        cursor.getLong(0)!!,
        cursor.getString(1)!!,
        cursor.getLong(2)!!,
        cursor.getString(3)!!,
        cursor.getString(4),
        cursor.getString(5)!!,
        cursor.getLong(6)!! == 1L,
        cursor.getLong(7)!! == 1L,
        cursor.getLong(8)!!,
        cursor.getLong(9)!!,
        cursor.getLong(10)!! == 1L,
        cursor.getString(11),
        cursor.getLong(12)!!,
        cursor.getLong(13)!!,
        cursor.getLong(14)!!,
        cursor.getString(15),
    )
}

class UpdatesQuery(
    val driver: SqlDriver,
    val after: Long,
    val limit: Long,
    val read: Boolean?,
    val started: Long?,
    val bookmarked: Boolean?,
    val hideExcludedScanlators: Long,
) : ExecutableQuery<UpdatesView>(mapper) {
    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
        return driver.executeQuery(
            null,
            """
                SELECT *
                FROM (
                    -- Normal source
                    SELECT
                        mangas._id AS mangaId,
                        mangas.title AS mangaTitle,
                        chapters._id AS chapterId,
                        chapters.name AS chapterName,
                        chapters.scanlator,
                        chapters.url AS chapterUrl,
                        chapters.read,
                        chapters.bookmark,
                        chapters.last_page_read,
                        mangas.source,
                        mangas.favorite,
                        mangas.thumbnail_url AS thumbnailUrl,
                        mangas.cover_last_modified AS coverLastModified,
                        chapters.date_upload AS dateUpload,
                        chapters.date_fetch AS datefetch,
                        excluded_scanlators.scanlator AS excludedScanlator
                    FROM mangas
                    JOIN chapters
                        ON mangas._id = chapters.manga_id
                    LEFT JOIN excluded_scanlators
                        ON mangas._id = excluded_scanlators.manga_id
                        AND chapters.scanlator = excluded_scanlators.scanlator
                    WHERE mangas.source <> $MERGED_SOURCE_ID
                    AND date_fetch > date_added

                    UNION ALL

                    -- Merged source
                    SELECT
                        mangas._id AS mangaId,
                        mangas.title AS mangaTitle,
                        chapters._id AS chapterId,
                        chapters.name AS chapterName,
                        chapters.scanlator,
                        chapters.url AS chapterUrl,
                        chapters.read,
                        chapters.bookmark,
                        chapters.last_page_read,
                        mangas.source,
                        mangas.favorite,
                        mangas.thumbnail_url AS thumbnailUrl,
                        mangas.cover_last_modified AS coverLastModified,
                        chapters.date_upload AS dateUpload,
                        chapters.date_fetch AS datefetch,
                        excluded_scanlators.scanlator AS excludedScanlator
                    FROM mangas
                    LEFT JOIN (
                        SELECT merged.manga_id, merged.merge_id
                        FROM merged
                        GROUP BY merged.merge_id
                    ) AS ME
                        ON ME.merge_id = mangas._id
                    JOIN chapters
                        ON ME.manga_id = chapters.manga_id
                    LEFT JOIN excluded_scanlators
                        ON ME.merge_id = excluded_scanlators.manga_id
                        AND chapters.scanlator = excluded_scanlators.scanlator
                    WHERE mangas.source = $MERGED_SOURCE_ID
                    AND date_fetch > date_added
                ) AS combined
                WHERE
                    favorite = 1
                    AND dateUpload > :after
                    AND (:read IS NULL OR read = :read)
                    AND (
                        :started IS NULL
                        OR (:started = 1 AND last_page_read > 0 AND read = 0)
                        OR (:started = 0 AND last_page_read = 0 AND read = 0)
                    )
                    AND (:bookmarked IS NULL OR bookmark = :bookmarked)
                    AND (
                        excludedScanlator IS NULL OR :hideExcludedScanlators = 0
                    )
                ORDER BY datefetch DESC
            LIMIT :limit;
            """.trimIndent(),
            mapper,
            6,
            binders = {
                var parameterIndex = 0
                bindLong(parameterIndex++, after)
                bindBoolean(parameterIndex++, read)
                bindLong(parameterIndex++, started)
                bindBoolean(parameterIndex++, bookmarked)
                bindLong(parameterIndex++, hideExcludedScanlators)
                bindLong(parameterIndex++, limit)
            },
        )
    }

    override fun toString(): String = "LibraryQuery.sq:get"
}
