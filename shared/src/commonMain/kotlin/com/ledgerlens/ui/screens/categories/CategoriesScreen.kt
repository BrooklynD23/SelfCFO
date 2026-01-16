package com.ledgerlens.ui.screens.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.ledgerlens.ui.viewmodels.categories.CategoriesUiState
import com.ledgerlens.ui.viewmodels.categories.CategoriesViewModel
import com.ledgerlens.ui.viewmodels.categories.CategoryUiModel

/**
 * Categories management screen with tree view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigateToRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.categoriesState.collectAsState()
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                actions = {
                    IconButton(onClick = onNavigateToRules) {
                        Icon(Icons.Default.Rule, contentDescription = "Rules")
                    }
                    IconButton(onClick = { viewModel.showCreateCategoryDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateCategoryDialog() },
                // TODO: Replace with LedgerLensTheme.colors.primary
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                        onRetry = viewModel::loadCategories,
                        onDismiss = viewModel::clearError
                    )
                }
                state.categories.isEmpty() -> {
                    EmptyCategoriesContent(
                        onAddCategory = { viewModel.showCreateCategoryDialog() }
                    )
                }
                else -> {
                    CategoriesList(
                        categories = state.flattenedCategories,
                        selectedCategoryId = state.selectedCategoryId,
                        onCategoryClick = viewModel::selectCategory,
                        onToggleExpand = viewModel::toggleCategoryExpanded,
                        onEditCategory = viewModel::showEditCategoryDialog,
                        onDeleteCategory = viewModel::deleteCategory,
                        onAddSubcategory = { parentId -> viewModel.showCreateCategoryDialog(parentId) }
                    )
                }
            }
        }
        
        // Edit/Create dialog
        if (state.showEditDialog) {
            CategoryEditDialog(
                category = state.editingCategory,
                isCreating = state.isCreatingNew,
                availableParents = state.categories.filter { it.canAddChild },
                onNameChange = { viewModel.updateEditingCategory(name = it) },
                onIconChange = { viewModel.updateEditingCategory(icon = it) },
                onColorChange = { viewModel.updateEditingCategory(color = it) },
                onParentChange = { viewModel.updateEditingCategory(parentId = it) },
                onSave = viewModel::saveCategory,
                onDismiss = viewModel::hideEditDialog
            )
        }
    }
}

@Composable
private fun CategoriesList(
    categories: List<CategoryUiModel>,
    selectedCategoryId: String?,
    onCategoryClick: (String?) -> Unit,
    onToggleExpand: (String) -> Unit,
    onEditCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onAddSubcategory: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryListItem(
                category = category,
                isSelected = category.id == selectedCategoryId,
                onClick = { onCategoryClick(category.id) },
                onToggleExpand = { onToggleExpand(category.id) },
                onEdit = { onEditCategory(category.id) },
                onDelete = { onDeleteCategory(category.id) },
                onAddSubcategory = { onAddSubcategory(category.id) }
            )
        }
        
        // Bottom spacer for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CategoryListItem(
    category: CategoryUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddSubcategory: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val indentDp = (category.depth * 24).dp
    
    // TODO: Replace with LedgerLensCard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon
            if (category.hasChildren) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (category.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (category.isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
            
            // Category icon/color
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        category.color?.let { parseColor(it) } 
                            ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (category.icon != null) {
                    Text(
                        text = category.icon,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = category.color?.let { parseColor(it) }?.copy(alpha = 0.8f)
                            ?: MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Category info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (category.isSystemDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "System",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                // Usage stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${category.transactionCount} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (category.totalAmount > 0) {
                        Text(
                            text = "$${category.totalAmount / 100}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
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
                    
                    if (category.canAddChild) {
                        DropdownMenuItem(
                            text = { Text("Add subcategory") },
                            onClick = {
                                showMenu = false
                                onAddSubcategory()
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                        )
                    }
                    
                    if (category.canDelete) {
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
    }
}

@Composable
private fun EmptyCategoriesContent(
    onAddCategory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Category,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No categories yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Create categories to organize your transactions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onAddCategory) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Category")
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

private fun parseColor(hexColor: String): Color {
    return try {
        val hex = hexColor.removePrefix("#")
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f
        )
    } catch (e: Exception) {
        Color.Gray
    }
}
