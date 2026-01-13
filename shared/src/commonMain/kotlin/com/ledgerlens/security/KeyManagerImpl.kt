package com.ledgerlens.security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementation of KeyManager per ADR-003 key hierarchy.
 * 
 * Key Hierarchy:
 * - User passphrase → Master Key (via Argon2id/PBKDF2)
 * - Master Key wraps KEK (stored in platform keystore)
 * - KEK wraps DEKs (database key, file keys)
 * 
 * @param keystore Platform-specific secure storage
 * @param keyWrapper Key wrapping operations (AES-GCM)
 * @param fileEncryption File encryption operations
 */
class KeyManagerImpl(
    private val keystore: PlatformKeystore,
    private val keyWrapper: KeyWrapperOperations,
    private val fileEncryption: FileEncryption
) : KeyManager {
    
    private val mutex = Mutex()
    
    @Volatile
    private var cachedKek: ByteArray? = null
    
    @Volatile
    private var cachedDbKey: ByteArray? = null
    
    override suspend fun initializeKeys(passphrase: String): Result<RecoveryKey> = mutex.withLock {
        runCatching {
            val validation = PassphraseRequirements.validate(passphrase)
            if (validation is PassphraseValidationResult.Invalid) {
                throw IllegalArgumentException(validation.errors.first())
            }
            
            if (isInitializedInternal()) {
                throw IllegalStateException("Keys already initialized")
            }
            
            val salt = generateSalt()
            val masterKey = KeyDerivation.deriveKey(passphrase, salt, Argon2Params.KEY_LENGTH)
            
            val kek = generateAesKey()
            val dbKey = generateAesKey()
            
            val wrappedKek = keyWrapper.wrap(kek, masterKey)
            val wrappedDbKey = keyWrapper.wrap(dbKey, kek)
            
            keystore.storeSalt(salt).getOrThrow()
            keystore.storeKek(wrappedKek).getOrThrow()
            storeWrappedDbKey(wrappedDbKey)
            
            cachedKek = kek.copyOf()
            cachedDbKey = dbKey.copyOf()
            
            masterKey.fill(0)
            
            val recoveryKey = MnemonicGenerator.generate()
            val recoveryMasterKey = MnemonicGenerator.deriveKeyFromMnemonic(recoveryKey, salt)
            val recoveryWrappedKek = keyWrapper.wrap(kek, recoveryMasterKey)
            storeRecoveryWrappedKek(recoveryWrappedKek)
            
            recoveryMasterKey.fill(0)
            kek.fill(0)
            dbKey.fill(0)
            
            recoveryKey
        }
    }
    
    override suspend fun unlock(passphrase: String): Result<Unit> = mutex.withLock {
        runCatching {
            if (cachedKek != null) {
                return@runCatching
            }
            
            val salt = keystore.retrieveSalt().getOrThrow()
                ?: throw IllegalStateException("No salt found - keys not initialized")
            
            val wrappedKek = keystore.retrieveKek().getOrThrow()
                ?: throw IllegalStateException("No KEK found - keys not initialized")
            
            val masterKey = KeyDerivation.deriveKey(passphrase, salt, Argon2Params.KEY_LENGTH)
            
            val kek = try {
                keyWrapper.unwrap(wrappedKek, masterKey)
            } catch (e: AuthenticationException) {
                throw InvalidPassphraseException("Invalid passphrase")
            } finally {
                masterKey.fill(0)
            }
            
            val wrappedDbKey = retrieveWrappedDbKey()
                ?: throw IllegalStateException("No database key found")
            
            val dbKey = keyWrapper.unwrap(wrappedDbKey, kek)
            
            cachedKek = kek
            cachedDbKey = dbKey
        }
    }
    
    override fun lock() {
        cachedKek?.fill(0)
        cachedDbKey?.fill(0)
        cachedKek = null
        cachedDbKey = null
    }
    
    override fun isUnlocked(): Boolean = cachedKek != null
    
    override suspend fun isInitialized(): Boolean = mutex.withLock {
        isInitializedInternal()
    }
    
    private suspend fun isInitializedInternal(): Boolean {
        val salt = keystore.retrieveSalt().getOrNull()
        val kek = keystore.retrieveKek().getOrNull()
        return salt != null && kek != null
    }
    
    override suspend fun getDatabaseKey(): Result<ByteArray> = mutex.withLock {
        runCatching {
            cachedDbKey?.copyOf()
                ?: throw IllegalStateException("Database is locked - call unlock() first")
        }
    }
    
    override suspend fun getFileKey(fileId: String): Result<ByteArray> = mutex.withLock {
        runCatching {
            val kek = cachedKek
                ?: throw IllegalStateException("Database is locked - call unlock() first")
            
            val existingWrappedKey = retrieveWrappedFileKey(fileId)
            if (existingWrappedKey != null) {
                return@runCatching keyWrapper.unwrap(existingWrappedKey, kek)
            }
            
            val fileKey = generateAesKey()
            val wrappedFileKey = keyWrapper.wrap(fileKey, kek)
            storeWrappedFileKey(fileId, wrappedFileKey)
            
            fileKey
        }
    }
    
    override suspend fun exportForBackup(exportPassphrase: String): Result<BackupBundle> = mutex.withLock {
        runCatching {
            val validation = PassphraseRequirements.validate(exportPassphrase)
            if (validation is PassphraseValidationResult.Invalid) {
                throw IllegalArgumentException(validation.errors.first())
            }
            
            val kek = cachedKek
                ?: throw IllegalStateException("Database is locked - call unlock() first")
            
            val exportSalt = generateSalt()
            val exportKey = KeyDerivation.deriveKey(exportPassphrase, exportSalt, Argon2Params.KEY_LENGTH)
            val encryptedKek = keyWrapper.wrap(kek, exportKey)
            
            exportKey.fill(0)
            
            BackupBundle(
                encryptedKek = encryptedKek,
                salt = exportSalt,
                version = 1
            )
        }
    }
    
    override suspend fun importFromBackup(
        backup: BackupBundle,
        exportPassphrase: String,
        newPassphrase: String
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val validation = PassphraseRequirements.validate(newPassphrase)
            if (validation is PassphraseValidationResult.Invalid) {
                throw IllegalArgumentException(validation.errors.first())
            }
            
            val exportKey = KeyDerivation.deriveKey(exportPassphrase, backup.salt, Argon2Params.KEY_LENGTH)
            
            val kek = try {
                keyWrapper.unwrap(backup.encryptedKek, exportKey)
            } catch (e: AuthenticationException) {
                throw InvalidPassphraseException("Invalid backup passphrase")
            } finally {
                exportKey.fill(0)
            }
            
            val newSalt = generateSalt()
            val newMasterKey = KeyDerivation.deriveKey(newPassphrase, newSalt, Argon2Params.KEY_LENGTH)
            val newWrappedKek = keyWrapper.wrap(kek, newMasterKey)
            
            newMasterKey.fill(0)
            
            keystore.storeSalt(newSalt).getOrThrow()
            keystore.storeKek(newWrappedKek).getOrThrow()
            
            cachedKek = kek
        }
    }
    
    override suspend fun cryptoErase(): Result<Unit> = mutex.withLock {
        runCatching {
            lock()
            
            keystore.deleteKek().getOrThrow()
            
            clearAllWrappedKeys()
        }
    }
    
    override suspend fun changePassphrase(
        currentPassphrase: String,
        newPassphrase: String
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val validation = PassphraseRequirements.validate(newPassphrase)
            if (validation is PassphraseValidationResult.Invalid) {
                throw IllegalArgumentException(validation.errors.first())
            }
            
            val salt = keystore.retrieveSalt().getOrThrow()
                ?: throw IllegalStateException("No salt found")
            
            val wrappedKek = keystore.retrieveKek().getOrThrow()
                ?: throw IllegalStateException("No KEK found")
            
            val currentMasterKey = KeyDerivation.deriveKey(currentPassphrase, salt, Argon2Params.KEY_LENGTH)
            
            val kek = try {
                keyWrapper.unwrap(wrappedKek, currentMasterKey)
            } catch (e: AuthenticationException) {
                throw InvalidPassphraseException("Invalid current passphrase")
            } finally {
                currentMasterKey.fill(0)
            }
            
            val newSalt = generateSalt()
            val newMasterKey = KeyDerivation.deriveKey(newPassphrase, newSalt, Argon2Params.KEY_LENGTH)
            val newWrappedKek = keyWrapper.wrap(kek, newMasterKey)
            
            newMasterKey.fill(0)
            kek.fill(0)
            
            keystore.storeSalt(newSalt).getOrThrow()
            keystore.storeKek(newWrappedKek).getOrThrow()
        }
    }
    
    private val wrappedDbKeyStorage = mutableMapOf<String, ByteArray>()
    private val wrappedFileKeyStorage = mutableMapOf<String, ByteArray>()
    private var recoveryWrappedKek: ByteArray? = null
    
    private fun storeWrappedDbKey(wrappedKey: ByteArray) {
        wrappedDbKeyStorage["db_key"] = wrappedKey
    }
    
    private fun retrieveWrappedDbKey(): ByteArray? {
        return wrappedDbKeyStorage["db_key"]
    }
    
    private fun storeWrappedFileKey(fileId: String, wrappedKey: ByteArray) {
        wrappedFileKeyStorage[fileId] = wrappedKey
    }
    
    private fun retrieveWrappedFileKey(fileId: String): ByteArray? {
        return wrappedFileKeyStorage[fileId]
    }
    
    private fun storeRecoveryWrappedKek(wrappedKek: ByteArray) {
        recoveryWrappedKek = wrappedKek
    }
    
    private fun clearAllWrappedKeys() {
        wrappedDbKeyStorage.values.forEach { it.fill(0) }
        wrappedDbKeyStorage.clear()
        wrappedFileKeyStorage.values.forEach { it.fill(0) }
        wrappedFileKeyStorage.clear()
        recoveryWrappedKek?.fill(0)
        recoveryWrappedKek = null
    }
}

/**
 * Interface for key wrapping operations.
 * Allows platform-specific implementations.
 */
interface KeyWrapperOperations {
    fun wrap(keyToWrap: ByteArray, wrappingKey: ByteArray): ByteArray
    fun unwrap(wrappedKey: ByteArray, wrappingKey: ByteArray): ByteArray
}

/**
 * Exception thrown when passphrase validation fails during unlock.
 */
class InvalidPassphraseException(message: String) : Exception(message)
