package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.editTextPreference
import eu.kanade.tachiyomi.util.preference.switchPreference

class SettingsExperimentController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "Experimental Features"

        switchPreference {
            key = PreferenceKeys.enableCompression
            title = "Enable Compression"
            defaultValue = false
        }

        switchPreference {
            key = PreferenceKeys.filterWebp
            title = "Filter WEBp"
            defaultValue = false
        }
        switchPreference {
            key = PreferenceKeys.filterJpeg
            title = "Filter JPEG"
            defaultValue = false
        }

        editTextPreference {
            key = PreferenceKeys.compressionHost
            title = "Compression Host"
            summary = preferences.compressionHost().get()
            defaultValue = ""
        }

    }
}
