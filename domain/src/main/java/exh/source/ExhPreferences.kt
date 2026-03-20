package exh.source

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class ExhPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // SY -->
    val isHentaiEnabled: Preference<Boolean> = preferenceStore.getBoolean("eh_is_hentai_enabled", true)

    val enableExhentai: Preference<Boolean> = preferenceStore.getBoolean(Preference.Companion.privateKey("enable_exhentai"), false)

    val imageQuality: Preference<String> = preferenceStore.getString("ehentai_quality", "auto")

    val useHentaiAtHome: Preference<Int> = preferenceStore.getInt("eh_enable_hah", 0)

    val useJapaneseTitle: Preference<Boolean> = preferenceStore.getBoolean("use_jp_title", false)

    val exhUseOriginalImages: Preference<Boolean> = preferenceStore.getBoolean("eh_useOrigImages", false)

    val ehTagFilterValue: Preference<Int> = preferenceStore.getInt("eh_tag_filtering_value", 0)

    val ehTagWatchingValue: Preference<Int> = preferenceStore.getInt("eh_tag_watching_value", 0)

    // EH Cookies
    val memberIdVal: Preference<String> = preferenceStore.getString(Preference.Companion.privateKey("eh_ipb_member_id"), "")

    val passHashVal: Preference<String> = preferenceStore.getString(Preference.Companion.privateKey("eh_ipb_pass_hash"), "")
    val igneousVal: Preference<String> = preferenceStore.getString(Preference.Companion.privateKey("eh_igneous"), "")
    val ehSettingsProfile: Preference<Int> = preferenceStore.getInt(Preference.Companion.privateKey("eh_ehSettingsProfile"), -1)
    val exhSettingsProfile: Preference<Int> = preferenceStore.getInt(Preference.Companion.privateKey("eh_exhSettingsProfile"), -1)
    val exhSettingsKey: Preference<String> = preferenceStore.getString(Preference.Companion.privateKey("eh_settingsKey"), "")
    val exhSessionCookie: Preference<String> = preferenceStore.getString(Preference.Companion.privateKey("eh_sessionCookie"), "")
    val exhHathPerksCookies: Preference<String> = preferenceStore.getString(Preference.Companion.privateKey("eh_hathPerksCookie"), "")

    val exhShowSyncIntro: Preference<Boolean> = preferenceStore.getBoolean("eh_show_sync_intro", true)

    val exhReadOnlySync: Preference<Boolean> = preferenceStore.getBoolean("eh_sync_read_only", false)

    val exhLenientSync: Preference<Boolean> = preferenceStore.getBoolean("eh_lenient_sync", false)

    val exhShowSettingsUploadWarning: Preference<Boolean> = preferenceStore.getBoolean("eh_showSettingsUploadWarning2", true)

    val logLevel: Preference<Int> = preferenceStore.getInt("eh_log_level", 0)

    val exhAutoUpdateFrequency: Preference<Int> = preferenceStore.getInt("eh_auto_update_frequency", 1)

    val exhAutoUpdateRequirements: Preference<Set<String>> = preferenceStore.getStringSet("eh_auto_update_restrictions", emptySet())

    val exhAutoUpdateStats: Preference<String> = preferenceStore.getString(Preference.Companion.appStateKey("eh_auto_update_stats"), "")

    val exhWatchedListDefaultState: Preference<Boolean> = preferenceStore.getBoolean("eh_watched_list_default_state", false)

    val exhSettingsLanguages: Preference<String> = preferenceStore.getString(
        "eh_settings_languages",
        "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false",
    )

    val exhEnabledCategories: Preference<String> = preferenceStore.getString(
        "eh_enabled_categories",
        "false,false,false,false,false,false,false,false,false,false",
    )

    val enhancedEHentaiView: Preference<Boolean> = preferenceStore.getBoolean("enhanced_e_hentai_view", true)
}
