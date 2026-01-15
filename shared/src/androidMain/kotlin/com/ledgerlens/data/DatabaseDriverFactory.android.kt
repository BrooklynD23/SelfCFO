package com.ledgerlens.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Android implementation of DatabaseDriverFactory.
 * 
 * Uses AndroidSqliteDriver for SQLite database access on Android.
 * Supports SQLCipher encryption for production use.
 */
actual class DatabaseDriverFactory(private val context: Context) {
    
    companion object {
        private const val DB_NAME = "ledgerlens.db"
        private const val ENCRYPTED_DB_NAME = "ledgerlens_encrypted.db"
    }
    
    init {
        // Load SQLCipher native libraries
        System.loadLibrary("sqlcipher")
    }
    
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = LedgerLensDatabase.Schema,
            context = context,
            name = DB_NAME
        )
    }
    
    actual fun createEncryptedDriver(key: ByteArray): SqlDriver {
        require(key.size == 32) { "Database key must be 32 bytes for AES-256" }
        
        val passphrase = key.toHexString()
        val factory = SupportOpenHelperFactory(passphrase.toByteArray())
        
        return AndroidSqliteDriver(
            schema = LedgerLensDatabase.Schema,
            context = context,
            name = ENCRYPTED_DB_NAME,
            factory = factory
        )
    }
    
    private fun ByteArray.toHexString(): String = 
        joinToString("") { "%02x".format(it) }
}
