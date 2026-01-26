package com.ledgerlens.data.repositories.impl

import app.cash.turbine.test
import com.ledgerlens.db.LedgerLensDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class SqlDelightAccountRepositoryTest {
    private lateinit var database: LedgerLensDatabase
    private lateinit var repository: SqlDelightAccountRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        repository = SqlDelightAccountRepository(database, testDispatcher)
    }

    @Test
    fun `insertAccount stores account correctly`() = runTest(testDispatcher) {
        val account = AccountTestFixtures.createTestAccountEntity()

        repository.insertAccount(account)

        repository.getAccount(account.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(account.displayName, result.displayName)
            assertEquals(account.institutionName, result.institutionName)
            assertEquals(account.accountType, result.accountType)
            assertEquals(account.currencyCode, result.currencyCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getActiveAccounts returns only active accounts`() = runTest(testDispatcher) {
        val activeAccount = AccountTestFixtures.createTestAccountEntity(id = "active-1", isActive = true)
        val inactiveAccount = AccountTestFixtures.createTestAccountEntity(id = "inactive-1", isActive = false)

        repository.insertAccount(activeAccount)
        repository.insertAccount(inactiveAccount)

        repository.getActiveAccounts().test {
            val accounts = awaitItem()
            assertEquals(1, accounts.size)
            assertEquals("active-1", accounts.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllAccounts returns all accounts including inactive`() = runTest(testDispatcher) {
        val activeAccount = AccountTestFixtures.createTestAccountEntity(id = "active-1", isActive = true)
        val inactiveAccount = AccountTestFixtures.createTestAccountEntity(id = "inactive-1", isActive = false)

        repository.insertAccount(activeAccount)
        repository.insertAccount(inactiveAccount)

        repository.getAllAccounts().test {
            val accounts = awaitItem()
            assertEquals(2, accounts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateAccount modifies account correctly`() = runTest(testDispatcher) {
        val account = AccountTestFixtures.createTestAccountEntity()
        repository.insertAccount(account)

        val updatedAccount = account.copy(displayName = "Updated Name")
        repository.updateAccount(updatedAccount)

        repository.getAccount(account.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Updated Name", result.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAccount soft deletes by deactivating`() = runTest(testDispatcher) {
        val account = AccountTestFixtures.createTestAccountEntity()
        repository.insertAccount(account)

        repository.deleteAccount(account.id)

        repository.getActiveAccounts().test {
            val accounts = awaitItem()
            assertEquals(0, accounts.size)
            cancelAndIgnoreRemainingEvents()
        }

        // But still exists in getAllAccounts
        repository.getAllAccounts().test {
            val accounts = awaitItem()
            assertEquals(1, accounts.size)
            assertFalse(accounts.first().isActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hardDeleteAccount removes account completely`() = runTest(testDispatcher) {
        val account = AccountTestFixtures.createTestAccountEntity()
        repository.insertAccount(account)

        repository.hardDeleteAccount(account.id)

        repository.getAllAccounts().test {
            val accounts = awaitItem()
            assertEquals(0, accounts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exists returns true for existing account`() = runTest(testDispatcher) {
        val account = AccountTestFixtures.createTestAccountEntity()
        repository.insertAccount(account)

        assertTrue(repository.exists(account.id))
    }

    @Test
    fun `exists returns false for non-existent account`() = runTest(testDispatcher) {
        assertFalse(repository.exists("non-existent"))
    }

    @Test
    fun `findByName returns account when found`() = runTest(testDispatcher) {
        val account = AccountTestFixtures.createTestAccountEntity(displayName = "My Checking")
        repository.insertAccount(account)

        val result = repository.findByName("My Checking")
        assertNotNull(result)
        assertEquals(account.id, result.id)
    }

    @Test
    fun `findByName returns null when not found`() = runTest(testDispatcher) {
        val result = repository.findByName("Non-existent Account")
        assertNull(result)
    }

    @Test
    fun `flow emits updates when account changes`() = runTest(testDispatcher) {
        val account = AccountTestFixtures.createTestAccountEntity()

        repository.getAllAccounts().test {
            // Initial empty state
            assertEquals(0, awaitItem().size)

            // Insert account
            repository.insertAccount(account)
            assertEquals(1, awaitItem().size)

            // Update account
            repository.updateAccount(account.copy(displayName = "New Name"))
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("New Name", updated.first().displayName)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
