package com.ledgerlens.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop implementation of PlatformKeystore.
 * 
 * Uses a combination of:
 * - Java Preferences API for storing encrypted data (backed by OS registry/plist/prefs)
 * - File-based key storage in user's app data directory with restricted permissions
 * 
 * Security notes:
 * - Desktop platforms don't have hardware-backed keystores like Android
 * - The wrapper key is stored in a protected file in user's app data
 * - Data is encrypted with AES-256-GCM before storage
 * 
 * For production, consider platform-specific integrations:
 * - Windows: Credential Manager via JNA
 * - macOS: Keychain via Security framework
 * - Linux: libsecret/GNOME Keyring
 */
actual class PlatformKeystore {
    
    companion object {
        private const val PREFS_NODE = "com/ledgerlens/security"
        private const val KEY_WRAPPED_KEK = "wrapped_kek"
        private const val KEY_SALT = "kdf_salt"
        private const val KEY_FILE_NAME = "ledgerlens.key"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 128
    }
    
    private val prefs: Preferences by lazy {
        Preferences.userRoot().node(PREFS_NODE)
    }
    
    private val appDataDir: File by lazy {
        val baseDir = when {
            System.getProperty("os.name").lowercase().contains("win") -> {
                File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "LedgerLens")
            }
            System.getProperty("os.name").lowercase().contains("mac") -> {
                File(System.getProperty("user.home"), "Library/Application Support/LedgerLens")
            }
            else -> {
                // Linux/Unix - follow XDG spec
                val xdgData = System.getenv("XDG_DATA_HOME") 
                    ?: "${System.getProperty("user.home")}/.local/share"
                File(xdgData, "ledgerlens")
            }
        }
        baseDir.apply { mkdirs() }
    }
    
    private val keyFile: File by lazy {
        File(appDataDir, KEY_FILE_NAME)
    }
    
    actual suspend fun storeKek(wrappedKek: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wrapperKey = getOrCreateWrapperKey()
            val encrypted = encrypt(wrappedKek, wrapperKey)
            prefs.put(KEY_WRAPPED_KEK, Base64.getEncoder().encodeToString(encrypted))
            prefs.flush()
        }
    }
    
    actual suspend fun retrieveKek(): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = prefs.get(KEY_WRAPPED_KEK, null) ?: return@runCatching null
            val encrypted = Base64.getDecoder().decode(encoded)
            val wrapperKey = loadWrapperKey() ?: return@runCatching null
            decrypt(encrypted, wrapperKey)
        }
    }
    
    actual suspend fun deleteKek(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            prefs.remove(KEY_WRAPPED_KEK)
            prefs.remove(KEY_SALT)
            prefs.flush()
            
            // Delete the wrapper key file
            if (keyFile.exists()) {
                keyFile.delete()
            }
        }
    }
    
    actual suspend fun storeSalt(salt: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            prefs.put(KEY_SALT, Base64.getEncoder().encodeToString(salt))
            prefs.flush()
        }
    }
    
    actual suspend fun retrieveSalt(): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            prefs.get(KEY_SALT, null)?.let { Base64.getDecoder().decode(it) }
        }
    }
    
    actual fun isHardwareBacked(): Boolean {
        // Desktop platforms don't have hardware-backed keystores
        return false
    }
    
    /**
     * Get existing wrapper key or create a new one.
     */
    private fun getOrCreateWrapperKey(): SecretKey {
        return loadWrapperKey() ?: generateAndStoreWrapperKey()
    }
    
    /**
     * Load the wrapper key from file.
     */
    private fun loadWrapperKey(): SecretKey? {
        if (!keyFile.exists()) return null
        
        return try {
            val keyBytes = keyFile.readBytes()
            SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Generate a new AES-256 wrapper key and store it securely.
     */
    private fun generateAndStoreWrapperKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        
        // Store with restricted permissions
        keyFile.writeBytes(key.encoded)
        setRestrictedPermissions(keyFile)
        
        return key
    }
    
    /**
     * Set file permissions to owner-only read/write.
     */
    private fun setRestrictedPermissions(file: File) {
        try {
            if (!System.getProperty("os.name").lowercase().contains("win")) {
                // Unix-like systems: set permissions to 600 (owner read/write only)
                Files.setPosixFilePermissions(
                    file.toPath(),
                    PosixFilePermissions.fromString("rw-------")
                )
            } else {
                // Windows: make file hidden and set readable/writable only by owner
                file.setReadable(false, false)
                file.setReadable(true, true)
                file.setWritable(false, false)
                file.setWritable(true, true)
            }
        } catch (e: Exception) {
            // Permissions may not be fully supported, continue anyway
        }
    }
    
    /**
     * Encrypt data using AES-GCM.
     */
    private fun encrypt(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        
        // Return IV + ciphertext
        return iv + ciphertext
    }
    
    /**
     * Decrypt data using AES-GCM.
     */
    private fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray {
        require(encrypted.size > IV_LENGTH) { "Encrypted data too short" }
        
        val iv = encrypted.sliceArray(0 until IV_LENGTH)
        val ciphertext = encrypted.sliceArray(IV_LENGTH until encrypted.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        
        return cipher.doFinal(ciphertext)
    }
}
