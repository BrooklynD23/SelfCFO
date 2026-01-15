# 08: Categories & Rules Screen

## Overview

Implement category management and categorization rules configuration screens.

---

## Implementation Steps

### Step 1: Categories State

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/categories/CategoriesState.kt
package com.ledgerlens.ui.screens.categories

data class CategoriesState(
    val isLoading: Boolean = true,
    val categories: List<CategoryTreeNode> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
    val searchQuery: String = ""
)

data class CategoryTreeNode(
    val id: String,
    val name: String,
    val icon: String?,
    val color: String,
    val parentId: String?,
    val level: Int,
    val children: List<CategoryTreeNode>,
    val transactionCount: Int,
    val isSystemCategory: Boolean
)

data class CategoryEditState(
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val icon: String? = null,
    val color: String = "#808080",
    val parentId: String? = null,
    val availableParents: List<CategoryOption> = emptyList(),
    val errors: Map<String, String> = emptyMap()
)

data class RulesState(
    val isLoading: Boolean = true,
    val rules: List<RuleDisplay> = emptyList(),
    val suggestedRules: List<RuleSuggestionDisplay> = emptyList()
)

data class RuleDisplay(
    val id: String,
    val name: String?,
    val ruleType: String,
    val matchExpression: String,
    val targetCategoryName: String,
    val targetCategoryColor: String,
    val priority: Int,
    val enabled: Boolean,
    val matchCount: Int
)

data class RuleSuggestionDisplay(
    val pattern: String,
    val ruleType: String,
    val categoryId: String,
    val categoryName: String,
    val correctionCount: Int,
    val potentialMatches: Int,
    val confidence: Float
)

data class RuleEditState(
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val ruleType: String = "merchant_contains",
    val matchExpression: String = "",
    val targetCategoryId: String? = null,
    val priority: Int = 100,
    val enabled: Boolean = true,
    val testResult: RuleTestResult? = null,
    val categories: List<CategoryOption> = emptyList(),
    val errors: Map<String, String> = emptyMap()
)

data class RuleTestResult(
    val matchCount: Int,
    val totalTransactions: Int,
    val sampleMatches: List<String>
)
```

### Step 2: Categories Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/categories/CategoriesScreen.kt
package com.ledgerlens.ui.screens.categories

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onEditCategory: (String?) -> Unit,
    onAddCategory: () -> Unit,
    viewModel: CategoriesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCategory) {
                Icon(Icons.Default.Add, "Add category")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::search,
                modifier = Modifier.padding(16.dp)
            )

            if (state.isLoading) {
                LoadingState()
            } else {
                CategoryTree(
                    categories = state.categories,
                    expandedIds = state.expandedIds,
                    onToggleExpand = viewModel::toggleExpand,
                    onCategoryClick = onEditCategory
                )
            }
        }
    }
}

@Composable
private fun CategoryTree(
    categories: List<CategoryTreeNode>,
    expandedIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    LazyColumn {
        categories.forEach { category ->
            item(key = category.id) {
                CategoryTreeItem(
                    category = category,
                    isExpanded = category.id in expandedIds,
                    onToggleExpand = { onToggleExpand(category.id) },
                    onClick = { onCategoryClick(category.id) }
                )
            }

            // Render children if expanded
            if (category.id in expandedIds && category.children.isNotEmpty()) {
                items(category.children, key = { it.id }) { child ->
                    CategoryTreeItem(
                        category = child,
                        isExpanded = child.id in expandedIds,
                        onToggleExpand = { onToggleExpand(child.id) },
                        onClick = { onCategoryClick(child.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTreeItem(
    category: CategoryTreeNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    val indent = category.level * 24

    ListItem(
        modifier = Modifier
            .padding(start = indent.dp)
            .clickable(onClick = onClick),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(category.color.toColor(), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.icon ?: category.name.take(1),
                        color = Color.White
                    )
                }
                Text(category.name)
            }
        },
        supportingContent = {
            Text("${category.transactionCount} transactions")
        },
        leadingContent = {
            if (category.children.isNotEmpty()) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null)
        }
    )
}
```

### Step 3: Category Edit Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/categories/CategoryEditScreen.kt
package com.ledgerlens.ui.screens.categories

@Composable
fun CategoryEditScreen(
    categoryId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CategoryEditViewModel = koinViewModel { parametersOf(categoryId) }
) {
    val state by viewModel.state.collectAsState()
    var showColorPicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New Category" else "Edit Category") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save()
                            onSaved()
                        },
                        enabled = state.errors.isEmpty() && state.name.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview
            CategoryPreview(
                name = state.name,
                icon = state.icon,
                color = state.color
            )

            // Name input
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Category Name") },
                isError = state.errors.containsKey("name"),
                supportingText = state.errors["name"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            // Color picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showColorPicker = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(state.color.toColor(), CircleShape)
                )
            }

            // Icon picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showIconPicker = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Icon")
                Text(state.icon ?: "None", style = MaterialTheme.typography.bodyLarge)
            }

            // Parent category
            ExposedDropdownMenuBox(
                expanded = false,
                onExpandedChange = {}
            ) {
                OutlinedTextField(
                    value = state.availableParents
                        .find { it.id == state.parentId }?.name ?: "None (Top Level)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Parent Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Dropdown menu with parent options
            }

            // Delete button (for existing categories)
            if (!state.isNew) {
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.delete()
                        onBack()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Category")
                }
            }
        }
    }

    // Color picker dialog
    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = state.color,
            onColorSelected = {
                viewModel.setColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // Icon picker dialog
    if (showIconPicker) {
        IconPickerDialog(
            currentIcon = state.icon,
            onIconSelected = {
                viewModel.setIcon(it)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
}
```

### Step 4: Rules Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/rules/RulesScreen.kt
package com.ledgerlens.ui.screens.rules

@Composable
fun RulesScreen(
    onBack: () -> Unit,
    onEditRule: (String?) -> Unit,
    onAddRule: () -> Unit,
    viewModel: RulesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorization Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(Icons.Default.Add, "Add rule")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Suggested rules section
            if (state.suggestedRules.isNotEmpty()) {
                item {
                    Text(
                        text = "Suggested Rules",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(state.suggestedRules) { suggestion ->
                    RuleSuggestionCard(
                        suggestion = suggestion,
                        onAccept = { viewModel.acceptSuggestion(suggestion) },
                        onDismiss = { viewModel.dismissSuggestion(suggestion) }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Active rules
            item {
                Text(
                    text = "Active Rules",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.rules.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Rule,
                        title = "No rules yet",
                        message = "Create rules to automatically categorize transactions"
                    )
                }
            } else {
                itemsIndexed(state.rules, key = { _, rule -> rule.id }) { index, rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { onEditRule(rule.id) },
                        onToggleEnabled = { viewModel.toggleRuleEnabled(rule.id) },
                        onMoveUp = if (index > 0) {
                            { viewModel.moveRule(rule.id, -1) }
                        } else null,
                        onMoveDown = if (index < state.rules.lastIndex) {
                            { viewModel.moveRule(rule.id, 1) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: RuleDisplay,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    LedgerCard(onClick = onEdit) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name ?: rule.matchExpression,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${rule.ruleType}: \"${rule.matchExpression}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                CategoryChip(
                    name = rule.targetCategoryName,
                    color = rule.targetCategoryColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${rule.matchCount} matches",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Reorder buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                onMoveUp?.let {
                    IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move up")
                    }
                }
                onMoveDown?.let {
                    IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move down")
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSuggestionCard(
    suggestion: RuleSuggestionDisplay,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    LedgerCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Secondary
                )
                Text(
                    text = "Suggested: \"${suggestion.pattern}\"",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Based on ${suggestion.correctionCount} corrections, would match ${suggestion.potentialMatches} transactions",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = onAccept) {
                    Text("Create Rule")
                }
            }
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Category tree displays with hierarchy
- [ ] Expand/collapse category groups
- [ ] Create new category with name, icon, color
- [ ] Edit existing category
- [ ] Delete category (with confirmation)
- [ ] Rules list shows all rules in priority order
- [ ] Create new rule with pattern and category
- [ ] Test rule against existing transactions
- [ ] Enable/disable rules
- [ ] Reorder rule priority
- [ ] Rule suggestions from corrections shown
- [ ] Accept/dismiss rule suggestions

---

## Estimated Complexity

**Medium** - CRUD interfaces with tree view and rule testing.

