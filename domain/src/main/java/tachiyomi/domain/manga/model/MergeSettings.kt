package tachiyomi.domain.manga.model

/**
 * SY-specific file
 */
data class MergeSettings(
    val mangaId: Long,
    val chapterSortMode: Int,
    val infoMangaId: Long?,
) {
    companion object {
        const val CHAPTER_SORT_NONE = 0
        const val CHAPTER_SORT_NO_DEDUPE = 1
        const val CHAPTER_SORT_PRIORITY = 2
        const val CHAPTER_SORT_MOST_CHAPTERS = 3
        const val CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER = 4

        /**
         * Modes where reordering the entries matters: NONE and NO_DEDUPE group every chapter by
         * entry priority, PRIORITY keeps the highest-priority copy of each number.
         */
        fun isReorderEnabled(chapterSortMode: Int): Boolean =
            chapterSortMode == CHAPTER_SORT_NONE ||
                chapterSortMode == CHAPTER_SORT_NO_DEDUPE ||
                chapterSortMode == CHAPTER_SORT_PRIORITY
    }
}
