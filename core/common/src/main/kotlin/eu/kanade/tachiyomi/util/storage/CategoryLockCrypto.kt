package eu.kanade.tachiyomi.util.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

// SY -->
/**
 * Encryption utilities for category PIN storage.
 * PINs are encrypted using Android KeyStore and stored in SharedPreferences
 * as a StringSet in format "categoryId:encryptedPin"
 */
object CategoryLockCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS_CATEGORY_PIN = "categoryPin"
    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    private const val CRYPTO_SETTINGS = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 16
    private const val BUFFER_SIZE = 2048

    private val securityPreferences: SecurityPreferences by injectLazy()

    private val keyStore = KeyStore.getInstance(KEYSTORE).apply {
        load(null)
    }

    private val encryptionCipher: Cipher
        get() = Cipher.getInstance(CRYPTO_SETTINGS).apply {
            init(Cipher.ENCRYPT_MODE, getKey())
        }

    /**
     * Creates and initializes a Cipher configured for AES decryption using the provided IV.
     *
     * @param iv The initialization vector to use for decryption; expected to be 16 bytes.
     * @return A `Cipher` instance initialized in decrypt mode with the stored secret key and the given IV.
     */
    private fun getDecryptCipher(iv: ByteArray): Cipher {
        return Cipher.getInstance(CRYPTO_SETTINGS).apply {
            init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
        }
    }

    /**
     * Retrieves the SecretKey for the category PIN alias from the Android Keystore, generating and storing a new key if none exists.
     *
     * @return The `SecretKey` associated with the category PIN alias.
     */
    private fun getKey(): SecretKey {
        val loadedKey = keyStore.getEntry(ALIAS_CATEGORY_PIN, null) as? KeyStore.SecretKeyEntry
        return loadedKey?.secretKey ?: generateKey()
    }

    /**
     * Creates and returns a new AES SecretKey stored in the AndroidKeyStore under the configured alias.
     *
     * The generated key is configured for encryption and decryption with the module's algorithm, block
     * mode, padding, and key size, and is allowed to perform randomized encryption without requiring
     * user authentication.
     *
     * @return The newly generated `SecretKey` placed in the AndroidKeyStore.
     */
    private fun generateKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS_CATEGORY_PIN,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setKeySize(KEY_SIZE).setBlockModes(BLOCK_MODE).setEncryptionPaddings(PADDING)
                    .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build(),
            )
        }.generateKey()
    }

    /**
     * Produces a Base64-encoded AES-CBC ciphertext of the given PIN with the initialization vector (IV) prefixed.
     *
     * @param pin The plaintext PIN to encrypt.
     * @return The encrypted PIN as a Base64 string where the IV is stored at the beginning of the decoded bytes.
     */
    private fun encryptPin(pin: String): String {
        val cipher = encryptionCipher
        val outputStream = ByteArrayOutputStream()
        outputStream.use { output ->
            // Write IV first
            output.write(cipher.iv)
            ByteArrayInputStream(pin.toByteArray()).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (input.available() > BUFFER_SIZE) {
                    input.read(buffer)
                    output.write(cipher.update(buffer))
                }
                output.write(cipher.doFinal(input.readBytes()))
            }
        }
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    /**
     * Decrypts a stored PIN encoded as Base64 with the IV prepended.
     *
     * @param encryptedPin A Base64-encoded blob whose first 16 bytes are the IV and the remainder is the AES-CBC-PKCS7 ciphertext of the PIN.
     * @return The decrypted PIN as a plain string.
     */
    private fun decryptPin(encryptedPin: String): String {
        val inputStream = Base64.decode(encryptedPin, Base64.DEFAULT).inputStream()
        return inputStream.use { input ->
            val iv = ByteArray(IV_SIZE)
            input.read(iv)
            val cipher = getDecryptCipher(iv)
            val outputStream = ByteArrayOutputStream()
            outputStream.use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (input.available() > BUFFER_SIZE) {
                    input.read(buffer)
                    output.write(cipher.update(buffer))
                }
                output.write(cipher.doFinal(input.readBytes()))
                String(output.toByteArray())
            }
        }
    }

    /**
     * Returns the stored category lock entries parsed into a map.
     *
     * Parses preference entries formatted as "categoryId:encryptedPin" and maps each numeric
     * category ID to its encrypted PIN string.
     *
     * @return A mutable map from category ID to encrypted PIN. Invalid or non-numeric entries are ignored.
     */
    private fun getCategoryLockMap(): MutableMap<Long, String> {
        val lockPins = securityPreferences.categoryLockPins().get()
        val map = mutableMapOf<Long, String>()
        lockPins.forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val categoryId = parts[0].toLongOrNull()
                val encryptedPin = parts[1]
                if (categoryId != null) {
                    map[categoryId] = encryptedPin
                }
            }
        }
        return map
    }

    /**
     * Persist a map of category IDs to encrypted PINs into preferences.
     *
     * Each map entry is stored in the preferences-backed StringSet as "categoryId:encryptedPin",
     * replacing the previously saved set of category locks.
     *
     * @param map Mapping from category ID to its encrypted PIN string.
     */
    private fun saveCategoryLockMap(map: Map<Long, String>) {
        val stringSet = map.map { (categoryId, encryptedPin) ->
            "$categoryId:$encryptedPin"
        }.toSet()
        securityPreferences.categoryLockPins().set(stringSet)
    }

    /**
     * Stores an encrypted PIN for the specified category after validating its format.
     *
     * @param categoryId The ID of the category to associate the PIN with.
     * @param pin A numeric PIN consisting of 4 to 10 digits.
     * @throws IllegalArgumentException If `pin` is not 4–10 characters long or contains non-digit characters.
     */
    fun setPinForCategory(categoryId: Long, pin: String) {
        require(pin.length in 4..10) { "PIN must be 4-10 digits" }
        require(pin.all { it.isDigit() }) { "PIN must contain only digits" }

        val map = getCategoryLockMap()
        map[categoryId] = encryptPin(pin)
        saveCategoryLockMap(map)
    }

    /**
     * Removes the stored PIN for the specified category.
     *
     * @param categoryId The ID of the category whose PIN should be removed.
     */
    fun removePinForCategory(categoryId: Long) {
        val map = getCategoryLockMap()
        map.remove(categoryId)
        saveCategoryLockMap(map)
    }

    /**
     * Determine whether a category has a stored PIN lock.
     *
     * @param categoryId The ID of the category to check.
     * @return `true` if the category has a stored PIN lock, `false` otherwise.
     */
    fun hasLock(categoryId: Long): Boolean {
        return getCategoryLockMap().containsKey(categoryId)
    }

    /**
     * Check whether the provided PIN matches the stored PIN for the given category.
     *
     * @param categoryId The category identifier whose PIN is verified.
     * @param inputPin The PIN to verify.
     * @return `true` if the input PIN matches the stored PIN for the category, `false` otherwise.
     *         Returns `false` if no PIN is stored for the category or if decryption fails.
     */
    fun verifyPin(categoryId: Long, inputPin: String): Boolean {
        val map = getCategoryLockMap()
        val encryptedPin = map[categoryId] ?: return false

        return try {
            val decryptedPin = decryptPin(encryptedPin)
            decryptedPin == inputPin
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to verify PIN for category $categoryId" }
            false
        }
    }

    /**
     * Get all category IDs that currently have a PIN lock.
     *
     * @return A set of category IDs that have a stored encrypted PIN.
     */
    fun getLockedCategoryIds(): Set<Long> {
        return getCategoryLockMap().keys
    }

    /**
     * Provides a Flow that emits the set of category IDs with stored PIN locks whenever the underlying preference changes.
     *
     * @return A Flow emitting sets of locked category IDs.
     */
    fun getLockedCategoryIdsFlow(): Flow<Set<Long>> {
        return securityPreferences.categoryLockPins().changes()
            .map { getCategoryLockMap().keys }
    }

    /**
     * Deletes the AES key used for category PIN encryption and clears all stored PINs.
     *
     * Removes the keystore entry for the category PIN alias, regenerates a new key, and
     * clears all per-category and master PIN values from preferences so previously
     * encrypted values cannot be decrypted.
     */
    fun deleteKey() {
        keyStore.deleteEntry(ALIAS_CATEGORY_PIN)
        generateKey()
        // Clear all stored PINs since they can't be decrypted anymore
        securityPreferences.categoryLockPins().set(emptySet())
        securityPreferences.categoryLockMasterPin().set("")
    }

    /**
     * Stores a master recovery PIN that can unlock any locked category.
     *
     * The PIN must be 4 to 10 characters long and contain only digits.
     *
     * @param pin The master recovery PIN to store.
     * @throws IllegalArgumentException if `pin` is not 4–10 digits or contains non-digit characters.
     */
    fun setMasterPin(pin: String) {
        require(pin.length in 4..10) { "Master PIN must be 4-10 digits" }
        require(pin.all { it.isDigit() }) { "Master PIN must contain only digits" }

        val encryptedPin = encryptPin(pin)
        securityPreferences.categoryLockMasterPin().set(encryptedPin)
    }

    /**
     * Clears the stored encrypted master PIN from preferences.
     */
    fun removeMasterPin() {
        securityPreferences.categoryLockMasterPin().set("")
    }

    /**
     * Checks whether a master PIN is stored.
     *
     * @return `true` if a master PIN is set, `false` otherwise.
     */
    fun hasMasterPin(): Boolean {
        return securityPreferences.categoryLockMasterPin().get().isNotEmpty()
    }

    /**
     * Provides a Flow that emits whether a master PIN is stored whenever the underlying preference changes.
     *
     * @return A Flow emitting `true` if a master PIN is set, `false` otherwise.
     */
    fun hasMasterPinFlow(): Flow<Boolean> {
        return securityPreferences.categoryLockMasterPin().changes()
            .map { it.isNotEmpty() }
    }

    /**
     * Checks whether the provided PIN matches the stored master PIN.
     *
     * @returns `true` if the provided PIN matches the stored master PIN; `false` if no master PIN is set, the PIN does not match, or decryption fails.
     */
    fun verifyMasterPin(inputPin: String): Boolean {
        val encryptedMasterPin = securityPreferences.categoryLockMasterPin().get()
        if (encryptedMasterPin.isEmpty()) return false

        return try {
            val decryptedPin = decryptPin(encryptedMasterPin)
            decryptedPin == inputPin
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to verify master PIN" }
            false
        }
    }

    /**
     * Retrieves the number of failed PIN attempts recorded for the given category.
     *
     * @param categoryId The category identifier to look up.
     * @return The failed-attempt count for the category, or 0 if no record exists.
     */
    fun getFailedAttempts(categoryId: Long): Int {
        val attemptsSet = securityPreferences.categoryLockFailedAttempts().get()
        attemptsSet.forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val id = parts[0].toLongOrNull()
                val count = parts[1].toIntOrNull()
                if (id == categoryId && count != null) {
                    return count
                }
            }
        }
        return 0
    }

    /**
     * Increments the stored failed-attempts counter for the given category and persists the new value.
     *
     * @param categoryId The category identifier whose failed-attempts counter to increment.
     * @return The updated failed-attempts count for the category.
     */
    fun incrementFailedAttempts(categoryId: Long): Int {
        val attemptsMap = getFailedAttemptsMap().toMutableMap()
        val currentCount = attemptsMap[categoryId] ?: 0
        val newCount = currentCount + 1
        attemptsMap[categoryId] = newCount
        saveFailedAttemptsMap(attemptsMap)
        return newCount
    }

    /**
     * Resets the failed unlock attempt count for the specified category.
     *
     * @param categoryId The category id whose failed attempt count will be cleared.
     */
    fun resetFailedAttempts(categoryId: Long) {
        val attemptsMap = getFailedAttemptsMap().toMutableMap()
        attemptsMap.remove(categoryId)
        saveFailedAttemptsMap(attemptsMap)
    }

    /**
     * Clears all stored failed-attempt counters for category locks.
     */
    fun resetAllFailedAttempts() {
        securityPreferences.categoryLockFailedAttempts().set(emptySet())
    }

    /**
     * Parses the stored failed-attempt entries and returns a map of category IDs to failed attempt counts.
     *
     * @return A `Map<Long, Int>` where each key is a category ID and each value is the parsed failed-attempt count; entries that fail to parse are omitted.
     */
    private fun getFailedAttemptsMap(): Map<Long, Int> {
        val attemptsSet = securityPreferences.categoryLockFailedAttempts().get()
        val map = mutableMapOf<Long, Int>()
        attemptsSet.forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val categoryId = parts[0].toLongOrNull()
                val count = parts[1].toIntOrNull()
                if (categoryId != null && count != null) {
                    map[categoryId] = count
                }
            }
        }
        return map
    }

    /**
     * Persist per-category failed-attempt counts to preferences.
     *
     * @param map Mapping from category ID to its failed attempt count.
     */
    private fun saveFailedAttemptsMap(map: Map<Long, Int>) {
        val stringSet = map.map { (categoryId, count) ->
            "$categoryId:$count"
        }.toSet()
        securityPreferences.categoryLockFailedAttempts().set(stringSet)
    }
}
// SY <--
