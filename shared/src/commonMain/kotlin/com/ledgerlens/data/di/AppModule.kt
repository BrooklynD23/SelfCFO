package com.ledgerlens.data.di

import com.ledgerlens.data.repositories.AccountRepository
import com.ledgerlens.data.repositories.CategoryRepository
import com.ledgerlens.data.repositories.ImportRepository
import com.ledgerlens.data.repositories.ReceiptRepository
import com.ledgerlens.data.repositories.RuleRepository
import com.ledgerlens.data.repositories.StatisticsRepository
import com.ledgerlens.data.repositories.TransactionRepository
import com.ledgerlens.data.repositories.impl.SqlDelightAccountRepository
import com.ledgerlens.data.repositories.impl.SqlDelightCategoryRepository
import com.ledgerlens.data.repositories.impl.SqlDelightImportRepository
import com.ledgerlens.data.repositories.impl.SqlDelightReceiptRepository
import com.ledgerlens.data.repositories.impl.SqlDelightRuleRepository
import com.ledgerlens.data.repositories.impl.SqlDelightStatisticsRepository
import com.ledgerlens.data.repositories.impl.SqlDelightTransactionRepository
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module providing repository implementations.
 *
 * This module binds all SQLDelight repository implementations to their
 * corresponding interfaces, making them available for dependency injection
 * throughout the application.
 *
 * Usage:
 * ```
 * // In your application initialization
 * startKoin {
 *     modules(repositoryModule, platformModule())
 * }
 * ```
 */
val repositoryModule: Module = module {
    // Account Repository
    single<AccountRepository> {
        SqlDelightAccountRepository(
            database = get(),
            dispatcher = Dispatchers.Default
        )
    }

    // Category Repository
    single<CategoryRepository> {
        SqlDelightCategoryRepository(
            database = get(),
            dispatcher = Dispatchers.Default
        )
    }

    // Rule Repository
    single<RuleRepository> {
        SqlDelightRuleRepository(
            database = get(),
            dispatcher = Dispatchers.Default
        )
    }

    // Import Repository
    single<ImportRepository> {
        SqlDelightImportRepository(
            database = get(),
            dispatcher = Dispatchers.Default
        )
    }

    // Transaction Repository
    single<TransactionRepository> {
        SqlDelightTransactionRepository(
            database = get(),
            dispatcher = Dispatchers.Default
        )
    }

    // Statistics Repository
    single<StatisticsRepository> {
        SqlDelightStatisticsRepository(
            database = get(),
            dispatcher = Dispatchers.Default
        )
    }

    // Receipt Repository
    single<ReceiptRepository> {
        SqlDelightReceiptRepository(
            database = get(),
            dispatcher = Dispatchers.Default
        )
    }
}

/**
 * Combined app module including database and repositories.
 *
 * This module expects a LedgerLensDatabase to be provided by a platform-specific
 * module. Use [createAppModules] to get all required modules for a platform.
 */
val appModule: Module = module {
    includes(repositoryModule)
}

/**
 * Creates a database module with an unencrypted database.
 * WARNING: Use only for development/testing.
 *
 * @param driverFactory Platform-specific database driver factory
 * @return Koin module providing the database
 */
fun createDatabaseModule(database: LedgerLensDatabase): Module = module {
    single { database }
}

/**
 * Helper to get all app modules for a given database.
 *
 * @param database The LedgerLensDatabase instance
 * @return List of modules to load into Koin
 */
fun createAppModules(database: LedgerLensDatabase): List<Module> = listOf(
    createDatabaseModule(database),
    repositoryModule
)
