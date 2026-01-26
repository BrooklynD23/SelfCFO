package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase

/**
 * Android unit test implementation of TestDatabaseHelper.
 * Uses JDBC SQLite driver for in-memory test databases.
 *
 * Note: Android unit tests run on the host JVM (not on device),
 * so we use the same JDBC driver as desktop tests.
 */
actual object TestDatabaseHelper {
    /**
     * Creates an in-memory LedgerLensDatabase for testing.
     */
    actual fun createInMemoryDatabase(): LedgerLensDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerLensDatabase.Schema.create(driver)
        return LedgerLensDatabase(driver)
    }
}
