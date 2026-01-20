package com.ledgerlens.security

/**
 * Stub implementation of KeyManager for development/testing.
 *
 * This is a placeholder until the full encryption layer is implemented.
 * All operations return success but don't perform actual encryption.
 *
 * WARNING: Do not use in production - no actual encryption is performed.
 */
class StubKeyManager : KeyManager {
    private var initialized = false
    private var unlocked = false

    override suspend fun initializeKeys(passphrase: String): Result<RecoveryKey> {
        initialized = true
        unlocked = true
        // Generate a fake recovery key
        val fakeWords = listOf(
            "abandon", "ability", "able", "about", "above", "absent",
            "absorb", "abstract", "absurd", "abuse", "access", "accident",
            "account", "accuse", "achieve", "acid", "acoustic", "acquire",
            "across", "act", "action", "actor", "actress", "actual"
        )
        return Result.success(RecoveryKey(fakeWords))
    }

    override suspend fun unlock(passphrase: String): Result<Unit> {
        if (!initialized) {
            return Result.failure(IllegalStateException("Keys not initialized"))
        }
        unlocked = true
        return Result.success(Unit)
    }

    override fun lock() {
        unlocked = false
    }

    override fun isUnlocked(): Boolean = unlocked

    override suspend fun isInitialized(): Boolean = initialized

    override suspend fun getDatabaseKey(): Result<ByteArray> {
        if (!unlocked) {
            return Result.failure(IllegalStateException("Database is locked"))
        }
        // Return a dummy key (32 bytes for AES-256)
        return Result.success(ByteArray(32) { it.toByte() })
    }

    override suspend fun getFileKey(fileId: String): Result<ByteArray> {
        if (!unlocked) {
            return Result.failure(IllegalStateException("Database is locked"))
        }
        // Return a dummy key derived from fileId
        return Result.success(ByteArray(32) { (it + fileId.hashCode()).toByte() })
    }

    override suspend fun exportForBackup(exportPassphrase: String): Result<BackupBundle> {
        if (!unlocked) {
            return Result.failure(IllegalStateException("Database is locked"))
        }
        // Return a dummy backup bundle
        return Result.success(
            BackupBundle(
                encryptedKek = ByteArray(32) { it.toByte() },
                salt = ByteArray(16) { it.toByte() }
            )
        )
    }

    override suspend fun importFromBackup(
        backup: BackupBundle,
        exportPassphrase: String,
        newPassphrase: String
    ): Result<Unit> {
        initialized = true
        unlocked = true
        return Result.success(Unit)
    }

    override suspend fun cryptoErase(): Result<Unit> {
        initialized = false
        unlocked = false
        return Result.success(Unit)
    }

    override suspend fun changePassphrase(
        currentPassphrase: String,
        newPassphrase: String
    ): Result<Unit> {
        if (!unlocked) {
            return Result.failure(IllegalStateException("Database is locked"))
        }
        // Passphrase change is a no-op in stub
        return Result.success(Unit)
    }
}
