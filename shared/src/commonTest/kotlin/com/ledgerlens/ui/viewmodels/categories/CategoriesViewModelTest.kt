package com.ledgerlens.ui.viewmodels.categories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CategoriesViewModel
    
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CategoriesViewModel()
    }
    
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    // ========== Categories State Tests ==========
    
    @Test
    fun `initial state has empty categories`() {
        val state = viewModel.categoriesState.value
        assertTrue(state.categories.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
    
    @Test
    fun `setSearchQuery updates search query`() {
        viewModel.setSearchQuery("food")
        assertEquals("food", viewModel.categoriesState.value.searchQuery)
    }
    
    @Test
    fun `toggleExpanded adds category to expanded set`() {
        viewModel.toggleExpanded("cat-1")
        assertTrue(viewModel.categoriesState.value.expandedCategories.contains("cat-1"))
    }
    
    @Test
    fun `toggleExpanded removes category from expanded set when already expanded`() {
        viewModel.toggleExpanded("cat-1")
        viewModel.toggleExpanded("cat-1")
        assertFalse(viewModel.categoriesState.value.expandedCategories.contains("cat-1"))
    }
    
    @Test
    fun `expandAll expands all categories`() {
        viewModel.expandAll()
        // With empty categories, this should still work
        // Just verifying no errors
    }
    
    @Test
    fun `collapseAll clears expanded categories`() {
        viewModel.toggleExpanded("cat-1")
        viewModel.toggleExpanded("cat-2")
        viewModel.collapseAll()
        assertTrue(viewModel.categoriesState.value.expandedCategories.isEmpty())
    }
    
    // ========== Edit Category Tests ==========
    
    @Test
    fun `startCreatingCategory sets editing state for new category`() {
        viewModel.startCreatingCategory()
        assertNotNull(viewModel.categoriesState.value.editingCategory)
        assertTrue(viewModel.categoriesState.value.isCreatingNew)
    }
    
    @Test
    fun `startCreatingCategory with parent sets parentId`() {
        viewModel.startCreatingCategory("parent-1")
        val editing = viewModel.categoriesState.value.editingCategory
        assertNotNull(editing)
        assertEquals("parent-1", editing.parentId)
    }
    
    @Test
    fun `cancelEditing clears editing state`() {
        viewModel.startCreatingCategory()
        viewModel.cancelEditing()
        assertNull(viewModel.categoriesState.value.editingCategory)
        assertFalse(viewModel.categoriesState.value.isCreatingNew)
    }
    
    @Test
    fun `updateEditingName updates category name`() {
        viewModel.startCreatingCategory()
        viewModel.updateEditingName("New Name")
        assertEquals("New Name", viewModel.categoriesState.value.editingCategory?.name)
    }
    
    @Test
    fun `updateEditingIcon updates category icon`() {
        viewModel.startCreatingCategory()
        viewModel.updateEditingIcon("🍕")
        assertEquals("🍕", viewModel.categoriesState.value.editingCategory?.icon)
    }
    
    @Test
    fun `updateEditingColor updates category color`() {
        viewModel.startCreatingCategory()
        viewModel.updateEditingColor("#FF0000")
        assertEquals("#FF0000", viewModel.categoriesState.value.editingCategory?.color)
    }
    
    @Test
    fun `updateEditingParent updates category parentId`() {
        viewModel.startCreatingCategory()
        viewModel.updateEditingParent("parent-2")
        assertEquals("parent-2", viewModel.categoriesState.value.editingCategory?.parentId)
    }
    
    // ========== Rules State Tests ==========
    
    @Test
    fun `initial rules state has empty rules`() {
        val state = viewModel.rulesState.value
        assertTrue(state.rules.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
    
    @Test
    fun `setRulesSearchQuery updates search query`() {
        viewModel.setRulesSearchQuery("merchant")
        assertEquals("merchant", viewModel.rulesState.value.searchQuery)
    }
    
    @Test
    fun `setRulesFilterEnabled updates filter`() {
        viewModel.setRulesFilterEnabled(true)
        assertEquals(true, viewModel.rulesState.value.filterEnabled)
        
        viewModel.setRulesFilterEnabled(false)
        assertEquals(false, viewModel.rulesState.value.filterEnabled)
        
        viewModel.setRulesFilterEnabled(null)
        assertNull(viewModel.rulesState.value.filterEnabled)
    }
    
    @Test
    fun `toggleRulesSortByPriority toggles sort order`() {
        assertFalse(viewModel.rulesState.value.sortByPriority)
        viewModel.toggleRulesSortByPriority()
        assertTrue(viewModel.rulesState.value.sortByPriority)
        viewModel.toggleRulesSortByPriority()
        assertFalse(viewModel.rulesState.value.sortByPriority)
    }
    
    @Test
    fun `selectRule updates selected rule`() {
        viewModel.selectRule("rule-1")
        assertEquals("rule-1", viewModel.rulesState.value.selectedRuleId)
        
        viewModel.selectRule(null)
        assertNull(viewModel.rulesState.value.selectedRuleId)
    }
    
    // ========== Rule Wizard Tests ==========
    
    @Test
    fun `showRuleWizard opens wizard`() {
        viewModel.showRuleWizard()
        assertTrue(viewModel.rulesState.value.showRuleWizard)
    }
    
    @Test
    fun `hideRuleWizard closes wizard`() {
        viewModel.showRuleWizard()
        viewModel.hideRuleWizard()
        assertFalse(viewModel.rulesState.value.showRuleWizard)
    }
    
    @Test
    fun `initial wizard state is on NAME step`() {
        viewModel.showRuleWizard()
        assertEquals(RuleWizardStep.NAME, viewModel.wizardState.value.step)
    }
    
    @Test
    fun `wizardSetName updates name`() {
        viewModel.showRuleWizard()
        viewModel.wizardSetName("Test Rule")
        assertEquals("Test Rule", viewModel.wizardState.value.name)
    }
    
    @Test
    fun `wizardSetDescription updates description`() {
        viewModel.showRuleWizard()
        viewModel.wizardSetDescription("Test description")
        assertEquals("Test description", viewModel.wizardState.value.description)
    }
    
    @Test
    fun `wizardNextStep advances step when valid`() {
        viewModel.showRuleWizard()
        viewModel.wizardSetName("Test Rule")
        viewModel.wizardNextStep()
        assertEquals(RuleWizardStep.CONDITIONS, viewModel.wizardState.value.step)
    }
    
    @Test
    fun `wizardNextStep does not advance when name is empty`() {
        viewModel.showRuleWizard()
        viewModel.wizardNextStep()
        assertEquals(RuleWizardStep.NAME, viewModel.wizardState.value.step)
        assertTrue(viewModel.wizardState.value.validationErrors.isNotEmpty())
    }
    
    @Test
    fun `wizardPreviousStep goes back one step`() {
        viewModel.showRuleWizard()
        viewModel.wizardSetName("Test Rule")
        viewModel.wizardNextStep()
        viewModel.wizardPreviousStep()
        assertEquals(RuleWizardStep.NAME, viewModel.wizardState.value.step)
    }
    
    @Test
    fun `wizardAddCondition adds condition`() {
        viewModel.showRuleWizard()
        val condition = com.ledgerlens.categorization.rules.MerchantContains("Starbucks")
        viewModel.wizardAddCondition(condition)
        assertTrue(viewModel.wizardState.value.conditions.contains(condition))
    }
    
    @Test
    fun `wizardRemoveCondition removes condition`() {
        viewModel.showRuleWizard()
        val condition = com.ledgerlens.categorization.rules.MerchantContains("Starbucks")
        viewModel.wizardAddCondition(condition)
        viewModel.wizardRemoveCondition(0)
        assertFalse(viewModel.wizardState.value.conditions.contains(condition))
    }
    
    @Test
    fun `wizardSetCategory updates selected category`() {
        viewModel.showRuleWizard()
        viewModel.wizardSetCategory("cat-food")
        assertEquals("cat-food", viewModel.wizardState.value.selectedCategoryId)
    }
    
    // ========== Error Handling Tests ==========
    
    @Test
    fun `clearError clears all errors`() {
        viewModel.clearError()
        assertNull(viewModel.categoriesState.value.error)
        assertNull(viewModel.rulesState.value.error)
    }
}
