package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class ChangeThemeModeToUppercaseMigration(
    private val context: Context,
    private val uiPreferences: UiPreferences,
) : Migration {
    override val version: Float = 42f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (uiPreferences.themeMode.isSet()) {
            prefs.edit {
                val themeMode = prefs.getString(uiPreferences.themeMode.key(), null) ?: return@edit
                putString(uiPreferences.themeMode.key(), themeMode.uppercase())
            }
        }

        return@withContext true
    }
}
