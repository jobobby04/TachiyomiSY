package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

class SavedSearchRepositoryImpl(
    private val database: Database,
) : SavedSearchRepository {

    override suspend fun getById(savedSearchId: Long): SavedSearch? {
        return database.saved_searchQueries
            .selectById(savedSearchId, SavedSearchMapper::map)
            .awaitAsOneOrNull()
    }

    override suspend fun getBySourceId(sourceId: Long): List<SavedSearch> {
        return database.saved_searchQueries
            .selectBySource(sourceId, SavedSearchMapper::map)
            .awaitAsList()
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<SavedSearch>> {
        return database.saved_searchQueries
            .selectBySource(sourceId, SavedSearchMapper::map)
            .subscribeToList()
    }

    override suspend fun delete(savedSearchId: Long) {
        database.saved_searchQueries
            .deleteById(savedSearchId)
    }

    override suspend fun insert(savedSearch: SavedSearch): Long {
        return database.saved_searchQueries.insert(
            savedSearch.source,
            savedSearch.name,
            savedSearch.query,
            savedSearch.filtersJson,
        ).awaitAsOne()
    }

    override suspend fun insertAll(savedSearch: List<SavedSearch>) {
        database.transaction {
            savedSearch.forEach {
                database.saved_searchQueries.insert(
                    it.source,
                    it.name,
                    it.query,
                    it.filtersJson,
                ).awaitAsOneOrNull()
            }
        }
    }
}
