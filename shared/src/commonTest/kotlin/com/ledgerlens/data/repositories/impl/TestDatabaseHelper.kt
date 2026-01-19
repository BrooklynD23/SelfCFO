package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerlens.db.LedgerLensDatabase

/**
 * Helper class for creating in-memory test databases.
 */
object TestDatabaseHelper {
    /**
     * Creates an in-memory LedgerLensDatabase for testing.
     */
    fun createInMemoryDatabase(): LedgerLensDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerLensDatabase.Schema.create(driver)
        return LedgerLensDatabase(driver)
    }
}

/**
 * Test fixtures for account-related tests.
 */
object AccountTestFixtures {
    fun createTestAccountEntity(
        id: String = "test-account-1",
        displayName: String = "Test Checking",
        institutionName: String? = "Test Bank",
        currencyCode: String = "USD",
        isActive: Boolean = true
    ) = com.ledgerlens.data.repositories.AccountEntity(
        id = id,
        displayName = displayName,
        institutionName = institutionName,
        accountType = com.ledgerlens.data.repositories.AccountType.CHECKING,
        accountNumberMasked = "****1234",
        currencyCode = currencyCode,
        isActive = isActive,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Test fixtures for category-related tests.
 */
object CategoryTestFixtures {
    fun createTestCategoryEntity(
        id: String = "test-category-1",
        name: String = "Test Category",
        parentId: String? = null,
        isSystemDefault: Boolean = false,
        sortOrder: Int = 0
    ) = com.ledgerlens.data.repositories.CategoryEntity(
        id = id,
        name = name,
        parentId = parentId,
        isSystemDefault = isSystemDefault,
        isUserCustom = !isSystemDefault,
        icon = null,
        color = null,
        sortOrder = sortOrder
    )
}

/**
 * Test fixtures for rule-related tests.
 */
object RuleTestFixtures {
    fun createTestRuleEntity(
        id: String = "test-rule-1",
        name: String = "Test Rule",
        conditionsJson: String = """{"type":"merchant_contains","value":"Coffee"}""",
        targetCategoryId: String = "food",
        priority: Int = 100,
        isEnabled: Boolean = true
    ) = com.ledgerlens.data.repositories.RuleEntity(
        id = id,
        name = name,
        conditionsJson = conditionsJson,
        targetCategoryId = targetCategoryId,
        priority = priority,
        isEnabled = isEnabled,
        matchCount = 0,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
