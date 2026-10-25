package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class ChapterNameSuffixMigration(
    private val preference: LibraryPreferences,
    // SY -->
    private val preferenceStore: PreferenceStore,
    // SY <--
) : Migration {
    override val version: Float = /* SY --> */83f/* SY <-- */

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        // SY -->
        // Carry over SY's own toggle for the same suffix, which defaulted to on
        val syIncludeChapterUrlHash = preferenceStore.getBoolean("download_include_chapter_url_hash", true)
        if (migrationContext.previousVersion > 0) {
            preference.enableChapterNameHash.set(syIncludeChapterUrlHash.get())
        }
        syIncludeChapterUrlHash.delete()
        // SY <--
        return true
    }
}
