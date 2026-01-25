package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase

/**
 * Desktop (JVM) implementation of TestDatabaseHelper.
 * Uses JDBC SQLite driver for in-memory test databases.
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
