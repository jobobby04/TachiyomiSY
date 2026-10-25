package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class MoveSettingsToPrivateOrAppStateMigration(
    private val context: Context,
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = 59f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefsToReplace = listOf(
            "pref_download_only",
            "incognito_mode",
            "last_catalogue_source",
            "trusted_signatures",
            "last_app_closed",
            "library_update_last_timestamp",
            "library_unseen_updates_count",
            "last_used_category",
            "last_app_check",
            "last_ext_check",
            "last_version_code",
            "skip_pre_migration",
            "eh_auto_update_stats",
            "storage_dir",
        )
        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key in prefsToReplace },
            newKey = { Preference.appStateKey(it) },
        )

        val privatePrefsToReplace = listOf(
            "sql_password",
            "encrypt_database",
            "cbz_password",
            "password_protect_downloads",
            "eh_ipb_member_id",
            "enable_exhentai",
            "eh_ipb_member_id",
            "eh_ipb_pass_hash",
            "eh_igneous",
            "eh_ehSettingsProfile",
            "eh_exhSettingsProfile",
            "eh_settingsKey",
            "eh_sessionCookie",
            "eh_hathPerksCookie",
        )

        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key in privatePrefsToReplace },
            newKey = { Preference.privateKey(it) },
        )

        // Deleting old download cache index files, but might as well clear it all out
        context.cacheDir.deleteRecursively()

        return@withContext true
    }
}
