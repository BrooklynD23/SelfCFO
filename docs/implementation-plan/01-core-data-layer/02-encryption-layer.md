# 02: Encryption Layer

## Overview

Implement the encryption strategy per [ADR-003](../../PRDs/13-architecture-decision-records.md#adr-003-encryption-strategy) with SQLCipher for database encryption and envelope encryption for attachments.

---

## Key Hierarchy

```
┌─────────────────────────────────────────────────────────┐
│                    KEY HIERARCHY                         │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  USER PASSPHRASE / RECOVERY KEY                         │
│         │                                                │
│         ▼ (Argon2id derivation)                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │              MASTER KEY (MK)                     │    │
│  │  Never stored; derived on-demand                 │    │
│  └─────────────────────────────────────────────────┘    │
│         │                                                │
│         ▼ (wrap with platform keystore)                 │
│  ┌─────────────────────────────────────────────────┐    │
│  │         KEY ENCRYPTION KEY (KEK)                 │    │
│  │  Stored in: Android Keystore / OS Keychain      │    │
│  └─────────────────────────────────────────────────┘    │
│         │                                                │
│         ▼ (wraps DEKs)                                  │
│  ┌──────────────────┐  ┌──────────────────┐            │
│  │    DB DEK        │  │   FILE DEK       │            │
│  │  (SQLCipher)     │  │  (Per-file)      │            │
│  └──────────────────┘  └──────────────────┘            │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Implementation Steps

### Step 1: Define Key Management Interface

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/security/KeyManager.kt
package com.ledgerlens.security

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
     * Check if database is currently unlocked.
     */
    fun isUnlocked(): Boolean

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

data class RecoveryKey(
    val words: List<String>  // 24-word mnemonic
)

data class BackupBundle(
    val encryptedKek: ByteArray,
    val salt: ByteArray,
    val version: Int = 1
)
```

### Step 2: Implement Argon2 Key Derivation

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/security/KeyDerivation.kt
package com.ledgerlens.security

expect object KeyDerivation {
    /**
     * Derive key from passphrase using Argon2id.
     * @param passphrase User passphrase
     * @param salt Random salt (16 bytes)
     * @param outputLength Desired key length (32 for AES-256)
     * @return Derived key
     */
    fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        outputLength: Int = 32
    ): ByteArray
}

// Argon2 parameters per ADR-003
object Argon2Params {
    const val MEMORY_KB = 65536  // 64 MB
    const val ITERATIONS = 3
    const val PARALLELISM = 4
    const val SALT_LENGTH = 16
    const val KEY_LENGTH = 32
}
```

**Android Implementation:**
```kotlin
// shared/src/androidMain/kotlin/com/ledgerlens/security/KeyDerivation.kt
package com.ledgerlens.security

import org.signal.argon2.Argon2

actual object KeyDerivation {
    actual fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        outputLength: Int
    ): ByteArray {
        return Argon2.Builder(Argon2.Version.V13)
            .type(Argon2.Type.Argon2id)
            .memoryCostKiB(Argon2Params.MEMORY_KB)
            .parallelism(Argon2Params.PARALLELISM)
            .iterations(Argon2Params.ITERATIONS)
            .hashLength(outputLength)
            .build()
            .hash(passphrase.toByteArray(Charsets.UTF_8), salt)
            .hash
    }
}
```

### Step 3: Platform Keystore Integration

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/security/PlatformKeystore.kt
package com.ledgerlens.security

expect class PlatformKeystore {
    /**
     * Store wrapped KEK in platform secure storage.
     */
    suspend fun storeKek(wrappedKek: ByteArray): Result<Unit>

    /**
     * Retrieve wrapped KEK from platform secure storage.
     */
    suspend fun retrieveKek(): Result<ByteArray?>

    /**
     * Delete KEK (for crypto-erase).
     */
    suspend fun deleteKek(): Result<Unit>

    /**
     * Check if hardware-backed storage is available.
     */
    fun isHardwareBacked(): Boolean
}
```

**Android Implementation:**
```kotlin
// shared/src/androidMain/kotlin/com/ledgerlens/security/PlatformKeystore.kt
package com.ledgerlens.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

actual class PlatformKeystore(private val context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "ledgerlens_kek_wrapper"

    actual suspend fun storeKek(wrappedKek: ByteArray): Result<Unit> = runCatching {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)

        val encryptedData = cipher.doFinal(wrappedKek)
        val iv = cipher.iv

        // Store IV + encrypted data
        context.getSharedPreferences("ledgerlens_keys", Context.MODE_PRIVATE)
            .edit()
            .putString("kek_iv", iv.toBase64())
            .putString("kek_data", encryptedData.toBase64())
            .apply()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return if (keyStore.containsAlias(alias)) {
            keyStore.getKey(alias, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            keyGenerator.generateKey()
        }
    }

    actual fun isHardwareBacked(): Boolean {
        val keyInfo = keyStore.getKey(alias, null)?.let {
            val factory = javax.crypto.SecretKeyFactory.getInstance(
                it.algorithm, "AndroidKeyStore"
            )
            factory.getKeySpec(it, android.security.keystore.KeyInfo::class.java)
        }
        return keyInfo?.isInsideSecureHardware == true
    }
}
```

### Step 4: SQLCipher Integration

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/data/DatabaseFactory.kt
package com.ledgerlens.data

import com.ledgerlens.db.LedgerLensDatabase

expect class DatabaseFactory {
    fun createDatabase(key: ByteArray): LedgerLensDatabase
}
```

**Android Implementation:**
```kotlin
// shared/src/androidMain/kotlin/com/ledgerlens/data/DatabaseFactory.kt
package com.ledgerlens.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase
import net.sqlcipher.database.SupportFactory

actual class DatabaseFactory(private val context: Context) {
    actual fun createDatabase(key: ByteArray): LedgerLensDatabase {
        val factory = SupportFactory(key)
        val driver: SqlDriver = AndroidSqliteDriver(
            schema = LedgerLensDatabase.Schema,
            context = context,
            name = "ledgerlens.db",
            factory = factory
        )
        return LedgerLensDatabase(driver)
    }
}
```

### Step 5: File Encryption Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/security/FileEncryption.kt
package com.ledgerlens.security

interface FileEncryption {
    /**
     * Encrypt file data.
     * @param data Plaintext data
     * @param key AES-256 key
     * @return IV (12 bytes) + ciphertext + auth tag (16 bytes)
     */
    fun encrypt(data: ByteArray, key: ByteArray): ByteArray

    /**
     * Decrypt file data.
     * @param encryptedData IV + ciphertext + auth tag
     * @param key AES-256 key
     * @return Plaintext data
     */
    fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray
}

class AesGcmFileEncryption : FileEncryption {
    companion object {
        const val IV_LENGTH = 12
        const val TAG_LENGTH = 16
    }

    override fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = generateRandomBytes(IV_LENGTH)
        val cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv)
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext  // Tag is appended by GCM
    }

    override fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray {
        val iv = encryptedData.sliceArray(0 until IV_LENGTH)
        val ciphertext = encryptedData.sliceArray(IV_LENGTH until encryptedData.size)
        val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(ciphertext)
    }
}
```

### Step 6: Crypto-Erase Implementation

```kotlin
// In KeyManagerImpl
override suspend fun cryptoErase(): Result<Unit> = runCatching {
    // 1. Delete KEK from platform keystore
    platformKeystore.deleteKek()

    // 2. Delete encrypted database file
    databaseFile.delete()

    // 3. Delete encrypted attachments directory
    attachmentsDir.deleteRecursively()

    // 4. Clear any cached keys from memory
    cachedDek?.fill(0)
    cachedDek = null

    // Data is now unrecoverable
}
```

---

## Acceptance Criteria

- [ ] Key derivation with Argon2id works
- [ ] Platform keystore integration (Android Keystore)
- [ ] SQLCipher encrypts database
- [ ] File encryption with AES-GCM works
- [ ] Backup/restore preserves data
- [ ] Crypto-erase makes data unrecoverable
- [ ] Passphrase change works

---

## Security Requirements

- Minimum passphrase length: 12 characters
- Argon2id parameters: 64MB memory, 3 iterations, 4 parallelism
- AES-256-GCM for all encryption
- Random IV per encryption operation
- Secure key memory handling (zero on discard)

---

## Dependencies

- SQLCipher (Android: net.zetetic:android-database-sqlcipher)
- Argon2 (signal-argon2)
- Platform keystore APIs

---

## Estimated Complexity

**High** - Security-critical with platform-specific implementations.
