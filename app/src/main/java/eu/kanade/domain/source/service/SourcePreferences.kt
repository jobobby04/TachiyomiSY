package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getLongArray
import tachiyomi.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    preferenceStore: PreferenceStore,
) {

    val sourceDisplayMode: Preference<LibraryDisplayMode> = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val enabledLanguages: Preference<Set<String>> = preferenceStore.getStringSet(
        "source_languages",
        LocaleHelper.getDefaultEnabledLanguages(),
    )

    val disabledSources: Preference<Set<String>> = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    val incognitoExtensions: Preference<Set<String>> = preferenceStore.getStringSet("incognito_extensions", emptySet())

    val pinnedSources: Preference<Set<String>> = preferenceStore.getStringSet("pinned_catalogues", emptySet())

    val lastUsedSource: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    val showNsfwSource: Preference<Boolean> = preferenceStore.getBoolean("show_nsfw_source", true)

    val migrationSortingMode: Preference<SetMigrateSorting.Mode> = preferenceStore.getEnum(
        "pref_migration_sorting",
        SetMigrateSorting.Mode.ALPHABETICAL,
    )

    val migrationSortingDirection: Preference<SetMigrateSorting.Direction> = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    val hideInLibraryItems: Preference<Boolean> = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    val extensionRepos: Preference<Set<String>> = preferenceStore.getStringSet("extension_repos", emptySet())

    val extensionUpdatesCount: Preference<Int> = preferenceStore.getInt("ext_updates_count", 0)

    val trustedExtensions: Preference<Set<String>> = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    val globalSearchFilterState: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    // SY -->
    val enableSourceBlacklist: Preference<Boolean> = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    val sourcesTabCategories: Preference<Set<String>> = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    val sourcesTabCategoriesFilter: Preference<Boolean> = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    val sourcesTabSourcesInCategories: Preference<Set<String>> = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    val dataSaver: Preference<DataSaver> = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    val dataSaverIgnoreJpeg: Preference<Boolean> = preferenceStore.getBoolean("ignore_jpeg", false)

    val dataSaverIgnoreGif: Preference<Boolean> = preferenceStore.getBoolean("ignore_gif", true)

    val dataSaverImageQuality: Preference<Int> = preferenceStore.getInt("data_saver_image_quality", 80)

    val dataSaverImageFormatJpeg: Preference<Boolean> = preferenceStore.getBoolean("data_saver_image_format_jpeg", false)

    val dataSaverServer: Preference<String> = preferenceStore.getString("data_saver_server", "")

    val dataSaverColorBW: Preference<Boolean> = preferenceStore.getBoolean("data_saver_color_bw", false)

    val dataSaverExcludedSources: Preference<Set<String>> = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    val dataSaverDownloader: Preference<Boolean> = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
    }

    val allowLocalSourceHiddenFolders: Preference<Boolean> = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    val preferredMangaDexId: Preference<String> = preferenceStore.getString("preferred_mangaDex_id", "0")

    val mangadexSyncToLibraryIndexes: Preference<Set<String>> = preferenceStore.getStringSet(
        "pref_mangadex_sync_to_library_indexes",
        emptySet(),
    )

    val recommendationSearchFlags: Preference<Int> = preferenceStore.getInt("rec_search_flags", Int.MAX_VALUE)
    // SY <--

    val migrationSources: Preference<List<Long>> = preferenceStore.getLongArray("migration_sources", emptyList())

    val migrationFlags: Preference<Set<MigrationFlag>> = preferenceStore.getObjectFromInt(
        key = "migration_flags",
        defaultValue = MigrationFlag.entries.toSet(),
        serializer = { MigrationFlag.toBit(it) },
        deserializer = { value: Int -> MigrationFlag.fromBit(value) },
    )

    val migrationDeepSearchMode: Preference<Boolean> = preferenceStore.getBoolean("migration_deep_search", false)

    val migrationPrioritizeByChapters: Preference<Boolean> = preferenceStore.getBoolean(
        "migration_prioritize_by_chapters",
        false,
    )

    val migrationHideUnmatched: Preference<Boolean> = preferenceStore.getBoolean("migration_hide_unmatched", false)

    val migrationHideWithoutUpdates: Preference<Boolean> = preferenceStore.getBoolean(
        "migration_hide_without_updates",
        false,
    )
}
