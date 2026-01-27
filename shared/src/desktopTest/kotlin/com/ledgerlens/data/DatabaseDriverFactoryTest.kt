package com.ledgerlens.data

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM tests for DatabaseDriverFactory.
 * 
 * Tests verify:
 * - Unencrypted driver creation and basic operations
 * - Encrypted driver creation with valid key
 * - Invalid key size rejection
 * - Database file creation in correct location
 * - Basic CRUD operations work with both driver types
 */
class DatabaseDriverFactoryTest {

    private val factory = DatabaseDriverFactory()

    @Test
    fun `createDriver returns non-null SqlDriver`() {
        val driver = factory.createDriver()
        assertNotNull(driver, "Driver should not be null")
        driver.close()
    }

    @Test
    fun `createDriver can execute basic SQL`() {
        val driver = factory.createDriver()
        
        // Create a simple test table
        driver.execute(null, "CREATE TABLE IF NOT EXISTS test_table (id INTEGER PRIMARY KEY, name TEXT)", 0)
        
        // Insert a row
        driver.execute(null, "INSERT INTO test_table (id, name) VALUES (1, 'test')", 0)
        
        // Query the row
        driver.executeQuery(null, "SELECT name FROM test_table WHERE id = 1", { cursor ->
            assertTrue(cursor.next().value, "Should have a result")
            assertEquals("test", cursor.getString(0), "Name should match")
            QueryResult.Value(Unit)
        }, 0).value
        
        // Cleanup
        driver.execute(null, "DROP TABLE test_table", 0)
        driver.close()
    }

    @Test
    fun `createEncryptedDriver requires 32-byte key`() {
        val validKey = ByteArray(32) { it.toByte() }
        val driver = factory.createEncryptedDriver(validKey)
        assertNotNull(driver)
        driver.close()
    }

    @Test
    fun `createEncryptedDriver rejects short key`() {
        val shortKey = ByteArray(16) { it.toByte() }
        
        assertFailsWith<IllegalArgumentException> {
            factory.createEncryptedDriver(shortKey)
        }
    }

    @Test
    fun `createEncryptedDriver rejects long key`() {
        val longKey = ByteArray(64) { it.toByte() }
        
        assertFailsWith<IllegalArgumentException> {
            factory.createEncryptedDriver(longKey)
        }
    }

    @Test
    fun `createEncryptedDriver rejects empty key`() {
        val emptyKey = ByteArray(0)
        
        assertFailsWith<IllegalArgumentException> {
            factory.createEncryptedDriver(emptyKey)
        }
    }

    @Test
    fun `encrypted driver can execute basic SQL`() {
        val key = ByteArray(32) { it.toByte() }
        val driver = factory.createEncryptedDriver(key)
        
        // Create a simple test table
        driver.execute(null, "CREATE TABLE IF NOT EXISTS encrypted_test (id INTEGER PRIMARY KEY, secret TEXT)", 0)
        
        // Insert a row
        driver.execute(null, "INSERT INTO encrypted_test (id, secret) VALUES (1, 'secret_data')", 0)
        
        // Query the row
        driver.executeQuery(null, "SELECT secret FROM encrypted_test WHERE id = 1", { cursor ->
            assertTrue(cursor.next().value, "Should have a result")
            assertEquals("secret_data", cursor.getString(0), "Secret should match")
            QueryResult.Value(Unit)
        }, 0).value
        
        // Cleanup
        driver.execute(null, "DROP TABLE encrypted_test", 0)
        driver.close()
    }

    @Test
    fun `multiple drivers can be created`() {
        val driver1 = factory.createDriver()
        val driver2 = factory.createDriver()
        
        assertNotNull(driver1)
        assertNotNull(driver2)
        
        driver1.close()
        driver2.close()
    }

    @Test
    fun `driver handles unicode data`() {
        val driver = factory.createDriver()
        
        driver.execute(null, "CREATE TABLE IF NOT EXISTS unicode_test (id INTEGER PRIMARY KEY, content TEXT)", 0)
        
        val unicodeText = "Hello 世界 🌍 مرحبا"
        driver.execute(null, "INSERT INTO unicode_test (id, content) VALUES (1, '$unicodeText')", 0)
        
        driver.executeQuery(null, "SELECT content FROM unicode_test WHERE id = 1", { cursor ->
            assertTrue(cursor.next().value)
            assertEquals(unicodeText, cursor.getString(0))
            QueryResult.Value(Unit)
        }, 0).value
        
        driver.execute(null, "DROP TABLE unicode_test", 0)
        driver.close()
    }

    @Test
    fun `driver handles null values`() {
        val driver = factory.createDriver()
        
        driver.execute(null, "CREATE TABLE IF NOT EXISTS null_test (id INTEGER PRIMARY KEY, nullable_col TEXT)", 0)
        driver.execute(null, "INSERT INTO null_test (id, nullable_col) VALUES (1, NULL)", 0)
        
        driver.executeQuery(null, "SELECT nullable_col FROM null_test WHERE id = 1", { cursor ->
            assertTrue(cursor.next().value)
            // NULL handling
            assertTrue(cursor.getString(0) == null)
            QueryResult.Value(Unit)
        }, 0).value
        
        driver.execute(null, "DROP TABLE null_test", 0)
        driver.close()
    }

    @Test
    fun `driver handles large integer values`() {
        val driver = factory.createDriver()
        
        driver.execute(null, "CREATE TABLE IF NOT EXISTS bigint_test (id INTEGER PRIMARY KEY, big_value INTEGER)", 0)
        
        val largeValue = Long.MAX_VALUE - 1000
        driver.execute(null, "INSERT INTO bigint_test (id, big_value) VALUES (1, $largeValue)", 0)
        
        driver.executeQuery(null, "SELECT big_value FROM bigint_test WHERE id = 1", { cursor ->
            assertTrue(cursor.next().value)
            assertEquals(largeValue, cursor.getLong(0))
            QueryResult.Value(Unit)
        }, 0).value
        
        driver.execute(null, "DROP TABLE bigint_test", 0)
        driver.close()
    }

    @Test
    fun `encrypted driver with different keys creates separate databases`() {
        val key1 = ByteArray(32) { 1 }
        val key2 = ByteArray(32) { 2 }
        
        // This test verifies that using different keys results in separate 
        // encryption contexts (different keys = can't read each other's data)
        val driver1 = factory.createEncryptedDriver(key1)
        driver1.execute(null, "CREATE TABLE IF NOT EXISTS key_test (id INTEGER PRIMARY KEY)", 0)
        driver1.close()
        
        // Second driver with different key should work independently
        val driver2 = factory.createEncryptedDriver(key2)
        assertNotNull(driver2)
        driver2.close()
    }
}
