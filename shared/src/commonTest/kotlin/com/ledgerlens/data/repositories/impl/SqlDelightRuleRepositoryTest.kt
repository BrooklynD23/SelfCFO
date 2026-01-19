package com.ledgerlens.data.repositories.impl

import app.cash.turbine.test
import com.ledgerlens.data.repositories.RuleEntity
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightRuleRepositoryTest {
    private lateinit var database: LedgerLensDatabase
    private lateinit var repository: SqlDelightRuleRepository
    private lateinit var categoryRepository: SqlDelightCategoryRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        repository = SqlDelightRuleRepository(database, testDispatcher)
        categoryRepository = SqlDelightCategoryRepository(database, testDispatcher)
    }

    private suspend fun setupTestCategory() {
        // Rules need a valid category reference
        categoryRepository.seedDefaultCategories()
    }

    @Test
    fun `insertRule stores rule correctly`() = runTest(testDispatcher) {
        setupTestCategory()
        val rule = RuleTestFixtures.createTestRuleEntity(targetCategoryId = "food")

        repository.insertRule(rule)

        repository.getRule(rule.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(rule.targetCategoryId, result.targetCategoryId)
            assertEquals(rule.priority, result.priority)
            assertTrue(result.isEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllRules returns all rules ordered by priority`() = runTest(testDispatcher) {
        setupTestCategory()
        val rule1 = RuleTestFixtures.createTestRuleEntity(id = "rule-1", priority = 200, targetCategoryId = "food")
        val rule2 = RuleTestFixtures.createTestRuleEntity(id = "rule-2", priority = 100, targetCategoryId = "food")

        repository.insertRule(rule1)
        repository.insertRule(rule2)

        repository.getAllRules().test {
            val rules = awaitItem()
            assertEquals(2, rules.size)
            assertEquals("rule-2", rules[0].id) // priority 100 first
            assertEquals("rule-1", rules[1].id) // priority 200 second
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getEnabledRules returns only enabled rules`() = runTest(testDispatcher) {
        setupTestCategory()
        val enabledRule = RuleTestFixtures.createTestRuleEntity(id = "enabled", isEnabled = true, targetCategoryId = "food")
        val disabledRule = RuleTestFixtures.createTestRuleEntity(id = "disabled", isEnabled = false, targetCategoryId = "food")

        repository.insertRule(enabledRule)
        repository.insertRule(disabledRule)

        repository.getEnabledRules().test {
            val rules = awaitItem()
            assertEquals(1, rules.size)
            assertEquals("enabled", rules.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateRule modifies rule correctly`() = runTest(testDispatcher) {
        setupTestCategory()
        val rule = RuleTestFixtures.createTestRuleEntity(targetCategoryId = "food")
        repository.insertRule(rule)

        val updated = rule.copy(priority = 50, conditionsJson = """{"type":"merchant_equals","value":"Starbucks"}""")
        repository.updateRule(updated)

        repository.getRule(rule.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(50, result.priority)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteRule removes rule`() = runTest(testDispatcher) {
        setupTestCategory()
        val rule = RuleTestFixtures.createTestRuleEntity(targetCategoryId = "food")
        repository.insertRule(rule)

        repository.deleteRule(rule.id)

        repository.getRule(rule.id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setEnabled toggles rule enabled state`() = runTest(testDispatcher) {
        setupTestCategory()
        val rule = RuleTestFixtures.createTestRuleEntity(isEnabled = true, targetCategoryId = "food")
        repository.insertRule(rule)

        repository.setEnabled(rule.id, false)

        repository.getRule(rule.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertFalse(result.isEnabled)
            cancelAndIgnoreRemainingEvents()
        }

        repository.setEnabled(rule.id, true)

        repository.getRule(rule.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertTrue(result.isEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updatePriority changes rule priority`() = runTest(testDispatcher) {
        setupTestCategory()
        val rule = RuleTestFixtures.createTestRuleEntity(priority = 100, targetCategoryId = "food")
        repository.insertRule(rule)

        repository.updatePriority(rule.id, 50)

        repository.getRule(rule.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(50, result.priority)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reorderRules updates priorities based on order`() = runTest(testDispatcher) {
        setupTestCategory()
        val rule1 = RuleTestFixtures.createTestRuleEntity(id = "rule-1", priority = 100, targetCategoryId = "food")
        val rule2 = RuleTestFixtures.createTestRuleEntity(id = "rule-2", priority = 200, targetCategoryId = "food")
        val rule3 = RuleTestFixtures.createTestRuleEntity(id = "rule-3", priority = 300, targetCategoryId = "food")

        repository.insertRule(rule1)
        repository.insertRule(rule2)
        repository.insertRule(rule3)

        // Reorder: rule3, rule1, rule2
        repository.reorderRules(listOf("rule-3", "rule-1", "rule-2"))

        repository.getAllRules().test {
            val rules = awaitItem()
            assertEquals("rule-3", rules[0].id) // priority 1
            assertEquals("rule-1", rules[1].id) // priority 2
            assertEquals("rule-2", rules[2].id) // priority 3
            cancelAndIgnoreRemainingEvents()
        }
    }
}
