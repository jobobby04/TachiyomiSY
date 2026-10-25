package tachiyomi.data.source

import tachiyomi.domain.source.model.SavedSearch

/**
 * SY-specific file
 */
object SavedSearchMapper {
    fun map(
        id: Long,
        source: Long,
        name: String,
        query: String?,
        filtersJson: String?,
    ): SavedSearch {
        return SavedSearch(
            id = id,
            source = source,
            name = name,
            query = query,
            filtersJson = filtersJson,
        )
    }
}
