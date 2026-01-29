package com.ledgerlens.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for FileEncryption (AES-256-GCM) across all platforms.
 *
 * These tests verify:
 * - Encrypt/decrypt round-trip returns original data
 * - Different plaintexts produce different ciphertexts
 * - Same plaintext encrypted twice produces different ciphertexts (random IV)
 * - Invalid key sizes are rejected
 * - Tampered ciphertext is detected
 * - Edge cases: empty data, large data, binary data
 */
expect fun createFileEncryption(): FileEncryption

class FileEncryptionTest {

    private val encryption: FileEncryption = createFileEncryption()

    @Test
    fun `encrypt and decrypt round-trip works`() {
        val key = generateTestKey()
        val plaintext = "Hello, World!".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, key)
        val decrypted = encryption.decrypt(encrypted, key)

        assertTrue(plaintext.contentEquals(decrypted), "Decrypted data should match original")
    }

    @Test
    fun `encrypted data is longer than plaintext by IV plus tag`() {
        val key = generateTestKey()
        val plaintext = "Test message".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, key)

        val expectedMinLength = plaintext.size + AesGcmConstants.IV_LENGTH + AesGcmConstants.TAG_LENGTH
        assertTrue(
            encrypted.size >= expectedMinLength,
            "Encrypted should be at least plaintext + IV + tag"
        )
    }

    @Test
    fun `same plaintext encrypted twice produces different ciphertexts`() {
        val key = generateTestKey()
        val plaintext = "Identical message".encodeToByteArray()

        val encrypted1 = encryption.encrypt(plaintext, key)
        val encrypted2 = encryption.encrypt(plaintext, key)

        // Due to random IV, ciphertexts should differ
        assertTrue(
            !encrypted1.contentEquals(encrypted2),
            "Same plaintext should produce different ciphertexts (random IV)"
        )
    }

    @Test
    fun `different plaintexts produce different ciphertexts`() {
        val key = generateTestKey()
        val plaintext1 = "Message A".encodeToByteArray()
        val plaintext2 = "Message B".encodeToByteArray()

        val encrypted1 = encryption.encrypt(plaintext1, key)
        val encrypted2 = encryption.encrypt(plaintext2, key)

        assertTrue(!encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun `decrypt with wrong key fails`() {
        val key1 = generateTestKey(seed = 1)
        val key2 = generateTestKey(seed = 2)
        val plaintext = "Secret message".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, key1)

        assertFailsWith<Exception> {
            encryption.decrypt(encrypted, key2)
        }
    }

    @Test
    fun `tampered ciphertext is detected`() {
        val key = generateTestKey()
        val plaintext = "Important data".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, key)

        // Tamper with the ciphertext (modify a byte in the middle)
        val tampered = encrypted.copyOf()
        val middleIndex = AesGcmConstants.IV_LENGTH + (encrypted.size - AesGcmConstants.IV_LENGTH) / 2
        tampered[middleIndex] = (tampered[middleIndex].toInt() xor 0xFF).toByte()

        assertFailsWith<Exception> {
            encryption.decrypt(tampered, key)
        }
    }

    @Test
    fun `tampered IV is detected`() {
        val key = generateTestKey()
        val plaintext = "Important data".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, key)

        // Tamper with the IV (first bytes)
        val tampered = encrypted.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0xFF).toByte()

        assertFailsWith<Exception> {
            encryption.decrypt(tampered, key)
        }
    }

    @Test
    fun `tampered auth tag is detected`() {
        val key = generateTestKey()
        val plaintext = "Important data".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, key)

        // Tamper with the auth tag (last bytes)
        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        assertFailsWith<Exception> {
            encryption.decrypt(tampered, key)
        }
    }

    @Test
    fun `invalid key size throws exception on encrypt`() {
        val shortKey = ByteArray(16) { it.toByte() } // Too short (AES-128)
        val plaintext = "Test".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            encryption.encrypt(plaintext, shortKey)
        }
    }

    @Test
    fun `invalid key size throws exception on decrypt`() {
        val validKey = generateTestKey()
        val shortKey = ByteArray(16) { it.toByte() }
        val plaintext = "Test".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, validKey)

        assertFailsWith<IllegalArgumentException> {
            encryption.decrypt(encrypted, shortKey)
        }
    }

    @Test
    fun `empty plaintext can be encrypted and decrypted`() {
        val key = generateTestKey()
        val plaintext = ByteArray(0)

        val encrypted = encryption.encrypt(plaintext, key)
        val decrypted = encryption.decrypt(encrypted, key)

        assertEquals(0, decrypted.size, "Decrypted empty data should be empty")
    }

    @Test
    fun `single byte can be encrypted and decrypted`() {
        val key = generateTestKey()
        val plaintext = byteArrayOf(42)

        val encrypted = encryption.encrypt(plaintext, key)
        val decrypted = encryption.decrypt(encrypted, key)

        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun `large data can be encrypted and decrypted`() {
        val key = generateTestKey()
        // 1 MB of data
        val plaintext = ByteArray(1024 * 1024) { (it % 256).toByte() }

        val encrypted = encryption.encrypt(plaintext, key)
        val decrypted = encryption.decrypt(encrypted, key)

        assertTrue(plaintext.contentEquals(decrypted), "Large data round-trip should work")
    }

    @Test
    fun `binary data with all byte values round-trips correctly`() {
        val key = generateTestKey()
        // Array with all possible byte values
        val plaintext = ByteArray(256) { it.toByte() }

        val encrypted = encryption.encrypt(plaintext, key)
        val decrypted = encryption.decrypt(encrypted, key)

        assertTrue(plaintext.contentEquals(decrypted), "All byte values should round-trip")
    }

    @Test
    fun `null bytes in data are preserved`() {
        val key = generateTestKey()
        val plaintext = byteArrayOf(0, 1, 0, 2, 0, 3, 0, 0, 0)

        val encrypted = encryption.encrypt(plaintext, key)
        val decrypted = encryption.decrypt(encrypted, key)

        assertTrue(plaintext.contentEquals(decrypted), "Null bytes should be preserved")
    }

    @Test
    fun `data too short to be valid ciphertext throws exception`() {
        val key = generateTestKey()
        // Data shorter than IV + TAG
        val tooShort = ByteArray(10) { it.toByte() }

        assertFailsWith<IllegalArgumentException> {
            encryption.decrypt(tooShort, key)
        }
    }

    @Test
    fun `unicode text round-trips correctly`() {
        val key = generateTestKey()
        val plaintext = "Hello 世界 🌍 مرحبا".encodeToByteArray()

        val encrypted = encryption.encrypt(plaintext, key)
        val decrypted = encryption.decrypt(encrypted, key)

        assertEquals("Hello 世界 🌍 مرحبا", decrypted.decodeToString())
    }

    @Test
    fun `repeated encryption and decryption is stable`() {
        val key = generateTestKey()
        var data = "Original message".encodeToByteArray()

        // Encrypt and decrypt 10 times
        repeat(10) {
            data = encryption.encrypt(data, key)
            data = encryption.decrypt(data, key)
        }

        assertEquals("Original message", data.decodeToString())
    }

    private fun generateTestKey(seed: Int = 0): ByteArray {
        return ByteArray(AesGcmConstants.KEY_LENGTH) { (it + seed).toByte() }
    }
}
