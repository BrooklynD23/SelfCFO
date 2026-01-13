package com.ledgerlens.data

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory for creating platform-specific SQLDelight database drivers.
 * 
 * Each platform (Android, Desktop) provides its own implementation
 * using the expect/actual pattern.
 */
expect class DatabaseDriverFactory {
    /**
     * Creates a SqlDriver for the LedgerLens database.
     * 
     * @return Platform-specific SqlDriver implementation
     */
    fun createDriver(): SqlDriver
}
