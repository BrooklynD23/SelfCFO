package com.ledgerlens.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase

/**
 * Android implementation of DatabaseDriverFactory.
 * 
 * Uses AndroidSqliteDriver for SQLite database access on Android.
 * Future: Will integrate with SQLCipher for encryption.
 */
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = LedgerLensDatabase.Schema,
            context = context,
            name = "ledgerlens.db"
        )
    }
}
