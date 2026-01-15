package eu.kanade.tachiyomi.network

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NetworkPreferences(
    private val preferenceStore: PreferenceStore,
    private val verboseLogging: Boolean = false,
) {

    /**
     * Accesses the user's verbose network logging preference.
     *
     * @return `true` if verbose logging is enabled, `false` otherwise.
     */
    fun verboseLogging(): Preference<Boolean> {
        return preferenceStore.getBoolean("verbose_logging", verboseLogging)
    }

    /**
     * Provides the user preference controlling whether FlareSolverr integration is enabled.
     *
     * @return `true` if FlareSolverr is enabled, `false` otherwise.
     */
    fun enableFlareSolverr(): Preference<Boolean> {
        return preferenceStore.getBoolean("enable_flaresolverr", false)
    }

    /**
     * Provides the FlareSolverr service URL preference.
     *
     * @return The configured FlareSolverr endpoint as a `String`. Defaults to "http://localhost:8191/v1".
     */
    fun flareSolverrUrl(): Preference<String> {
        return preferenceStore.getString("flaresolverr_url", "http://localhost:8191/v1")
    }

    /**
     * Exposes the preference that controls whether FlareSolverr notifications are shown.
     *
     * @return `true` if FlareSolverr notifications should be shown, `false` otherwise.
     */
    fun showFlareSolverrNotifications(): Preference<Boolean> {
        return preferenceStore.getBoolean("show_flaresolverr_notifications", true)
    }

    /**
     * Exposes the user's selected DNS-over-HTTPS (DoH) provider preference.
     *
     * @return The selected DoH provider id, or -1 if no provider is selected.
     */
    fun dohProvider(): Preference<Int> {
        return preferenceStore.getInt("doh_provider", -1)
    }

    fun defaultUserAgent(): Preference<String> {
        return preferenceStore.getString(
            "default_user_agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
        )
    }
}
