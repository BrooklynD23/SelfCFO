package com.ledgerlens.security

/**
 * Key management interface for the encryption layer.
 * 
 * Implements the key hierarchy per ADR-003:
 * - User passphrase → Master Key (via Argon2id)
 * - Master Key wraps KEK (stored in platform keystore)
 * - KEK wraps DEKs (database key, file keys)
 */
interface KeyManager {
    /**
     * Initialize keys for first-time setup.
     * @param passphrase User's passphrase (min 12 chars)
     * @return Recovery key for backup (show once to user)
     */
    suspend fun initializeKeys(passphrase: String): Result<RecoveryKey>

    /**
     * Unlock the database with passphrase.
     */
    suspend fun unlock(passphrase: String): Result<Unit>

    /**
     * Lock the database and clear cached keys.
     */
    fun lock()

    /**
     * Check if database is currently unlocked.
     */
    fun isUnlocked(): Boolean

    /**
     * Check if keys have been initialized (first-time setup complete).
     */
    suspend fun isInitialized(): Boolean

    /**
     * Get the database encryption key (must be unlocked).
     */
    suspend fun getDatabaseKey(): Result<ByteArray>

    /**
     * Get encryption key for a specific file.
     * @param fileId Unique file identifier
     * @return AES-256 key for this file
     */
    suspend fun getFileKey(fileId: String): Result<ByteArray>

    /**
     * Export keys for backup (wrapped with export passphrase).
     */
    suspend fun exportForBackup(exportPassphrase: String): Result<BackupBundle>

    /**
     * Import keys from backup.
     */
    suspend fun importFromBackup(
        backup: BackupBundle,
        exportPassphrase: String,
        newPassphrase: String
    ): Result<Unit>

    /**
     * Crypto-erase all data (delete KEK, all data becomes unrecoverable).
     */
    suspend fun cryptoErase(): Result<Unit>

    /**
     * Change passphrase (re-wrap KEK).
     */
    suspend fun changePassphrase(
        currentPassphrase: String,
        newPassphrase: String
    ): Result<Unit>
}

/**
 * Recovery key represented as a 24-word mnemonic.
 */
data class RecoveryKey(
    val words: List<String>
) {
    override fun toString(): String = words.joinToString(" ")
    
    companion object {
        fun fromString(mnemonic: String): RecoveryKey {
            return RecoveryKey(mnemonic.split(" ").filter { it.isNotBlank() })
        }
    }
}

/**
 * Bundle for backup/restore of encryption keys.
 */
data class BackupBundle(
    val encryptedKek: ByteArray,
    val salt: ByteArray,
    val version: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BackupBundle
        return encryptedKek.contentEquals(other.encryptedKek) &&
               salt.contentEquals(other.salt) &&
               version == other.version
    }

    override fun hashCode(): Int {
        var result = encryptedKek.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + version
        return result
    }
}

/**
 * Passphrase validation requirements.
 */
object PassphraseRequirements {
    const val MIN_LENGTH = 12
    
    fun validate(passphrase: String): PassphraseValidationResult {
        val errors = mutableListOf<String>()
        
        if (passphrase.length < MIN_LENGTH) {
            errors.add("Passphrase must be at least $MIN_LENGTH characters")
        }
        
        return if (errors.isEmpty()) {
            PassphraseValidationResult.Valid
        } else {
            PassphraseValidationResult.Invalid(errors)
        }
    }
}

sealed class PassphraseValidationResult {
    data object Valid : PassphraseValidationResult()
    data class Invalid(val errors: List<String>) : PassphraseValidationResult()
}
