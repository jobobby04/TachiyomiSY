package eu.kanade.domain.source.service

import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.util.system.LocaleHelper

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun sourceDisplayMode() = preferenceStore.getObject("pref_display_mode_catalogue", LibraryDisplayMode.default, LibraryDisplayMode.Serializer::serialize, LibraryDisplayMode.Serializer::deserialize)

    fun enabledLanguages() = preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun disabledSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet("pinned_catalogues", emptySet())

    fun duplicatePinnedSources() = preferenceStore.getBoolean("duplicate_pinned_sources", false)

    fun lastUsedSource() = preferenceStore.getLong("last_catalogue_source", -1)

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    fun migrationSortingDirection() = preferenceStore.getEnum("pref_migration_direction", SetMigrateSorting.Direction.ASCENDING)

    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun trustedSignatures() = preferenceStore.getStringSet("trusted_signatures", emptySet())

    fun searchPinnedSourcesOnly() = preferenceStore.getBoolean("search_pinned_sources_only", false)

    // SY -->
    fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    fun sourcesTabCategories() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    fun sourcesTabCategoriesFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    fun sourcesTabSourcesInCategories() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getInt("data_saver", 0)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean("data_saver_image_format_jpeg", false)

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)
    // SY <--
}
