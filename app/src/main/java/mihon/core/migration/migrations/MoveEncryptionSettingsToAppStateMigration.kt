package mihon.core.migration.migrations

import android.app.Application
import android.widget.Toast
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.util.system.toast
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext

@Inject
@ContributesIntoSet(AppScope::class)
class MoveEncryptionSettingsToAppStateMigration(
    private val context: Application,
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = 66f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(Preference.privateKey("encrypt_database"), false)) {
            withUIContext {
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

        return@withIOContext true
    }
}
