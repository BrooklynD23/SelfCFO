package com.ledgerlens.data

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory for creating platform-specific SQLDelight database drivers.
 * 
 * Each platform (Android, Desktop) provides its own implementation
 * using the expect/actual pattern.
 * 
 * Supports both encrypted (SQLCipher) and unencrypted database modes.
 */
expect class DatabaseDriverFactory {
    /**
     * Creates an unencrypted SqlDriver for the LedgerLens database.
     * 
     * WARNING: Use only for development/testing. Production should use [createEncryptedDriver].
     * 
     * @return Platform-specific SqlDriver implementation
     */
    fun createDriver(): SqlDriver
    
    /**
     * Creates an encrypted SqlDriver using SQLCipher.
     * 
     * @param key Database encryption key (32 bytes for AES-256)
     * @return Encrypted SqlDriver implementation
     * @throws IllegalArgumentException if key is invalid
     */
    fun createEncryptedDriver(key: ByteArray): SqlDriver
}
