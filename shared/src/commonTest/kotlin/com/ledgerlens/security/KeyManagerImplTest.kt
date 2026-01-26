package com.ledgerlens.security

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for KeyManagerImpl.
 *
 * These tests use a mock FileEncryption implementation since the real
 * implementation requires platform-specific crypto APIs.
 */
class KeyManagerImplTest {

    private class MockFileEncryption : FileEncryption {
        // Store mapping: (keyHash, encryptedDataHash) -> originalData
        // This allows us to decrypt data encrypted with a specific key
        private val storage = mutableMapOf<Pair<String, String>, ByteArray>()

        private fun keyHash(key: ByteArray): String {
            return key.contentHashCode().toString()
        }

        private fun dataHash(data: ByteArray): String {
            return data.contentHashCode().toString()
        }

        override fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
            require(key.size == AesGcmConstants.KEY_LENGTH) {
                "Key must be ${AesGcmConstants.KEY_LENGTH} bytes"
            }

            // Generate IV and create encrypted representation
            val iv = ByteArray(AesGcmConstants.IV_LENGTH) { it.toByte() }
            // For mock, we'll use a simple transformation: XOR with key hash
            val encrypted = data.mapIndexed { i, byte ->
                (byte.toInt() xor (key[i % key.size].toInt())).toByte()
            }.toByteArray()

            // Store mapping: (keyHash, encryptedDataHash) -> originalData
            val keyH = keyHash(key)
            val encH = dataHash(encrypted)
            storage[Pair(keyH, encH)] = data.copyOf()

            // Return IV + encrypted data
            return iv + encrypted
        }

        override fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray {
            require(key.size == AesGcmConstants.KEY_LENGTH) {
                "Key must be ${AesGcmConstants.KEY_LENGTH} bytes"
            }
            require(encryptedData.size > AesGcmConstants.IV_LENGTH) {
                "Encrypted data too short"
            }

            // Extract IV and ciphertext
            val iv = encryptedData.sliceArray(0 until AesGcmConstants.IV_LENGTH)
            val ciphertext = encryptedData.sliceArray(AesGcmConstants.IV_LENGTH until encryptedData.size)

            // Look up original data
            val keyH = keyHash(key)
            val encH = dataHash(ciphertext)
            val original = storage[Pair(keyH, encH)] ?: throw AuthenticationException("Invalid key or data not found")

            // Verify by re-encrypting and checking
            val reEncrypted = original.mapIndexed { i, byte ->
                (byte.toInt() xor (key[i % key.size].toInt())).toByte()
            }.toByteArray()

            if (!reEncrypted.contentEquals(ciphertext)) {
                throw AuthenticationException("Data authentication failed - data may be tampered")
            }

            return original.copyOf()
        }
    }

    private class MockPlatformKeystore : PlatformKeystore {
        private var storedKek: ByteArray? = null
        private var storedSalt: ByteArray? = null

        override suspend fun storeKek(wrappedKek: ByteArray): Result<Unit> {
            storedKek = wrappedKek.copyOf()
            return Result.success(Unit)
        }

        override suspend fun retrieveKek(): Result<ByteArray?> {
            return Result.success(storedKek?.copyOf())
        }

        override suspend fun deleteKek(): Result<Unit> {
            storedKek = null
            storedSalt = null
            return Result.success(Unit)
        }

        override suspend fun storeSalt(salt: ByteArray): Result<Unit> {
            storedSalt = salt.copyOf()
            return Result.success(Unit)
        }

        override suspend fun retrieveSalt(): Result<ByteArray?> {
            return Result.success(storedSalt?.copyOf())
        }

        override fun isHardwareBacked(): Boolean = false
    }

    @Test
    fun `initializeKeys creates keys and returns recovery key`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        val result = keyManager.initializeKeys(passphrase)

        assertTrue(result.isSuccess)
        val recoveryKey = result.getOrNull()
        assertNotNull(recoveryKey)
        assertEquals(24, recoveryKey.words.size)
        assertTrue(keyManager.isUnlocked())
    }

    @Test
    fun `initializeKeys fails with short passphrase`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val result = keyManager.initializeKeys("short")

        assertTrue(result.isFailure)
        assertFalse(keyManager.isUnlocked())
    }

    @Test
    fun `unlock succeeds with correct passphrase`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()
        keyManager.lock()

        val result = keyManager.unlock(passphrase)

        assertTrue(result.isSuccess)
        assertTrue(keyManager.isUnlocked())
    }

    @Test
    fun `unlock fails with wrong passphrase`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()
        keyManager.lock()

        val result = keyManager.unlock("wrong_passphrase")

        assertTrue(result.isFailure)
        assertFalse(keyManager.isUnlocked())
    }

    @Test
    fun `getDatabaseKey returns consistent key when unlocked`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()

        val key1 = keyManager.getDatabaseKey().getOrThrow()
        val key2 = keyManager.getDatabaseKey().getOrThrow()

        assertEquals(32, key1.size) // AES-256 key length
        assertTrue(key1.contentEquals(key2)) // Should be consistent
    }

    @Test
    fun `getDatabaseKey fails when locked`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()
        keyManager.lock()

        val result = keyManager.getDatabaseKey()

        assertTrue(result.isFailure)
    }

    @Test
    fun `getFileKey returns different keys for different fileIds`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()

        val key1 = keyManager.getFileKey("file1").getOrThrow()
        val key2 = keyManager.getFileKey("file2").getOrThrow()

        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertFalse(key1.contentEquals(key2)) // Different files should have different keys
    }

    @Test
    fun `getFileKey returns same key for same fileId`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()

        val key1 = keyManager.getFileKey("file1").getOrThrow()
        val key2 = keyManager.getFileKey("file1").getOrThrow()

        assertTrue(key1.contentEquals(key2)) // Same file should have same key
    }

    @Test
    fun `encrypt decrypt roundtrip works`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()

        // Get a file key
        val fileKey = keyManager.getFileKey("test_file").getOrThrow()

        // Encrypt some data
        val plaintext = "Hello, World!".toByteArray()
        val encrypted = encryption.encrypt(plaintext, fileKey)

        // Decrypt it back
        val decrypted = encryption.decrypt(encrypted, fileKey)

        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun `wrong key fails to decrypt`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()

        // Get a file key
        val fileKey = keyManager.getFileKey("test_file").getOrThrow()
        val wrongKey = ByteArray(32) { it.toByte() }

        // Encrypt with correct key
        val plaintext = "Hello, World!".toByteArray()
        val encrypted = encryption.encrypt(plaintext, fileKey)

        // Try to decrypt with wrong key
        try {
            encryption.decrypt(encrypted, wrongKey)
            fail("Should have thrown AuthenticationException")
        } catch (e: AuthenticationException) {
            // Expected
        }
    }

    @Test
    fun `lock clears cached keys`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()

        // Get keys
        val dbKey = keyManager.getDatabaseKey().getOrThrow()
        val fileKey = keyManager.getFileKey("file1").getOrThrow()

        assertTrue(keyManager.isUnlocked())

        // Lock
        keyManager.lock()

        assertFalse(keyManager.isUnlocked())
        assertTrue(keyManager.getDatabaseKey().isFailure)
        assertTrue(keyManager.getFileKey("file1").isFailure)
    }

    @Test
    fun `isInitialized returns correct state`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        assertFalse(keyManager.isInitialized())

        keyManager.initializeKeys("test_passphrase_123").getOrThrow()

        assertTrue(keyManager.isInitialized())
    }

    @Test
    fun `cryptoErase deletes all keys`() = runTest {
        val keystore = MockPlatformKeystore()
        val encryption = MockFileEncryption()
        val keyManager = KeyManagerImpl(keystore, encryption)

        val passphrase = "test_passphrase_123"
        keyManager.initializeKeys(passphrase).getOrThrow()
        assertTrue(keyManager.isInitialized())

        val result = keyManager.cryptoErase()

        assertTrue(result.isSuccess)
        assertFalse(keyManager.isInitialized())
        assertFalse(keyManager.isUnlocked())
    }
}
