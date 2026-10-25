package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.util.system.DeviceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * SY-specific file
 */
@Inject
@ContributesIntoSet(AppScope::class)
class ChangeMiuiExtensionInstallerMigration(
    private val basePreferences: BasePreferences,
) : Migration {
    override val version: Float = 27f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        if (
            DeviceUtil.isMiui &&
            basePreferences.extensionInstaller.get() == BasePreferences.ExtensionInstaller
                .PACKAGEINSTALLER
        ) {
            basePreferences.extensionInstaller.set(BasePreferences.ExtensionInstaller.LEGACY)
        }

        return@withContext true
    }
}
