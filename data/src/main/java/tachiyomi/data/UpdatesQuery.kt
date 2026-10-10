package tachiyomi.data

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import exh.source.MERGED_SOURCE_ID
import tachiyomi.view.UpdatesView
import kotlin.time.Instant

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
        IntColumnAdapter.decode(cursor.getLong(8)!!),
        cursor.getLong(9)!!,
        cursor.getLong(10)!! == 1L,
        cursor.getString(11),
        cursor.getLong(12)?.let(InstantColumnAdapter::decode),
        cursor.getLong(13)?.let(InstantColumnAdapter::decode),
        InstantColumnAdapter.decode(cursor.getLong(14)!!),
        cursor.getString(15),
    )
}

fun getUpdatesQuery(
    driver: SqlDriver,
    after: Instant,
    limit: Long,
    read: Boolean?,
    started: Long?,
    bookmarked: Boolean?,
    hideExcludedScanlators: Long,
    includedEmpty: Boolean,
    includedCategories: Collection<Long>,
    excludedEmpty: Boolean,
    excludedCategories: Collection<Long>,
): UpdatesQuery {
    return UpdatesQuery(
        driver = driver,
        after = after,
        limit = limit,
        read = read,
        started = started,
        bookmarked = bookmarked,
        hideExcludedScanlators = hideExcludedScanlators,
        includedEmpty = includedEmpty,
        includedCategories = includedCategories,
        excludedEmpty = excludedEmpty,
        excludedCategories = excludedCategories,
    )
}

class UpdatesQuery(
    val driver: SqlDriver,
    val after: Instant,
    val limit: Long,
    val read: Boolean?,
    val started: Long?,
    val bookmarked: Boolean?,
    val hideExcludedScanlators: Long,
    val includedEmpty: Boolean,
    val includedCategories: Collection<Long>,
    val excludedEmpty: Boolean,
    val excludedCategories: Collection<Long>,
) : ExecutableQuery<UpdatesView>(mapper) {
    private fun createArguments(count: Int): String {
        if (count == 0) return "()"

        return buildString(count * 2 + 1) {
            append("(?")
            repeat(count - 1) {
                append(",?")
            }
            append(')')
        }
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
        val includedCategoriesIndexes = createArguments(count = includedCategories.size)
        val excludedCategoriesIndexes = createArguments(count = excludedCategories.size)
        return driver.executeQuery(
            null,
            // language=sqldelight
            """
                SELECT *
                FROM (
                    -- Normal source
                    SELECT
                        manga.id AS mangaId,
                        manga.remote_title AS mangaTitle,
                        chapter.id AS chapterId,
                        chapter.remote_name AS chapterName,
                        chapter.remote_scanlator AS scanlator,
                        chapter.remote_url AS chapterUrl,
                        chapter.user_read AS read,
                        chapter.user_bookmark AS bookmark,
                        chapter.user_last_page_read AS last_page_read,
                        manga.source_id AS source,
                        manga.user_favorite_at IS NOT NULL AS favorite,
                        manga.remote_cover AS thumbnailUrl,
                        manga.state_cover_last_modified AS coverLastModified,
                        chapter.remote_date_upload AS dateUpload,
                        chapter.state_date_fetch AS datefetch,
                        excluded_scanlator.scanlator AS excludedScanlator
                    FROM manga
                    JOIN chapter
                        ON manga.id = chapter.manga_id
                    LEFT JOIN excluded_scanlator
                        ON manga.id = excluded_scanlator.manga_id
                        AND chapter.remote_scanlator = excluded_scanlator.scanlator
                    WHERE manga.source_id <> $MERGED_SOURCE_ID
                    AND chapter.state_date_fetch > manga.user_favorite_at

                    UNION ALL

                    -- Merged source
                    SELECT
                        manga.id AS mangaId,
                        manga.remote_title AS mangaTitle,
                        chapter.id AS chapterId,
                        chapter.remote_name AS chapterName,
                        chapter.remote_scanlator AS scanlator,
                        chapter.remote_url AS chapterUrl,
                        chapter.user_read AS read,
                        chapter.user_bookmark AS bookmark,
                        chapter.user_last_page_read AS last_page_read,
                        manga.source_id AS source,
                        manga.user_favorite_at IS NOT NULL AS favorite,
                        manga.remote_cover AS thumbnailUrl,
                        manga.state_cover_last_modified AS coverLastModified,
                        chapter.remote_date_upload AS dateUpload,
                        chapter.state_date_fetch AS datefetch,
                        excluded_scanlator.scanlator AS excludedScanlator
                    FROM manga
                    LEFT JOIN (
                        SELECT merged.manga_id, merged.merge_id
                        FROM merged
                        GROUP BY merged.merge_id
                    ) AS ME
                        ON ME.merge_id = manga.id
                    JOIN chapter
                        ON ME.manga_id = chapter.manga_id
                    LEFT JOIN excluded_scanlator
                        ON ME.merge_id = excluded_scanlator.manga_id
                        AND chapter.remote_scanlator = excluded_scanlator.scanlator
                    WHERE manga.source_id = $MERGED_SOURCE_ID
                    AND chapter.state_date_fetch > manga.user_favorite_at
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
                    AND (
                        -- includedEmpty being true expresses "don't care" state and bypasses the membership filter
                        :includedEmpty
                        OR EXISTS (
                            SELECT 1 FROM manga_category
                            WHERE manga_category.manga_id = mangaId
                            AND COALESCE(manga_category.category_id, 0) IN $includedCategoriesIndexes
                        )
                        OR (0 IN $includedCategoriesIndexes AND NOT EXISTS (
                            SELECT 1 FROM manga_category mc WHERE mc.manga_id = mangaId)
                        )
                    )
                    AND (
                        -- excludedEmpty being true expresses "don't care" state and bypasses the membership filter
                        :excludedEmpty
                        OR (
                            NOT EXISTS (
                                SELECT 1 FROM manga_category
                                WHERE manga_category.manga_id = mangaId
                                AND COALESCE(manga_category.category_id, 0) IN $excludedCategoriesIndexes
                            )
                            AND (
                                0 NOT IN $excludedCategoriesIndexes
                                OR EXISTS (SELECT 1 FROM manga_category WHERE manga_category.manga_id = mangaId)
                            )
                        )
                    )
                ORDER BY datefetch DESC
            LIMIT :limit;
            """.trimIndent(),
            mapper,
            8 + includedCategories.size + includedCategories.size + excludedCategories.size + excludedCategories.size,
            binders = {
                var parameterIndex = 0
                bindLong(parameterIndex++, InstantColumnAdapter.encode(after))
                bindBoolean(parameterIndex++, read)
                bindLong(parameterIndex++, started)
                bindBoolean(parameterIndex++, bookmarked)
                bindLong(parameterIndex++, hideExcludedScanlators)
                bindBoolean(parameterIndex++, includedEmpty)
                includedCategories.forEach { includedCategories_ ->
                    bindLong(parameterIndex++, includedCategories_)
                }
                includedCategories.forEach { includedCategories__ ->
                    bindLong(parameterIndex++, includedCategories__)
                }
                bindBoolean(parameterIndex++, excludedEmpty)
                excludedCategories.forEach { excludedCategories_ ->
                    bindLong(parameterIndex++, excludedCategories_)
                }
                excludedCategories.forEach { excludedCategories__ ->
                    bindLong(parameterIndex++, excludedCategories__)
                }
                bindLong(parameterIndex++, limit)
            },
        )
    }

    override fun toString(): String = "LibraryQuery.sq:get"
}
