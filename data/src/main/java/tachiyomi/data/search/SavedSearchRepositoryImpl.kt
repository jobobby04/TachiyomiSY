package tachiyomi.data.search

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import tachiyomi.data.Database
import tachiyomi.domain.search.model.SavedSearch
import tachiyomi.domain.search.repository.SavedSearchRepository

/**
 * SY-specific file
 */
@Inject
@ContributesBinding(AppScope::class)
class SavedSearchRepositoryImpl(
    private val database: Database,
) : SavedSearchRepository {

    override suspend fun getAll(): List<SavedSearch> {
        return database.saved_searchQueries
            .selectAll { _, source, name, query, filtersJson ->
                SavedSearch(
                    source = source,
                    name = name,
                    query = query,
                    filtersJson = filtersJson,
                )
            }
            .awaitAsList()
    }

    override suspend fun insert(search: SavedSearch) {
        database.saved_searchQueries.insert(
            source = search.source,
            name = search.name,
            query = search.query,
            filtersJson = search.filtersJson,
        )
    }

    override suspend fun insertAll(searches: List<SavedSearch>) {
        database.transaction {
            searches.forEach { insert(it) }
        }
    }

    override suspend fun deleteAll() {
        database.saved_searchQueries.deleteAll()
    }
}
