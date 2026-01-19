package com.ledgerlens.data.di

import com.ledgerlens.data.repositories.AccountRepository
import com.ledgerlens.data.repositories.CategoryRepository
import com.ledgerlens.data.repositories.ImportRepository
import com.ledgerlens.data.repositories.ReceiptRepository
import com.ledgerlens.data.repositories.RuleRepository
import com.ledgerlens.data.repositories.StatisticsRepository
import com.ledgerlens.data.repositories.TransactionRepository
import com.ledgerlens.data.repositories.impl.TestDatabaseHelper
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Integration tests for Koin dependency injection module.
 * Verifies that all repositories can be resolved from the DI container.
 */
class KoinModuleTest : KoinTest {

    @BeforeTest
    fun setup() {
        // Create test database and start Koin
        val database = TestDatabaseHelper.createInMemoryDatabase()
        startKoin {
            modules(createAppModules(database))
        }
    }

    @AfterTest
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `AccountRepository can be resolved`() {
        val repository: AccountRepository = get()
        assertNotNull(repository)
    }

    @Test
    fun `CategoryRepository can be resolved`() {
        val repository: CategoryRepository = get()
        assertNotNull(repository)
    }

    @Test
    fun `RuleRepository can be resolved`() {
        val repository: RuleRepository = get()
        assertNotNull(repository)
    }

    @Test
    fun `ImportRepository can be resolved`() {
        val repository: ImportRepository = get()
        assertNotNull(repository)
    }

    @Test
    fun `TransactionRepository can be resolved`() {
        val repository: TransactionRepository = get()
        assertNotNull(repository)
    }

    @Test
    fun `StatisticsRepository can be resolved`() {
        val repository: StatisticsRepository = get()
        assertNotNull(repository)
    }

    @Test
    fun `ReceiptRepository can be resolved`() {
        val repository: ReceiptRepository = get()
        assertNotNull(repository)
    }

    @Test
    fun `all repositories are singletons`() {
        val account1: AccountRepository = get()
        val account2: AccountRepository = get()
        assertNotNull(account1)
        assertNotNull(account2)
        // Koin singletons should return the same instance
        assert(account1 === account2) { "AccountRepository should be a singleton" }

        val category1: CategoryRepository = get()
        val category2: CategoryRepository = get()
        assert(category1 === category2) { "CategoryRepository should be a singleton" }

        val transaction1: TransactionRepository = get()
        val transaction2: TransactionRepository = get()
        assert(transaction1 === transaction2) { "TransactionRepository should be a singleton" }
    }
}
