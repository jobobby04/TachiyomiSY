package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class FeedSavedSearchRepositoryImpl(
    private val database: Database,
) : FeedSavedSearchRepository {

    override suspend fun getGlobal(): List<FeedSavedSearch> {
        return database.feed_saved_searchQueries
            .selectAllGlobal(FeedSavedSearchMapper::map)
            .awaitAsList()
    }

    override fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>> {
        return database.feed_saved_searchQueries
            .selectAllGlobal(FeedSavedSearchMapper::map)
            .subscribeToList()
    }

    override suspend fun getGlobalFeedSavedSearch(): List<SavedSearch> {
        return database.feed_saved_searchQueries
            .selectGlobalFeedSavedSearch(SavedSearchMapper::map)
            .awaitAsList()
    }

    override suspend fun countGlobal(): Long {
        return database.feed_saved_searchQueries
            .countGlobal()
            .awaitAsOne()
    }

    override suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch> {
        return database.feed_saved_searchQueries
            .selectBySource(sourceId, FeedSavedSearchMapper::map)
            .awaitAsList()
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>> {
        return database.feed_saved_searchQueries
            .selectBySource(sourceId, FeedSavedSearchMapper::map)
            .subscribeToList()
    }

    override suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch> {
        return database.feed_saved_searchQueries
            .selectSourceFeedSavedSearch(sourceId, SavedSearchMapper::map)
            .awaitAsList()
    }

    override suspend fun countBySourceId(sourceId: Long): Long {
        return database.feed_saved_searchQueries
            .countSourceFeedSavedSearch(sourceId)
            .awaitAsOne()
    }

    override suspend fun delete(feedSavedSearchId: Long) {
        database.feed_saved_searchQueries
            .deleteById(feedSavedSearchId)
    }

    override suspend fun insert(feedSavedSearch: FeedSavedSearch): Long {
        return database.feed_saved_searchQueries.insert(
            feedSavedSearch.source,
            feedSavedSearch.savedSearch,
            feedSavedSearch.global,
        ).awaitAsOne()
    }

    override suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>) {
        return database.transaction {
            feedSavedSearch.forEach {
                database.feed_saved_searchQueries.insert(
                    it.source,
                    it.savedSearch,
                    it.global,
                ).awaitAsOne()
            }
        }
    }
}
