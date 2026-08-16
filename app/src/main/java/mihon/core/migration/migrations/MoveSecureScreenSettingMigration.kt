package mihon.core.migration.migrations

import android.app.Application
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class MoveSecureScreenSettingMigration(
    private val context: Application,
    private val securityPreferences: SecurityPreferences,
) : Migration {
    override val version: Float = 27f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldSecureScreen = prefs.getBoolean("secure_screen", false)
        if (oldSecureScreen) {
            securityPreferences.secureScreen.set(SecurityPreferences.SecureScreenMode.ALWAYS)
        }

        return@withIOContext true
    }
}
