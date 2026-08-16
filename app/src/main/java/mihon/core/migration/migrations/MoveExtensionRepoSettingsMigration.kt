package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class MoveExtensionRepoSettingsMigration(
    private val context: Application,
    private val preferenceStore: PreferenceStore,
    private val sourcePreferences: SourcePreferences,
) : Migration {
    override val version: Float = 60f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        sourcePreferences.extensionRepos.getAndSet {
            it.map { "https://raw.githubusercontent.com/$it/repo" }.toSet()
        }
        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key.startsWith("pref_mangasync_") || it.key.startsWith("track_token_") },
            newKey = { Preference.privateKey(it) },
        )
        prefs.edit {
            remove(Preference.appStateKey("trusted_signatures"))
        }

        return@withIOContext true
    }
}
