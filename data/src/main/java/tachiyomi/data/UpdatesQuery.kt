package tachiyomi.data

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import exh.source.MERGED_SOURCE_ID
import tachiyomi.view.UpdatesView
import kotlin.time.Instant

/**
 * SY-specific file
 */
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
        cursor.getString(16),
        cursor.getString(17),
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
                WITH merged_base AS MATERIALIZED (
                    SELECT
                        chapter.id,
                        chapter.manga_id,
                        chapter.remote_chapter_number,
                        chapter.remote_date_upload,
                        chapter.state_date_fetch,
                        chapter.user_read,
                        chapter.user_bookmark,
                        chapter.user_last_page_read,
                        ME.merge_id,
                        ME.chapter_priority,
                        coalesce(SELF.chapter_sort_mode, 0) AS sort_mode
                    FROM chapter
                    JOIN merged AS ME
                        ON ME.manga_id = chapter.manga_id
                    LEFT JOIN merged AS SELF
                        ON SELF.merge_id = ME.merge_id
                        AND SELF.manga_source = $MERGED_SOURCE_ID
                )
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
                        excluded_scanlator.scanlator AS excludedScanlator,
                        manga.user_title AS userTitle,
                        manga.user_thumbnail_url AS userThumbnailUrl
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
                        excluded_scanlator.scanlator AS excludedScanlator,
                        manga.user_title AS userTitle,
                        manga.user_thumbnail_url AS userThumbnailUrl
                    FROM merged_base AS base
                    JOIN chapter
                        ON chapter.id = base.id
                    JOIN manga
                        ON manga.id = base.merge_id
                    LEFT JOIN excluded_scanlator
                        ON base.merge_id = excluded_scanlator.manga_id
                        AND chapter.remote_scanlator = excluded_scanlator.scanlator
                    WHERE manga.source_id = $MERGED_SOURCE_ID
                    AND chapter.state_date_fetch > manga.user_favorite_at
                    AND (
                        base.sort_mode IN (0, 1)
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
                    )
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

    override fun toString(): String = "UpdatesQuery"
}
