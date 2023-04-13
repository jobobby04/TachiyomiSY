package eu.kanade.tachiyomi.util.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import eu.kanade.tachiyomi.core.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import tachiyomi.core.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

// SY -->
/**
 * object used to En/Decrypt and Base64 en/decode
 * CBZ file passwords before storing
 * them in Shared Preferences
 */
object CbzCrypto {
    private val securityPreferences: SecurityPreferences = Injekt.get()
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply {
        load(null)
    }

    private val encryptionCipher
        get() = Cipher.getInstance(CRYPTO_SETTINGS).apply {
            init(
                Cipher.ENCRYPT_MODE,
                getKey(),
            )
        }

    private fun getDecryptCipher(iv: ByteArray): Cipher {
        return Cipher.getInstance(CRYPTO_SETTINGS).apply {
            init(
                Cipher.DECRYPT_MODE,
                getKey(),
                IvParameterSpec(iv),
            )
        }
    }

    private fun getKey(): SecretKey {
        val loadedKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return loadedKey?.secretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(KEY_SIZE)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()
    }

    fun encrypt(password: String): String {
        val cipher = encryptionCipher
        val outputStream = ByteArrayOutputStream()
        outputStream.use { output ->
            output.write(cipher.iv)
            ByteArrayInputStream(password.toByteArray()).use { input ->
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

    private fun decrypt(encryptedPassword: String): String {
        val inputStream = Base64.decode(encryptedPassword, Base64.DEFAULT).inputStream()
        return inputStream.use { input ->
            val iv = ByteArray(IV_SIZE)
            input.read(iv)
            val cipher = getDecryptCipher(iv)
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (inputStream.available() > BUFFER_SIZE) {
                    inputStream.read(buffer)
                    output.write(cipher.update(buffer))
                }
                output.write(cipher.doFinal(inputStream.readBytes()))
                output.toString()
            }
        }
    }

    fun deleteKey() {
        keyStore.deleteEntry(ALIAS)
        generateKey()
    }

    fun getDecryptedPassword(): CharArray {
        return decrypt(securityPreferences.cbzPassword().get()).toCharArray()
    }

    /** Function that returns true when the supplied password
     * can Successfully decrypt the supplied zip archive */
    // not very elegant but this is the solution recommended by the maintainer for checking passwords
    // a real password check will likely be implemented in the future though
    fun checkCbzPassword(zip4j: ZipFile, password: CharArray, fileName: String? = null): Boolean {
        try {
            zip4j.setPassword(password)
            zip4j.use { zip ->
                zip.getInputStream(zip.fileHeaders.firstOrNull())
            }
            return true
        } catch (e: Exception) {
            when (fileName) {
                null -> logcat { "Wrong CBZ password" }
                else -> logcat { "Wrong CBZ password for $fileName" }
            }
        }
        return false
    }

    fun isPasswordSet(): Boolean {
        return securityPreferences.cbzPassword().get().isNotEmpty()
    }

    fun getPasswordProtectDlPref(): Boolean {
        return securityPreferences.passwordProtectDownloads().get()
    }

    fun createComicInfoPadding(): String? {
        return if (getPasswordProtectDlPref()) {
            val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            List((48..144).random()) { charPool.random() }.joinToString("")
        } else {
            null
        }
    }

    fun setZipParametersEncrypted(zipParameters: ZipParameters) {
        zipParameters.isEncryptFiles = true
        zipParameters.encryptionMethod = EncryptionMethod.AES
        zipParameters.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
    }

    fun deleteLocalCoverCache(context: Context) {
        if (context.getExternalFilesDir(LOCAL_CACHE_DIR)?.exists() == true) {
            context.getExternalFilesDir(LOCAL_CACHE_DIR)?.deleteRecursively()
        }
    }

    fun deleteLocalCoverSystemFiles(context: Context) {
        val baseFolderLocation = "${context.getString(R.string.app_name)}${File.separator}local"

        DiskUtil.getExternalStorages(context)
            .map { File(it.absolutePath, baseFolderLocation) }
            .asSequence()
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.isDirectory }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.name == ".cacheCoverInternal" || it.name == ".nocover" }
            .forEach { it.delete() }
    }
}

private const val BUFFER_SIZE = 8192
private const val KEY_SIZE = 256
private const val IV_SIZE = 16

private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
private const val CRYPTO_SETTINGS = "$ALGORITHM/$BLOCK_MODE/$PADDING"
private const val KEYSTORE = "AndroidKeyStore"
private const val ALIAS = "cbzPw"

private const val LOCAL_CACHE_DIR = "covers/local"

// SY <--
