package com.ledgerlens.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase
import java.io.File

/**
 * Desktop (JVM) implementation of DatabaseDriverFactory.
 * 
 * Uses JdbcSqliteDriver for SQLite database access on desktop platforms.
 * Database is stored in the user's app data directory.
 * Future: Will integrate with SQLCipher for encryption.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val databasePath = getDatabasePath()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")
        
        // Create schema if database is new
        if (!File(databasePath).exists()) {
            LedgerLensDatabase.Schema.create(driver)
        }
        
        return driver
    }
    
    private fun getDatabasePath(): String {
        val appDataDir = getAppDataDirectory()
        val dbDir = File(appDataDir, "LedgerLens")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        return File(dbDir, "ledgerlens.db").absolutePath
    }
    
    private fun getAppDataDirectory(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> System.getenv("APPDATA") ?: System.getProperty("user.home")
            os.contains("mac") -> "${System.getProperty("user.home")}/Library/Application Support"
            else -> "${System.getProperty("user.home")}/.local/share"
        }
    }
}
