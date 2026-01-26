package com.ledgerlens.data.di

import android.content.Context
import com.ledgerlens.data.DatabaseDriverFactory
import com.ledgerlens.db.LedgerLensDatabase
import com.ledgerlens.security.AesGcmFileEncryption
import com.ledgerlens.security.AndroidPlatformKeystore
import com.ledgerlens.security.FileEncryption
import com.ledgerlens.security.PlatformKeystore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific Koin module providing database dependencies.
 *
 * This module provides:
 * - DatabaseDriverFactory with Android Context
 * - LedgerLensDatabase instance (encrypted or unencrypted)
 *
 * Usage:
 * ```
 * // In your Android Application class
 * startKoin {
 *     androidContext(this@MyApplication)
 *     modules(androidPlatformModule, repositoryModule)
 * }
 * ```
 */
val androidPlatformModule: Module = module {
    // DatabaseDriverFactory requires Android Context
    single { DatabaseDriverFactory(get()) }
}

/**
 * Creates an Android platform module with an unencrypted database.
 * WARNING: Use only for development/testing.
 *
 * @param context Android application context
 * @return Koin module providing unencrypted database
 */
fun createAndroidDatabaseModule(context: Context): Module = module {
    single { DatabaseDriverFactory(context) }
    single {
        val driverFactory: DatabaseDriverFactory = get()
        LedgerLensDatabase(driverFactory.createDriver())
    }

    // Security primitives (needed by KeyManagerImpl)
    single<PlatformKeystore> { AndroidPlatformKeystore(context) }
    single<FileEncryption> { AesGcmFileEncryption() }
}

/**
 * Creates an Android platform module with an encrypted database.
 *
 * @param context Android application context
 * @param encryptionKey 32-byte AES-256 encryption key
 * @return Koin module providing encrypted database
 */
fun createAndroidEncryptedDatabaseModule(context: Context, encryptionKey: ByteArray): Module = module {
    single { DatabaseDriverFactory(context) }
    single {
        val driverFactory: DatabaseDriverFactory = get()
        LedgerLensDatabase(driverFactory.createEncryptedDriver(encryptionKey))
    }
}

/**
 * Gets all modules needed for Android with an unencrypted database.
 * WARNING: Use only for development/testing.
 *
 * @param context Android application context
 * @return List of Koin modules
 */
fun getAndroidModules(context: Context): List<Module> = listOf(
    createAndroidDatabaseModule(context),
    repositoryModule
)

/**
 * Gets all modules needed for Android with an encrypted database.
 *
 * @param context Android application context
 * @param encryptionKey 32-byte AES-256 encryption key
 * @return List of Koin modules
 */
fun getAndroidEncryptedModules(context: Context, encryptionKey: ByteArray): List<Module> = listOf(
    createAndroidEncryptedDatabaseModule(context, encryptionKey),
    repositoryModule
)
