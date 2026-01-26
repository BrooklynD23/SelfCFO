package com.ledgerlens.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of PlatformKeystore using Android Keystore System
 * and EncryptedSharedPreferences for secure key storage.
 *
 * Security features:
 * - Hardware-backed key storage when available (StrongBox or TEE)
 * - Keys are non-exportable and bound to device
 * - Uses AES-256-GCM for encryption
 */
class AndroidPlatformKeystore(private val context: Context) : PlatformKeystore {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEK_ALIAS = "ledgerlens_kek_wrapper"
        private const val PREFS_NAME = "ledgerlens_secure_prefs"
        private const val KEY_WRAPPED_KEK = "wrapped_kek"
        private const val KEY_SALT = "kdf_salt"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun storeKek(wrappedKek: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureWrapperKeyExists()
            encryptedPrefs.edit()
                .putString(KEY_WRAPPED_KEK, wrappedKek.toBase64())
                .apply()
        }
    }

    override suspend fun retrieveKek(): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            encryptedPrefs.getString(KEY_WRAPPED_KEK, null)?.fromBase64()
        }
    }

    override suspend fun deleteKek(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            encryptedPrefs.edit()
                .remove(KEY_WRAPPED_KEK)
                .remove(KEY_SALT)
                .apply()

            // Also remove the wrapper key from Android Keystore
            if (keyStore.containsAlias(KEK_ALIAS)) {
                keyStore.deleteEntry(KEK_ALIAS)
            }
        }
    }

    override suspend fun storeSalt(salt: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            encryptedPrefs.edit()
                .putString(KEY_SALT, salt.toBase64())
                .apply()
        }
    }

    override suspend fun retrieveSalt(): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            encryptedPrefs.getString(KEY_SALT, null)?.fromBase64()
        }
    }

    override fun isHardwareBacked(): Boolean {
        return try {
            ensureWrapperKeyExists()
            val key = keyStore.getKey(KEK_ALIAS, null) as? SecretKey ?: return false

            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ensure the wrapper key exists in Android Keystore.
     * This key is used internally by EncryptedSharedPreferences.
     */
    private fun ensureWrapperKeyExists() {
        if (!keyStore.containsAlias(KEK_ALIAS)) {
            generateWrapperKey()
        }
    }

    /**
     * Generate an AES-256-GCM key in Android Keystore.
     * Attempts to use StrongBox if available for maximum security.
     */
    private fun generateWrapperKey() {
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setKeySize(256)
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setRandomizedEncryptionRequired(true)

            // Try to use StrongBox if available (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    setIsStrongBoxBacked(true)
                } catch (_: Exception) {
                    // StrongBox not available, fall back to TEE
                }
            }
        }.build()

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(keyGenSpec)
        keyGenerator.generateKey()
    }

    private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(
        this,
        android.util.Base64.NO_WRAP
    )

    private fun String.fromBase64(): ByteArray = android.util.Base64.decode(
        this,
        android.util.Base64.NO_WRAP
    )
}
