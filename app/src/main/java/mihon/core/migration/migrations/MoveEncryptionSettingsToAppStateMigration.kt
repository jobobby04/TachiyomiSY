package mihon.core.migration.migrations

import android.content.Context
import android.widget.Toast
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.util.system.toast
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
class MoveEncryptionSettingsToAppStateMigration(
    private val context: Context,
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = 66f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(Preference.privateKey("encrypt_database"), false)) {
            withContext(Dispatchers.Main) {
                context.toast(
                    "Restart the app to load your encrypted library",
                    Toast.LENGTH_LONG,
                )
            }
        }

        val appStatePrefsToReplace = listOf(
            "__PRIVATE_sql_password",
            "__PRIVATE_encrypt_database",
            "__PRIVATE_cbz_password",
        )

        MigrateUtils.replacePreferences(
            preferenceStore = preferenceStore,
            filterPredicate = { it.key in appStatePrefsToReplace },
            newKey = { Preference.appStateKey(it.replace("__PRIVATE_", "").trim()) },
        )

        return@withContext true
    }
}
