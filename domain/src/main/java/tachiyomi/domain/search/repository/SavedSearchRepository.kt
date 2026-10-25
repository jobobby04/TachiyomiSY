package tachiyomi.domain.search.repository

import tachiyomi.domain.search.model.SavedSearch

/**
 * SY-specific file
 */
interface SavedSearchRepository {

    suspend fun getAll(): List<SavedSearch>

    suspend fun insert(search: SavedSearch)

    suspend fun insertAll(searches: List<SavedSearch>)

    suspend fun deleteAll()
}
