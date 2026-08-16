package eu.kanade.domain.ui

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Inject
@SingleIn(AppScope::class)
class UiPreferences(
    preferenceStore: PreferenceStore,
) {

    val themeMode: Preference<ThemeMode> = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    val appTheme: Preference<AppTheme> = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    val themeDarkAmoled: Preference<Boolean> = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    val relativeTime: Preference<Boolean> = preferenceStore.getBoolean("relative_time_v2", true)

    val dateFormat: Preference<String> = preferenceStore.getString("app_date_format", "")

    val tabletUiMode: Preference<TabletUiMode> = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    val imagesInDescription: Preference<Boolean> = preferenceStore.getBoolean("pref_render_images_description", true)

    // SY -->

    val expandFilters: Preference<Boolean> = preferenceStore.getBoolean("eh_expand_filters", false)

    val hideFeedTab: Preference<Boolean> = preferenceStore.getBoolean("hide_latest_tab", false)

    val feedTabInFront: Preference<Boolean> = preferenceStore.getBoolean("latest_tab_position", false)

    val recommendsInOverflow: Preference<Boolean> = preferenceStore.getBoolean("recommends_in_overflow", false)

    val mergeInOverflow: Preference<Boolean> = preferenceStore.getBoolean("merge_in_overflow", true)

    val previewsRowCount: Preference<Int> = preferenceStore.getInt("pref_previews_row_count", 4)

    val useNewSourceNavigation: Preference<Boolean> = preferenceStore.getBoolean("use_new_source_navigation", true)

    val bottomBarLabels: Preference<Boolean> = preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)

    val showNavUpdates: Preference<Boolean> = preferenceStore.getBoolean("pref_show_updates_button", true)

    val showNavHistory: Preference<Boolean> = preferenceStore.getBoolean("pref_show_history_button", true)

    // SY <--

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
