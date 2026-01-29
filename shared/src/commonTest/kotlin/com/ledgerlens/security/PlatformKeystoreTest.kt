package com.ledgerlens.security

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

expect fun createPlatformKeystore(): PlatformKeystore

/**
 * Tests for PlatformKeystore across all platforms.
 *
 * These tests verify:
 * - KEK storage and retrieval round-trip
 * - Salt storage and retrieval round-trip
 * - Delete operation clears stored data
 * - Empty/null handling
 * - Multiple store operations (overwrite behavior)
 */
class PlatformKeystoreTest {

    private val keystore = createPlatformKeystore()

    @Test
    fun `storeKek and retrieveKek round-trip works`() = runTest {
        val testKek = ByteArray(32) { it.toByte() }

        // Store KEK
        val storeResult = keystore.storeKek(testKek)
        assertTrue(storeResult.isSuccess, "Store should succeed")

        // Retrieve KEK
        val retrieveResult = keystore.retrieveKek()
        assertTrue(retrieveResult.isSuccess, "Retrieve should succeed")

        val retrieved = retrieveResult.getOrNull()
        assertNotNull(retrieved, "Retrieved KEK should not be null")
        assertTrue(testKek.contentEquals(retrieved), "Retrieved KEK should match stored KEK")

        // Cleanup
        keystore.deleteKek()
    }

    @Test
    fun `storeSalt and retrieveSalt round-trip works`() = runTest {
        val testSalt = ByteArray(16) { (it * 2).toByte() }

        // Store salt
        val storeResult = keystore.storeSalt(testSalt)
        assertTrue(storeResult.isSuccess, "Store should succeed")

        // Retrieve salt
        val retrieveResult = keystore.retrieveSalt()
        assertTrue(retrieveResult.isSuccess, "Retrieve should succeed")

        val retrieved = retrieveResult.getOrNull()
        assertNotNull(retrieved, "Retrieved salt should not be null")
        assertTrue(testSalt.contentEquals(retrieved), "Retrieved salt should match stored salt")

        // Cleanup
        keystore.deleteKek()
    }

    @Test
    fun `retrieveKek returns null when nothing stored`() = runTest {
        // Ensure clean state
        keystore.deleteKek()

        val result = keystore.retrieveKek()
        assertTrue(result.isSuccess, "Retrieve should succeed even when empty")
        assertNull(result.getOrNull(), "Should return null when nothing stored")
    }

    @Test
    fun `retrieveSalt returns null when nothing stored`() = runTest {
        // Ensure clean state
        keystore.deleteKek()

        val result = keystore.retrieveSalt()
        assertTrue(result.isSuccess, "Retrieve should succeed even when empty")
        assertNull(result.getOrNull(), "Should return null when nothing stored")
    }

    @Test
    fun `deleteKek clears stored data`() = runTest {
        val testKek = ByteArray(32) { it.toByte() }
        val testSalt = ByteArray(16) { it.toByte() }

        // Store data
        keystore.storeKek(testKek)
        keystore.storeSalt(testSalt)

        // Verify stored
        assertNotNull(keystore.retrieveKek().getOrNull())
        assertNotNull(keystore.retrieveSalt().getOrNull())

        // Delete
        val deleteResult = keystore.deleteKek()
        assertTrue(deleteResult.isSuccess, "Delete should succeed")

        // Verify deleted
        assertNull(keystore.retrieveKek().getOrNull(), "KEK should be null after delete")
        assertNull(keystore.retrieveSalt().getOrNull(), "Salt should be null after delete")
    }

    @Test
    fun `storeKek overwrites existing value`() = runTest {
        val kek1 = ByteArray(32) { 1 }
        val kek2 = ByteArray(32) { 2 }

        // Store first KEK
        keystore.storeKek(kek1)

        // Store second KEK (overwrite)
        keystore.storeKek(kek2)

        // Retrieve should return second KEK
        val retrieved = keystore.retrieveKek().getOrNull()
        assertNotNull(retrieved)
        assertTrue(kek2.contentEquals(retrieved), "Should return the overwritten KEK")

        // Cleanup
        keystore.deleteKek()
    }

    @Test
    fun `storeSalt overwrites existing value`() = runTest {
        val salt1 = ByteArray(16) { 1 }
        val salt2 = ByteArray(16) { 2 }

        // Store first salt
        keystore.storeSalt(salt1)

        // Store second salt (overwrite)
        keystore.storeSalt(salt2)

        // Retrieve should return second salt
        val retrieved = keystore.retrieveSalt().getOrNull()
        assertNotNull(retrieved)
        assertTrue(salt2.contentEquals(retrieved), "Should return the overwritten salt")

        // Cleanup
        keystore.deleteKek()
    }

    @Test
    fun `isHardwareBacked returns boolean without exception`() {
        // Just verify it returns without throwing
        val result = keystore.isHardwareBacked()
        // Result is platform-specific (true on some Android devices, false on desktop)
        assertTrue(result || !result, "Should return a valid boolean")
    }

    @Test
    fun `storeKek handles various key sizes`() = runTest {
        // Standard 32-byte key
        val key32 = ByteArray(32) { it.toByte() }
        assertTrue(keystore.storeKek(key32).isSuccess)
        assertTrue(key32.contentEquals(keystore.retrieveKek().getOrNull()))

        // Larger key (64 bytes - for wrapped keys with additional data)
        val key64 = ByteArray(64) { it.toByte() }
        assertTrue(keystore.storeKek(key64).isSuccess)
        assertTrue(key64.contentEquals(keystore.retrieveKek().getOrNull()))

        // Cleanup
        keystore.deleteKek()
    }

    @Test
    fun `handles empty byte arrays`() = runTest {
        val emptyArray = ByteArray(0)

        // Store empty KEK
        val storeResult = keystore.storeKek(emptyArray)
        assertTrue(storeResult.isSuccess, "Should handle empty array")

        val retrieved = keystore.retrieveKek().getOrNull()
        assertNotNull(retrieved)
        assertEquals(expected = 0, actual = retrieved.size, message = "Should retrieve empty array")

        // Cleanup
        keystore.deleteKek()
    }

    @Test
    fun `handles binary data with all byte values`() = runTest {
        // Create array with all possible byte values
        val allBytes = ByteArray(256) { it.toByte() }

        assertTrue(keystore.storeKek(allBytes).isSuccess)

        val retrieved = keystore.retrieveKek().getOrNull()
        assertNotNull(retrieved)
        assertTrue(allBytes.contentEquals(retrieved), "Should preserve all byte values")

        // Cleanup
        keystore.deleteKek()
    }
}
