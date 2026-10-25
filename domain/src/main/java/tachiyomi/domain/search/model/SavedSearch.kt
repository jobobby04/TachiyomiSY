package tachiyomi.domain.search.model

/**
 * SY-specific file
 */
data class SavedSearch(
    val source: Long,
    val name: String,
    val query: String?,
    val filtersJson: String?,
)
