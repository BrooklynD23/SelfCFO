package com.ledgerlens.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

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
        // Load SQLCipher native libraries (android-database-sqlcipher)
        SQLiteDatabase.loadLibs(context)
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

        // android-database-sqlcipher expects a passphrase byte[]
        val factory = SupportFactory(key)

        return AndroidSqliteDriver(
            schema = LedgerLensDatabase.Schema,
            context = context,
            name = ENCRYPTED_DB_NAME,
            factory = factory
        )
    }

    // Note: passphrase-to-bytes strategy is handled by SupportFactory; keep key handling centralized.
}
