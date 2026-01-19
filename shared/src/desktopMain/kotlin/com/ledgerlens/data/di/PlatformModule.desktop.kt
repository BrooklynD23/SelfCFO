package com.ledgerlens.data.di

import com.ledgerlens.data.DatabaseDriverFactory
import com.ledgerlens.db.LedgerLensDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop-specific Koin module providing database dependencies.
 *
 * This module provides:
 * - DatabaseDriverFactory for JVM/Desktop
 * - LedgerLensDatabase instance (encrypted or unencrypted)
 *
 * Usage:
 * ```
 * // In your Desktop application main
 * startKoin {
 *     modules(desktopPlatformModule, repositoryModule)
 * }
 * ```
 */
val desktopPlatformModule: Module = module {
    single { DatabaseDriverFactory() }
}

/**
 * Creates a Desktop platform module with an unencrypted database.
 * WARNING: Use only for development/testing.
 *
 * @return Koin module providing unencrypted database
 */
fun createDesktopDatabaseModule(): Module = module {
    single { DatabaseDriverFactory() }
    single {
        val driverFactory: DatabaseDriverFactory = get()
        LedgerLensDatabase(driverFactory.createDriver())
    }
}

/**
 * Creates a Desktop platform module with an encrypted database.
 *
 * @param encryptionKey 32-byte AES-256 encryption key
 * @return Koin module providing encrypted database
 */
fun createDesktopEncryptedDatabaseModule(encryptionKey: ByteArray): Module = module {
    single { DatabaseDriverFactory() }
    single {
        val driverFactory: DatabaseDriverFactory = get()
        LedgerLensDatabase(driverFactory.createEncryptedDriver(encryptionKey))
    }
}

/**
 * Gets all modules needed for Desktop with an unencrypted database.
 * WARNING: Use only for development/testing.
 *
 * @return List of Koin modules
 */
fun getDesktopModules(): List<Module> = listOf(
    createDesktopDatabaseModule(),
    repositoryModule
)

/**
 * Gets all modules needed for Desktop with an encrypted database.
 *
 * @param encryptionKey 32-byte AES-256 encryption key
 * @return List of Koin modules
 */
fun getDesktopEncryptedModules(encryptionKey: ByteArray): List<Module> = listOf(
    createDesktopEncryptedDatabaseModule(encryptionKey),
    repositoryModule
)
