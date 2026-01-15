package eu.kanade.tachiyomi.core.security

import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

class SecurityPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun useAuthenticator() = preferenceStore.getBoolean("use_biometric_lock", false)

    fun lockAppAfter() = preferenceStore.getInt("lock_app_after", 0)

    fun secureScreen() = preferenceStore.getEnum("secure_screen_v2", SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = preferenceStore.getBoolean("hide_notification_content", false)

    // SY -->
    fun authenticatorTimeRanges() = this.preferenceStore.getStringSet("biometric_time_ranges", mutableSetOf())

    fun authenticatorDays() = this.preferenceStore.getInt("biometric_days", 0x7F)

    fun encryptDatabase() = this.preferenceStore.getBoolean(Preference.appStateKey("encrypt_database"), false)

    fun sqlPassword() = this.preferenceStore.getString(Preference.appStateKey("sql_password"), "")

    fun passwordProtectDownloads() = preferenceStore.getBoolean(
        Preference.privateKey("password_protect_downloads"),
        false,
    )

    /**
     * Retrieves the configured encryption type used for stored data.
     *
     * @return The selected [EncryptionType]; defaults to `EncryptionType.AES_256` if not set.
     */
    fun encryptionType() = this.preferenceStore.getEnum("encryption_type", EncryptionType.AES_256)

    /**
     * Password used for CBZ archives.
     *
     * @return The CBZ archive password, or an empty string if not set.
     */
    fun cbzPassword() = this.preferenceStore.getString(Preference.appStateKey("cbz_password"), "")

    // Category lock preferences
    // SECURITY NOTE: Category PINs are stored in SharedPreferences using the privateKey prefix
    // to exclude them from backups. The PIN values themselves are encrypted using Android KeyStore
    // (AES-256) via CategoryLockCrypto before storage. For enhanced security, consider migrating
    // to EncryptedSharedPreferences for at-rest encryption of the entire preference file.

    /**
     * Provides the stored set of category lock PINs.
     *
     * Values are persisted using a private preference key (excluded from backups) and are encrypted before storage.
     *
     * @return The set of category lock PINs.
     */
    fun categoryLockPins() = preferenceStore.getStringSet(
        Preference.privateKey("category_lock_pins"),
        emptySet(),
    )

    /**
     * Category lock timeout in seconds.
     * Semantics:
     * - 0 = immediate lock (default): categories are locked immediately when leaving them
     * - -1 = never timeout: categories remain unlocked until app process terminates
     * - >0 = custom duration: categories auto-lock after N seconds of inactivity
     */
    fun categoryLockTimeout() = preferenceStore.getInt("category_lock_timeout", 0)

    /**
     * Controls whether locked categories are displayed in category lists.
     *
     * @return `true` if locked categories are shown, `false` otherwise.
     */
    fun showLockedCategories() = preferenceStore.getBoolean("show_locked_categories", true)

    /**
     * Provides the stored failed-PIN attempt counts for category locks.
     *
     * Each entry is a string formatted as "categoryId:attemptCount".
     *
     * @return A set of entries in the format `"categoryId:attemptCount"`.
     */
    fun categoryLockFailedAttempts() = preferenceStore.getStringSet(
        Preference.appStateKey("category_lock_failed_attempts"),
        emptySet(),
    )

    /**
     * Master recovery PIN used for category locks, stored encrypted and excluded from backups.
     *
     * The value is encrypted via CategoryLockCrypto and protected with Android KeyStore; it is persisted under a private preference key to avoid inclusion in backups.
     *
     * @return The encrypted master PIN value, or an empty string if not set.
     *         Use [CategoryLockCrypto] to decrypt before comparison.
     */
    fun categoryLockMasterPin() = preferenceStore.getString(
        Preference.privateKey("category_lock_master_pin"),
        "",
    )
    // SY <--

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise this pref should be deleted.
     */
    fun lastAppClosed() = preferenceStore.getLong(
        Preference.appStateKey("last_app_closed"),
        0,
    )

    enum class SecureScreenMode(val titleRes: StringResource) {
        ALWAYS(MR.strings.lock_always),
        INCOGNITO(MR.strings.pref_incognito_mode),
        NEVER(MR.strings.lock_never),
    }

    // SY -->
    enum class EncryptionType(val titleRes: StringResource) {
        AES_256(SYMR.strings.aes_256),
        AES_128(SYMR.strings.aes_128),
        ZIP_STANDARD(SYMR.strings.standard_zip_encryption),
    }
    // SY <--
}
