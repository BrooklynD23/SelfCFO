package com.ledgerlens.ui.viewmodels.categories

import com.ledgerlens.categorization.Category
import com.ledgerlens.categorization.CategoryNode
import com.ledgerlens.categorization.CategoryTree
import com.ledgerlens.categorization.CategoryValidationResult
import com.ledgerlens.categorization.validate
import com.ledgerlens.categorization.rules.*
import com.ledgerlens.data.repositories.CategoryEntity
import com.ledgerlens.data.repositories.CategoryRepository
import com.ledgerlens.data.repositories.CategoryWithStats
import com.ledgerlens.data.repositories.RuleEntity
import com.ledgerlens.data.repositories.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI model for a category with usage statistics.
 */
data class CategoryUiModel(
    val id: String,
    val name: String,
    val parentId: String?,
    val icon: String?,
    val color: String?,
    val isSystemDefault: Boolean,
    val transactionCount: Int = 0,
    val totalAmount: Long = 0,
    val children: List<CategoryUiModel> = emptyList(),
    val depth: Int = 0,
    val isExpanded: Boolean = false
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
    val canDelete: Boolean get() = !isSystemDefault && transactionCount == 0
    val canAddChild: Boolean get() = depth < Category.MAX_HIERARCHY_DEPTH - 1
}

/**
 * UI model for a categorization rule.
 */
data class RuleUiModel(
    val id: String,
    val name: String,
    val description: String?,
    val conditionSummary: String,
    val actionSummary: String,
    val priority: Int,
    val enabled: Boolean,
    val matchCount: Long,
    val source: RuleSource,
    val categoryId: String?,
    val categoryName: String?
) {
    val isUserCreated: Boolean get() = source == RuleSource.USER
    val isSuggested: Boolean get() = source == RuleSource.SUGGESTED
}

/**
 * UI state for categories screen.
 */
data class CategoriesUiState(
    val categories: List<CategoryUiModel> = emptyList(),
    val expandedCategoryIds: Set<String> = emptySet(),
    val selectedCategoryId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showEditDialog: Boolean = false,
    val editingCategory: Category? = null,
    val isCreatingNew: Boolean = false
) {
    val flattenedCategories: List<CategoryUiModel>
        get() = categories.flatMap { flattenCategory(it) }
    
    private fun flattenCategory(category: CategoryUiModel): List<CategoryUiModel> {
        val isExpanded = category.id in expandedCategoryIds
        val updatedCategory = category.copy(isExpanded = isExpanded)
        
        return if (isExpanded && category.hasChildren) {
            listOf(updatedCategory) + category.children.flatMap { flattenCategory(it) }
        } else {
            listOf(updatedCategory)
        }
    }
    
    val selectedCategory: CategoryUiModel?
        get() = findCategory(selectedCategoryId, categories)
    
    private fun findCategory(id: String?, list: List<CategoryUiModel>): CategoryUiModel? {
        if (id == null) return null
        for (cat in list) {
            if (cat.id == id) return cat
            findCategory(id, cat.children)?.let { return it }
        }
        return null
    }
}

/**
 * UI state for rules screen.
 */
data class RulesUiState(
    val rules: List<RuleUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedRuleId: String? = null,
    val showRuleWizard: Boolean = false,
    val editingRule: CategoryRule? = null,
    val searchQuery: String = "",
    val filterEnabled: Boolean? = null, // null = all, true = enabled only, false = disabled only
    val sortByPriority: Boolean = true
) {
    val filteredRules: List<RuleUiModel>
        get() {
            var result = rules
            
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.lowercase()
                result = result.filter { 
                    it.name.lowercase().contains(query) ||
                    it.conditionSummary.lowercase().contains(query) ||
                    it.categoryName?.lowercase()?.contains(query) == true
                }
            }
            
            filterEnabled?.let { enabled ->
                result = result.filter { it.enabled == enabled }
            }
            
            result = if (sortByPriority) {
                result.sortedByDescending { it.priority }
            } else {
                result.sortedBy { it.name.lowercase() }
            }
            
            return result
        }
}

/**
 * State for rule wizard/editor.
 */
data class RuleWizardState(
    val step: RuleWizardStep = RuleWizardStep.NAME,
    val name: String = "",
    val description: String = "",
    val conditions: List<RuleCondition> = emptyList(),
    val selectedCategoryId: String? = null,
    val priority: Int = CategoryRule.DEFAULT_PRIORITY,
    val enabled: Boolean = true,
    val validationErrors: List<String> = emptyList()
) {
    val isValid: Boolean get() = name.isNotBlank() && conditions.isNotEmpty() && selectedCategoryId != null
    val canProceed: Boolean get() = when (step) {
        RuleWizardStep.NAME -> name.isNotBlank()
        RuleWizardStep.CONDITIONS -> conditions.isNotEmpty()
        RuleWizardStep.ACTION -> selectedCategoryId != null
        RuleWizardStep.REVIEW -> isValid
    }
}

enum class RuleWizardStep {
    NAME, CONDITIONS, ACTION, REVIEW
}

/**
 * ViewModel for categories and rules screens.
 */
class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
    private val ruleRepository: RuleRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cache for category lookups
    private var categoryCache: Map<String, CategoryEntity> = emptyMap()

    private val _categoriesState = MutableStateFlow(CategoriesUiState())
    val categoriesState: StateFlow<CategoriesUiState> = _categoriesState.asStateFlow()

    private val _rulesState = MutableStateFlow(RulesUiState())
    val rulesState: StateFlow<RulesUiState> = _rulesState.asStateFlow()

    private val _wizardState = MutableStateFlow(RuleWizardState())
    val wizardState: StateFlow<RuleWizardState> = _wizardState.asStateFlow()

    init {
        loadCategories()
        loadRules()
    }
    
    // ========== Categories Actions ==========

    fun loadCategories() {
        viewModelScope.launch {
            _categoriesState.update { it.copy(isLoading = true, error = null) }
            try {
                val categoriesWithStats = categoryRepository.getCategoriesWithStats().first()
                categoryCache = categoriesWithStats.associate { it.category.id to it.category }
                val uiModels = buildCategoryTree(categoriesWithStats)
                _categoriesState.update { it.copy(
                    categories = uiModels,
                    isLoading = false
                )}
            } catch (e: Exception) {
                _categoriesState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load categories: ${e.message}"
                )}
            }
        }
    }
    
    fun toggleCategoryExpanded(categoryId: String) {
        _categoriesState.update { state ->
            val newExpanded = if (categoryId in state.expandedCategoryIds) {
                state.expandedCategoryIds - categoryId
            } else {
                state.expandedCategoryIds + categoryId
            }
            state.copy(expandedCategoryIds = newExpanded)
        }
    }
    
    fun selectCategory(categoryId: String?) {
        _categoriesState.update { it.copy(selectedCategoryId = categoryId) }
    }
    
    fun showCreateCategoryDialog(parentId: String? = null) {
        _categoriesState.update { 
            it.copy(
                showEditDialog = true,
                isCreatingNew = true,
                editingCategory = Category(
                    id = "",
                    name = "",
                    parentId = parentId,
                    isUserCustom = true
                )
            )
        }
    }
    
    fun showEditCategoryDialog(categoryId: String) {
        val category = findCategoryDomain(categoryId)
        _categoriesState.update { 
            it.copy(
                showEditDialog = true,
                isCreatingNew = false,
                editingCategory = category
            )
        }
    }
    
    fun hideEditDialog() {
        _categoriesState.update { 
            it.copy(
                showEditDialog = false,
                editingCategory = null,
                isCreatingNew = false
            )
        }
    }
    
    fun updateEditingCategory(
        name: String? = null,
        icon: String? = null,
        color: String? = null,
        parentId: String? = null
    ) {
        _categoriesState.update { state ->
            val current = state.editingCategory ?: return@update state
            state.copy(
                editingCategory = current.copy(
                    name = name ?: current.name,
                    icon = icon ?: current.icon,
                    color = color ?: current.color,
                    parentId = if (parentId != null) parentId.ifEmpty { null } else current.parentId
                )
            )
        }
    }
    
    fun saveCategory() {
        viewModelScope.launch {
            val state = _categoriesState.value
            val category = state.editingCategory ?: return@launch

            try {
                // Validate
                val categoryToValidate = category.copy(
                    id = if (state.isCreatingNew) generateCategoryId(category.name) else category.id
                )
                val validation = categoryToValidate.validate()

                if (validation is CategoryValidationResult.Invalid) {
                    _categoriesState.update { it.copy(error = validation.reason) }
                    return@launch
                }

                // Convert to entity and save
                val categoryEntity = CategoryEntity(
                    id = if (state.isCreatingNew) generateCategoryId(category.name) else category.id,
                    name = category.name,
                    parentId = category.parentId,
                    isSystemDefault = category.isSystemDefault,
                    isUserCustom = category.isUserCustom,
                    icon = category.icon,
                    color = category.color,
                    sortOrder = 0
                )

                if (state.isCreatingNew) {
                    categoryRepository.insertCategory(categoryEntity)
                } else {
                    categoryRepository.updateCategory(categoryEntity)
                }

                hideEditDialog()
                loadCategories()
            } catch (e: Exception) {
                _categoriesState.update { it.copy(error = "Failed to save category: ${e.message}") }
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            try {
                categoryRepository.deleteCategory(categoryId)
                loadCategories()
            } catch (e: Exception) {
                _categoriesState.update { it.copy(error = "Failed to delete category: ${e.message}") }
            }
        }
    }
    
    // ========== Rules Actions ==========

    fun loadRules() {
        viewModelScope.launch {
            _rulesState.update { it.copy(isLoading = true, error = null) }
            try {
                val rules = ruleRepository.getAllRules().first()
                val uiModels = rules.map { it.toUiModel() }
                _rulesState.update { it.copy(
                    rules = uiModels,
                    isLoading = false
                )}
            } catch (e: Exception) {
                _rulesState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load rules: ${e.message}"
                )}
            }
        }
    }
    
    fun setRulesSearchQuery(query: String) {
        _rulesState.update { it.copy(searchQuery = query) }
    }
    
    fun setRulesFilterEnabled(enabled: Boolean?) {
        _rulesState.update { it.copy(filterEnabled = enabled) }
    }
    
    fun toggleRulesSortByPriority() {
        _rulesState.update { it.copy(sortByPriority = !it.sortByPriority) }
    }
    
    fun selectRule(ruleId: String?) {
        _rulesState.update { it.copy(selectedRuleId = ruleId) }
    }
    
    fun toggleRuleEnabled(ruleId: String) {
        viewModelScope.launch {
            try {
                val rule = ruleRepository.getRule(ruleId).first()
                if (rule != null) {
                    ruleRepository.setEnabled(ruleId, !rule.isEnabled)
                }
                loadRules()
            } catch (e: Exception) {
                _rulesState.update { it.copy(error = "Failed to toggle rule: ${e.message}") }
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            try {
                ruleRepository.deleteRule(ruleId)
                loadRules()
            } catch (e: Exception) {
                _rulesState.update { it.copy(error = "Failed to delete rule: ${e.message}") }
            }
        }
    }

    fun updateRulePriority(ruleId: String, newPriority: Int) {
        viewModelScope.launch {
            try {
                ruleRepository.updatePriority(ruleId, newPriority)
                loadRules()
            } catch (e: Exception) {
                _rulesState.update { it.copy(error = "Failed to update priority: ${e.message}") }
            }
        }
    }
    
    // ========== Rule Wizard Actions ==========
    
    fun showRuleWizard(editingRuleId: String? = null) {
        if (editingRuleId != null) {
            // TODO: Load existing rule
            _wizardState.update { RuleWizardState() }
        } else {
            _wizardState.update { RuleWizardState() }
        }
        _rulesState.update { it.copy(showRuleWizard = true) }
    }
    
    fun hideRuleWizard() {
        _rulesState.update { it.copy(showRuleWizard = false, editingRule = null) }
        _wizardState.update { RuleWizardState() }
    }
    
    fun wizardSetName(name: String) {
        _wizardState.update { it.copy(name = name) }
    }
    
    fun wizardSetDescription(description: String) {
        _wizardState.update { it.copy(description = description) }
    }
    
    fun wizardAddCondition(condition: RuleCondition) {
        _wizardState.update { it.copy(conditions = it.conditions + condition) }
    }
    
    fun wizardRemoveCondition(index: Int) {
        _wizardState.update { 
            it.copy(conditions = it.conditions.filterIndexed { i, _ -> i != index })
        }
    }
    
    fun wizardSetCategory(categoryId: String) {
        _wizardState.update { it.copy(selectedCategoryId = categoryId) }
    }
    
    fun wizardSetPriority(priority: Int) {
        _wizardState.update { it.copy(priority = priority.coerceIn(CategoryRule.MIN_PRIORITY, CategoryRule.MAX_PRIORITY)) }
    }
    
    fun wizardSetEnabled(enabled: Boolean) {
        _wizardState.update { it.copy(enabled = enabled) }
    }
    
    fun wizardNextStep() {
        _wizardState.update { state ->
            val nextStep = when (state.step) {
                RuleWizardStep.NAME -> RuleWizardStep.CONDITIONS
                RuleWizardStep.CONDITIONS -> RuleWizardStep.ACTION
                RuleWizardStep.ACTION -> RuleWizardStep.REVIEW
                RuleWizardStep.REVIEW -> RuleWizardStep.REVIEW
            }
            state.copy(step = nextStep)
        }
    }
    
    fun wizardPreviousStep() {
        _wizardState.update { state ->
            val prevStep = when (state.step) {
                RuleWizardStep.NAME -> RuleWizardStep.NAME
                RuleWizardStep.CONDITIONS -> RuleWizardStep.NAME
                RuleWizardStep.ACTION -> RuleWizardStep.CONDITIONS
                RuleWizardStep.REVIEW -> RuleWizardStep.ACTION
            }
            state.copy(step = prevStep)
        }
    }
    
    fun wizardSaveRule() {
        viewModelScope.launch {
            val state = _wizardState.value

            if (!state.isValid) {
                _wizardState.update { it.copy(validationErrors = listOf("Please complete all required fields")) }
                return@launch
            }

            try {
                // Build conditions JSON (simplified for now)
                val conditionsJson = state.conditions.joinToString(",") {
                    """{"type":"${it::class.simpleName}"}"""
                }.let { "[$it]" }

                val ruleEntity = RuleEntity(
                    id = generateRuleId(state.name),
                    name = state.name,
                    conditionsJson = conditionsJson,
                    targetCategoryId = state.selectedCategoryId!!,
                    priority = state.priority,
                    isEnabled = state.enabled,
                    matchCount = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                ruleRepository.insertRule(ruleEntity)

                hideRuleWizard()
                loadRules()
            } catch (e: Exception) {
                _wizardState.update { it.copy(validationErrors = listOf("Failed to save rule: ${e.message}")) }
            }
        }
    }
    
    // ========== Helpers ==========

    fun clearError() {
        _categoriesState.update { it.copy(error = null) }
        _rulesState.update { it.copy(error = null) }
    }

    private fun findCategoryDomain(categoryId: String): Category? {
        val entity = categoryCache[categoryId] ?: return null
        return Category(
            id = entity.id,
            name = entity.name,
            parentId = entity.parentId,
            icon = entity.icon,
            color = entity.color,
            isSystemDefault = entity.isSystemDefault,
            isUserCustom = entity.isUserCustom
        )
    }

    private fun generateCategoryId(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-') + "-${System.currentTimeMillis()}"
    }

    private fun generateRuleId(name: String): String {
        return "rule-" + name.lowercase()
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-') + "-${System.currentTimeMillis()}"
    }

    private fun buildCategoryTree(categoriesWithStats: List<CategoryWithStats>): List<CategoryUiModel> {
        val categoryMap = categoriesWithStats.associateBy { it.category.id }

        // Find root categories (no parent)
        val roots = categoriesWithStats.filter { it.category.parentId == null }

        fun buildNode(catWithStats: CategoryWithStats, depth: Int): CategoryUiModel {
            val children = categoriesWithStats
                .filter { it.category.parentId == catWithStats.category.id }
                .map { buildNode(it, depth + 1) }

            return catWithStats.toUiModel(depth, children)
        }

        return roots.map { buildNode(it, 0) }
    }

    // Extension function to map CategoryWithStats to CategoryUiModel
    private fun CategoryWithStats.toUiModel(depth: Int, children: List<CategoryUiModel>): CategoryUiModel {
        return CategoryUiModel(
            id = category.id,
            name = category.name,
            parentId = category.parentId,
            icon = category.icon,
            color = category.color,
            isSystemDefault = category.isSystemDefault,
            transactionCount = transactionCount,
            totalAmount = totalSpentMinorUnits,
            children = children,
            depth = depth,
            isExpanded = false
        )
    }

    // Extension function to map RuleEntity to RuleUiModel
    private fun RuleEntity.toUiModel(): RuleUiModel {
        val targetCategory = categoryCache[targetCategoryId]
        return RuleUiModel(
            id = id,
            name = name,
            description = null,
            conditionSummary = parseConditionSummary(conditionsJson),
            actionSummary = "Set category to ${targetCategory?.name ?: targetCategoryId}",
            priority = priority,
            enabled = isEnabled,
            matchCount = matchCount.toLong(),
            source = RuleSource.USER,
            categoryId = targetCategoryId,
            categoryName = targetCategory?.name
        )
    }

    private fun parseConditionSummary(conditionsJson: String): String {
        // Simple parsing for display
        return if (conditionsJson.contains("MerchantEquals")) {
            "Merchant matches"
        } else if (conditionsJson.contains("AmountRange")) {
            "Amount in range"
        } else {
            "Custom conditions"
        }
    }
}
