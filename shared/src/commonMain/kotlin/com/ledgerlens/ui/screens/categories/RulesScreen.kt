package com.ledgerlens.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerlens.categorization.rules.RuleSource
import com.ledgerlens.ui.viewmodels.categories.CategoriesViewModel
import com.ledgerlens.ui.viewmodels.categories.RuleUiModel
import com.ledgerlens.ui.viewmodels.categories.RulesUiState

/**
 * Rules management screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: CategoriesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.rulesState.collectAsState()
    var showFilterMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Categorization Rules") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showRuleWizard() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rule")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showRuleWizard() },
                // TODO: Replace with LedgerLensTheme.colors.primary
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search and filter bar
            RulesFilterBar(
                state = state,
                showFilterMenu = showFilterMenu,
                onSearchQueryChange = viewModel::setRulesSearchQuery,
                onShowFilterMenu = { showFilterMenu = it },
                onFilterEnabledChange = {
                    viewModel.setRulesFilterEnabled(it)
                    showFilterMenu = false
                },
                onToggleSortByPriority = viewModel::toggleRulesSortByPriority
            )
            
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = viewModel::loadRules,
                        onDismiss = viewModel::clearError
                    )
                }
                state.filteredRules.isEmpty() -> {
                    EmptyRulesContent(
                        hasFilters = state.searchQuery.isNotBlank() || state.filterEnabled != null,
                        onAddRule = { viewModel.showRuleWizard() }
                    )
                }
                else -> {
                    RulesList(
                        rules = state.filteredRules,
                        selectedRuleId = state.selectedRuleId,
                        onRuleClick = viewModel::selectRule,
                        onToggleEnabled = viewModel::toggleRuleEnabled,
                        onEditRule = { viewModel.showRuleWizard(it) },
                        onDeleteRule = viewModel::deleteRule
                    )
                }
            }
        }
        
        // Rule wizard
        if (state.showRuleWizard) {
            RuleWizardDialog(
                viewModel = viewModel,
                onDismiss = viewModel::hideRuleWizard
            )
        }
    }
}

@Composable
private fun RulesFilterBar(
    state: RulesUiState,
    showFilterMenu: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onShowFilterMenu: (Boolean) -> Unit,
    onFilterEnabledChange: (Boolean?) -> Unit,
    onToggleSortByPriority: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search field
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search rules...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filter by enabled
            item {
                Box {
                    FilterChip(
                        selected = state.filterEnabled != null,
                        onClick = { onShowFilterMenu(true) },
                        label = { 
                            Text(when (state.filterEnabled) {
                                true -> "Enabled"
                                false -> "Disabled"
                                null -> "All"
                            })
                        },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { onShowFilterMenu(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All rules") },
                            onClick = { onFilterEnabledChange(null) },
                            leadingIcon = {
                                if (state.filterEnabled == null) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Enabled only") },
                            onClick = { onFilterEnabledChange(true) },
                            leadingIcon = {
                                if (state.filterEnabled == true) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Disabled only") },
                            onClick = { onFilterEnabledChange(false) },
                            leadingIcon = {
                                if (state.filterEnabled == false) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
            
            // Sort toggle
            item {
                FilterChip(
                    selected = state.sortByPriority,
                    onClick = onToggleSortByPriority,
                    label = { 
                        Text(if (state.sortByPriority) "By Priority" else "By Name")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RulesList(
    rules: List<RuleUiModel>,
    selectedRuleId: String?,
    onRuleClick: (String?) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onEditRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rules, key = { it.id }) { rule ->
            RuleListItem(
                rule = rule,
                isSelected = rule.id == selectedRuleId,
                onClick = { onRuleClick(rule.id) },
                onToggleEnabled = { onToggleEnabled(rule.id) },
                onEdit = { onEditRule(rule.id) },
                onDelete = { onDeleteRule(rule.id) }
            )
        }
        
        // Bottom spacer for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun RuleListItem(
    rule: RuleUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                !rule.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rule name and badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Enabled switch
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.height(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rule.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (rule.enabled) 
                                    MaterialTheme.colorScheme.onSurface 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Source badge
                            val (sourceText, sourceColor) = when (rule.source) {
                                RuleSource.USER -> "Custom" to MaterialTheme.colorScheme.primary
                                RuleSource.SYSTEM -> "System" to MaterialTheme.colorScheme.secondary
                                RuleSource.SUGGESTED -> "Suggested" to Color(0xFFFF9800)
                                RuleSource.IMPORTED -> "Imported" to Color(0xFF9C27B0)
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = sourceColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = sourceText,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = sourceColor
                                )
                            }
                        }
                        
                        // Priority indicator
                        Text(
                            text = "Priority: ${rule.priority}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        
                        if (rule.isUserCreated) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Condition summary
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = rule.conditionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Action summary (category)
            if (rule.categoryName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Label,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "→ ${rule.categoryName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Match count
            if (rule.matchCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${rule.matchCount} matches",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyRulesContent(
    hasFilters: Boolean,
    onAddRule: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Rule,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (hasFilters) "No rules match your filters" else "No rules yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (hasFilters)
                "Try adjusting your search or filters"
            else
                "Create rules to automatically categorize transactions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        if (!hasFilters) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onAddRule) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Rule")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleWizardDialog(
    viewModel: CategoriesViewModel,
    onDismiss: () -> Unit
) {
    val wizardState by viewModel.wizardState.collectAsState()
    val categoriesState by viewModel.categoriesState.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        // TODO: Replace with LedgerLensCard
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Text(
                    text = "Create Rule",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Step indicator
                LinearProgressIndicator(
                    progress = { (wizardState.step.ordinal + 1) / 4f },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Step content
                when (wizardState.step) {
                    com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.NAME -> {
                        NameStep(
                            name = wizardState.name,
                            description = wizardState.description,
                            onNameChange = viewModel::wizardSetName,
                            onDescriptionChange = viewModel::wizardSetDescription
                        )
                    }
                    com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.CONDITIONS -> {
                        ConditionsStep(
                            conditions = wizardState.conditions,
                            onAddCondition = viewModel::wizardAddCondition,
                            onRemoveCondition = viewModel::wizardRemoveCondition
                        )
                    }
                    com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.ACTION -> {
                        ActionStep(
                            selectedCategoryId = wizardState.selectedCategoryId,
                            categories = categoriesState.flattenedCategories,
                            onSelectCategory = viewModel::wizardSetCategory
                        )
                    }
                    com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.REVIEW -> {
                        ReviewStep(state = wizardState)
                    }
                }
                
                // Validation errors
                if (wizardState.validationErrors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    wizardState.validationErrors.forEach { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            if (wizardState.step == com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.NAME) {
                                onDismiss()
                            } else {
                                viewModel.wizardPreviousStep()
                            }
                        }
                    ) {
                        Text(
                            if (wizardState.step == com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.NAME) 
                                "Cancel" 
                            else 
                                "Back"
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (wizardState.step == com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.REVIEW) {
                                viewModel.wizardSaveRule()
                            } else {
                                viewModel.wizardNextStep()
                            }
                        },
                        enabled = wizardState.canProceed
                    ) {
                        Text(
                            if (wizardState.step == com.ledgerlens.ui.viewmodels.categories.RuleWizardStep.REVIEW) 
                                "Create Rule" 
                            else 
                                "Next"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    description: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Step 1: Name your rule",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Rule Name *") },
            placeholder = { Text("e.g., Coffee Shops") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description (optional)") },
            placeholder = { Text("e.g., Match all coffee shop transactions") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConditionsStep(
    conditions: List<com.ledgerlens.categorization.rules.RuleCondition>,
    onAddCondition: (com.ledgerlens.categorization.rules.RuleCondition) -> Unit,
    onRemoveCondition: (Int) -> Unit
) {
    var showConditionPicker by remember { mutableStateOf(false) }
    var merchantPattern by remember { mutableStateOf("") }
    
    Column {
        Text(
            text = "Step 2: Set conditions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Define when this rule should apply",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Existing conditions
        conditions.forEachIndexed { index, condition ->
            // TODO: Replace with LedgerLensCard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = condition.describe(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = { onRemoveCondition(index) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Simple condition builder (merchant contains)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = merchantPattern,
                onValueChange = { merchantPattern = it },
                label = { Text("Merchant contains") },
                placeholder = { Text("e.g., Starbucks") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = {
                    if (merchantPattern.isNotBlank()) {
                        onAddCondition(
                            com.ledgerlens.categorization.rules.MerchantContains(merchantPattern)
                        )
                        merchantPattern = ""
                    }
                },
                enabled = merchantPattern.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add condition")
            }
        }
    }
}

@Composable
private fun ActionStep(
    selectedCategoryId: String?,
    categories: List<com.ledgerlens.ui.viewmodels.categories.CategoryUiModel>,
    onSelectCategory: (String) -> Unit
) {
    Column {
        Text(
            text = "Step 3: Choose action",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Select the category to assign",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Category picker
        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                val isSelected = category.id == selectedCategoryId
                val indentDp = (category.depth * 16).dp
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = indentDp)
                        .clickable { onSelectCategory(category.id) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(
    state: com.ledgerlens.ui.viewmodels.categories.RuleWizardState
) {
    Column {
        Text(
            text = "Step 4: Review",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // TODO: Replace with LedgerLensCard
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (state.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "When:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                state.conditions.forEach { condition ->
                    Text(
                        text = "• ${condition.describe()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Then:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "• Set category to: ${state.selectedCategoryId ?: "None"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
