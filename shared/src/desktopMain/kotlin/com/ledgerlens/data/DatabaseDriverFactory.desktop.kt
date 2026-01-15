package com.ledgerlens.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase
import java.io.File
import java.util.Properties

/**
 * Desktop (JVM) implementation of DatabaseDriverFactory.
 * 
 * Uses JdbcSqliteDriver for SQLite database access on desktop platforms.
 * Database is stored in the user's app data directory.
 * Supports SQLCipher encryption for production use.
 * 
 * Note: SQLCipher for JDBC requires the sqlcipher4 JDBC driver:
 * - Uses PRAGMA key for encryption
 * - Compatible with SQLCipher 4.x encryption format
 */
actual class DatabaseDriverFactory {
    
    companion object {
        private const val DB_NAME = "ledgerlens.db"
        private const val ENCRYPTED_DB_NAME = "ledgerlens_encrypted.db"
    }
    
    actual fun createDriver(): SqlDriver {
        val databasePath = getDatabasePath(DB_NAME)
        val isNewDb = !File(databasePath).exists()
        
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")
        
        if (isNewDb) {
            LedgerLensDatabase.Schema.create(driver)
        }
        
        return driver
    }
    
    actual fun createEncryptedDriver(key: ByteArray): SqlDriver {
        require(key.size == 32) { "Database key must be 32 bytes for AES-256" }
        
        val databasePath = getDatabasePath(ENCRYPTED_DB_NAME)
        val isNewDb = !File(databasePath).exists()
        
        // Convert key to hex string for SQLCipher PRAGMA
        val hexKey = key.toHexString()
        
        // Create driver with encryption key in connection properties
        val properties = Properties().apply {
            put("cipher", "sqlcipher")
            put("legacy", "4") // SQLCipher 4.x format
        }
        
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$databasePath",
            properties = properties
        )
        
        // Set encryption key using PRAGMA
        driver.execute(null, "PRAGMA key = \"x'$hexKey'\";", 0)
        
        // SQLCipher settings for compatibility and performance
        driver.execute(null, "PRAGMA cipher_page_size = 4096;", 0)
        driver.execute(null, "PRAGMA kdf_iter = 256000;", 0)
        driver.execute(null, "PRAGMA cipher_hmac_algorithm = HMAC_SHA256;", 0)
        driver.execute(null, "PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA256;", 0)
        
        if (isNewDb) {
            LedgerLensDatabase.Schema.create(driver)
        }
        
        return driver
    }
    
    private fun getDatabasePath(dbName: String): String {
        val appDataDir = getAppDataDirectory()
        val dbDir = File(appDataDir, "LedgerLens")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        return File(dbDir, dbName).absolutePath
    }
    
    private fun getAppDataDirectory(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> System.getenv("APPDATA") ?: System.getProperty("user.home")
            os.contains("mac") -> "${System.getProperty("user.home")}/Library/Application Support"
            else -> "${System.getProperty("user.home")}/.local/share"
        }
    }
    
    private fun ByteArray.toHexString(): String = 
        joinToString("") { "%02x".format(it) }
}
