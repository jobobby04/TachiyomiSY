package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import exh.util.nullIfBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class MoveLatestToFeedMigration(
    private val context: Context,
    private val insertSavedSearch: InsertSavedSearch,
    private val insertFeedSavedSearch: InsertFeedSavedSearch,
) : Migration {
    override val version: Float = 31f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedSearch = prefs.getStringSet("eh_saved_searches", emptySet())?.mapNotNull {
            runCatching {
                val content = Json.decodeFromString<JsonObject>(it.substringAfter(':'))
                SavedSearch(
                    id = -1,
                    source = it.substringBefore(':').toLongOrNull()
                        ?: return@runCatching null,
                    name = content["name"]!!.jsonPrimitive.content,
                    query = content["query"]!!.jsonPrimitive.contentOrNull?.nullIfBlank(),
                    filtersJson = Json.encodeToString(content["filters"]!!.jsonArray),
                )
            }.getOrNull()
        }
        if (!savedSearch.isNullOrEmpty()) {
            insertSavedSearch.awaitAll(savedSearch)
        }
        val feedSavedSearch = prefs.getStringSet("latest_tab_sources", emptySet())?.map {
            FeedSavedSearch(
                id = -1,
                source = it.toLong(),
                savedSearch = null,
                global = true,
            )
        }
        if (!feedSavedSearch.isNullOrEmpty()) {
            insertFeedSavedSearch.awaitAll(feedSavedSearch)
        }
        prefs.edit(commit = true) {
            remove("eh_saved_searches")
            remove("latest_tab_sources")
        }

        return@withContext true
    }
}
