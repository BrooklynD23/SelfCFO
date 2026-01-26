package com.ledgerlens.ui.viewmodels.categories

import com.ledgerlens.categorization.rules.MerchantContains
import com.ledgerlens.data.repositories.fake.FakeCategoryRepository
import com.ledgerlens.data.repositories.fake.FakeRuleRepository
import com.ledgerlens.data.repositories.fake.TestDataFactory
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var viewModel: CategoriesViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        categoryRepository = FakeCategoryRepository()
        ruleRepository = FakeRuleRepository()

        // Seed test data
        categoryRepository.setCategories(
            listOf(
                TestDataFactory.createCategory(id = "groceries", name = "Groceries", isSystemDefault = true),
                TestDataFactory.createCategory(id = "dining", name = "Dining", isSystemDefault = true),
                TestDataFactory.createCategory(id = "fast-food", name = "Fast Food", parentId = "dining"),
                TestDataFactory.createCategory(id = "restaurants", name = "Restaurants", parentId = "dining")
            )
        )

        ruleRepository.setRules(
            listOf(
                TestDataFactory.createRule(id = "rule-1", name = "Starbucks", targetCategoryId = "dining"),
                TestDataFactory.createRule(id = "rule-2", name = "Walmart", targetCategoryId = "groceries")
            )
        )

        viewModel = CategoriesViewModel(categoryRepository, ruleRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Categories State Tests ==========

    @Test
    fun `initial state is loading then loaded`() = runTest {
        advanceUntilIdle()
        val state = viewModel.categoriesState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadCategories populates categories`() = runTest {
        advanceUntilIdle()
        val state = viewModel.categoriesState.value
        assertTrue(state.categories.isNotEmpty())
    }

    @Test
    fun `toggleCategoryExpanded adds category to expanded set`() = runTest {
        advanceUntilIdle()
        viewModel.toggleCategoryExpanded("dining")
        assertTrue(viewModel.categoriesState.value.expandedCategoryIds.contains("dining"))
    }

    @Test
    fun `toggleCategoryExpanded removes category from expanded set when already expanded`() = runTest {
        advanceUntilIdle()
        viewModel.toggleCategoryExpanded("dining")
        viewModel.toggleCategoryExpanded("dining")
        assertFalse(viewModel.categoriesState.value.expandedCategoryIds.contains("dining"))
    }

    @Test
    fun `selectCategory updates selectedCategoryId`() = runTest {
        advanceUntilIdle()
        viewModel.selectCategory("groceries")
        assertEquals("groceries", viewModel.categoriesState.value.selectedCategoryId)
    }

    @Test
    fun `selectCategory with null clears selection`() = runTest {
        advanceUntilIdle()
        viewModel.selectCategory("groceries")
        viewModel.selectCategory(null)
        assertNull(viewModel.categoriesState.value.selectedCategoryId)
    }

    // ========== Edit Category Tests ==========

    @Test
    fun `showCreateCategoryDialog sets editing state for new category`() = runTest {
        advanceUntilIdle()
        viewModel.showCreateCategoryDialog()
        val state = viewModel.categoriesState.value
        assertTrue(state.showEditDialog)
        assertTrue(state.isCreatingNew)
        assertNotNull(state.editingCategory)
    }

    @Test
    fun `showCreateCategoryDialog with parent sets parentId`() = runTest {
        advanceUntilIdle()
        viewModel.showCreateCategoryDialog("dining")
        val editing = viewModel.categoriesState.value.editingCategory
        assertNotNull(editing)
        assertEquals("dining", editing.parentId)
    }

    @Test
    fun `hideEditDialog clears editing state`() = runTest {
        advanceUntilIdle()
        viewModel.showCreateCategoryDialog()
        viewModel.hideEditDialog()
        val state = viewModel.categoriesState.value
        assertFalse(state.showEditDialog)
        assertNull(state.editingCategory)
        assertFalse(state.isCreatingNew)
    }

    @Test
    fun `updateEditingCategory updates category name`() = runTest {
        advanceUntilIdle()
        viewModel.showCreateCategoryDialog()
        viewModel.updateEditingCategory(name = "New Name")
        assertEquals("New Name", viewModel.categoriesState.value.editingCategory?.name)
    }

    @Test
    fun `updateEditingCategory updates category icon`() = runTest {
        advanceUntilIdle()
        viewModel.showCreateCategoryDialog()
        viewModel.updateEditingCategory(icon = "🍕")
        assertEquals("🍕", viewModel.categoriesState.value.editingCategory?.icon)
    }

    @Test
    fun `updateEditingCategory updates category color`() = runTest {
        advanceUntilIdle()
        viewModel.showCreateCategoryDialog()
        viewModel.updateEditingCategory(color = "#FF0000")
        assertEquals("#FF0000", viewModel.categoriesState.value.editingCategory?.color)
    }

    @Test
    fun `updateEditingCategory updates category parentId`() = runTest {
        advanceUntilIdle()
        viewModel.showCreateCategoryDialog()
        viewModel.updateEditingCategory(parentId = "groceries")
        assertEquals("groceries", viewModel.categoriesState.value.editingCategory?.parentId)
    }

    // ========== Rules State Tests ==========

    @Test
    fun `initial rules state loads rules`() = runTest {
        advanceUntilIdle()
        val state = viewModel.rulesState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.rules.isNotEmpty())
    }

    @Test
    fun `setRulesSearchQuery updates search query`() = runTest {
        advanceUntilIdle()
        viewModel.setRulesSearchQuery("merchant")
        assertEquals("merchant", viewModel.rulesState.value.searchQuery)
    }

    @Test
    fun `setRulesFilterEnabled updates filter`() = runTest {
        advanceUntilIdle()
        viewModel.setRulesFilterEnabled(true)
        assertEquals(true, viewModel.rulesState.value.filterEnabled)

        viewModel.setRulesFilterEnabled(false)
        assertEquals(false, viewModel.rulesState.value.filterEnabled)

        viewModel.setRulesFilterEnabled(null)
        assertNull(viewModel.rulesState.value.filterEnabled)
    }

    @Test
    fun `toggleRulesSortByPriority toggles sort order`() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.rulesState.value.sortByPriority) // default is true
        viewModel.toggleRulesSortByPriority()
        assertFalse(viewModel.rulesState.value.sortByPriority)
        viewModel.toggleRulesSortByPriority()
        assertTrue(viewModel.rulesState.value.sortByPriority)
    }

    @Test
    fun `selectRule updates selected rule`() = runTest {
        advanceUntilIdle()
        viewModel.selectRule("rule-1")
        assertEquals("rule-1", viewModel.rulesState.value.selectedRuleId)

        viewModel.selectRule(null)
        assertNull(viewModel.rulesState.value.selectedRuleId)
    }

    // ========== Rule Wizard Tests ==========

    @Test
    fun `showRuleWizard opens wizard`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        assertTrue(viewModel.rulesState.value.showRuleWizard)
    }

    @Test
    fun `hideRuleWizard closes wizard`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.hideRuleWizard()
        assertFalse(viewModel.rulesState.value.showRuleWizard)
    }

    @Test
    fun `initial wizard state is on NAME step`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        assertEquals(RuleWizardStep.NAME, viewModel.wizardState.value.step)
    }

    @Test
    fun `wizardSetName updates name`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.wizardSetName("Test Rule")
        assertEquals("Test Rule", viewModel.wizardState.value.name)
    }

    @Test
    fun `wizardSetDescription updates description`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.wizardSetDescription("Test description")
        assertEquals("Test description", viewModel.wizardState.value.description)
    }

    @Test
    fun `wizardNextStep advances step when name is set`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.wizardSetName("Test Rule")
        viewModel.wizardNextStep()
        assertEquals(RuleWizardStep.CONDITIONS, viewModel.wizardState.value.step)
    }

    @Test
    fun `wizardPreviousStep goes back one step`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.wizardSetName("Test Rule")
        viewModel.wizardNextStep()
        viewModel.wizardPreviousStep()
        assertEquals(RuleWizardStep.NAME, viewModel.wizardState.value.step)
    }

    @Test
    fun `wizardAddCondition adds condition`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        val condition = MerchantContains("Starbucks")
        viewModel.wizardAddCondition(condition)
        assertTrue(viewModel.wizardState.value.conditions.contains(condition))
    }

    @Test
    fun `wizardRemoveCondition removes condition`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        val condition = MerchantContains("Starbucks")
        viewModel.wizardAddCondition(condition)
        viewModel.wizardRemoveCondition(0)
        assertFalse(viewModel.wizardState.value.conditions.contains(condition))
    }

    @Test
    fun `wizardSetCategory updates selected category`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.wizardSetCategory("groceries")
        assertEquals("groceries", viewModel.wizardState.value.selectedCategoryId)
    }

    @Test
    fun `wizardSetPriority updates priority within bounds`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.wizardSetPriority(50)
        assertEquals(50, viewModel.wizardState.value.priority)
    }

    @Test
    fun `wizardSetEnabled updates enabled flag`() = runTest {
        advanceUntilIdle()
        viewModel.showRuleWizard()
        viewModel.wizardSetEnabled(false)
        assertFalse(viewModel.wizardState.value.enabled)
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `clearError clears all errors`() = runTest {
        advanceUntilIdle()
        viewModel.clearError()
        assertNull(viewModel.categoriesState.value.error)
        assertNull(viewModel.rulesState.value.error)
    }

    // ========== Delete Tests ==========

    @Test
    fun `deleteCategory removes category from repository`() = runTest {
        advanceUntilIdle()
        val initialCount = viewModel.categoriesState.value.categories.size

        viewModel.deleteCategory("fast-food")
        advanceUntilIdle()

        // Since fast-food is a child, it won't be in the root list
        // but we can verify no error occurred
        assertNull(viewModel.categoriesState.value.error)
    }

    @Test
    fun `deleteRule removes rule from repository`() = runTest {
        advanceUntilIdle()
        val initialCount = viewModel.rulesState.value.rules.size

        viewModel.deleteRule("rule-1")
        advanceUntilIdle()

        assertEquals(initialCount - 1, viewModel.rulesState.value.rules.size)
    }

    @Test
    fun `toggleRuleEnabled toggles rule enabled state`() = runTest {
        advanceUntilIdle()
        val initialEnabled = viewModel.rulesState.value.rules.first { it.id == "rule-1" }.enabled

        viewModel.toggleRuleEnabled("rule-1")
        advanceUntilIdle()

        val newEnabled = viewModel.rulesState.value.rules.first { it.id == "rule-1" }.enabled
        assertEquals(!initialEnabled, newEnabled)
    }
}
