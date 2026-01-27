package com.ledgerlens.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.ledgerlens.categorization.Category
import com.ledgerlens.ui.viewmodels.categories.CategoryUiModel

/**
 * Dialog for creating or editing a category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditDialog(
    category: Category?,
    isCreating: Boolean,
    availableParents: List<CategoryUiModel>,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onParentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (category == null) return
    
    var showIconPicker by remember { mutableStateOf(false) }
    var showParentPicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isCreating) "Create Category" else "Edit Category",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = category.name,
                    onValueChange = onNameChange,
                    label = { Text("Category Name *") },
                    placeholder = { Text("e.g., Groceries") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = category.name.isBlank()
                )
                
                // Icon selection
                Column {
                    Text(
                        text = "Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showIconPicker = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
                                    style = MaterialTheme.typography.titleLarge
                                )
                            } else {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = category.icon ?: "Choose icon",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (category.icon != null) 
                                MaterialTheme.colorScheme.onSurface 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Color selection
                Column {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ColorPicker(
                        selectedColor = category.color,
                        onColorSelected = onColorChange
                    )
                }
                
                // Parent selection (for subcategories)
                if (availableParents.isNotEmpty() || category.parentId != null) {
                    Column {
                        Text(
                            text = "Parent Category",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showParentPicker = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val parentName = availableParents.find { it.id == category.parentId }?.name
                            
                            Text(
                                text = parentName ?: "None (top-level)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (parentName != null) 
                                    MaterialTheme.colorScheme.onSurface 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = category.name.isNotBlank()
            ) {
                Text(if (isCreating) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Icon picker dialog
    if (showIconPicker) {
        IconPickerDialog(
            selectedIcon = category.icon,
            onIconSelected = { icon ->
                onIconChange(icon)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
    
    // Parent picker dialog
    if (showParentPicker) {
        ParentPickerDialog(
            selectedParentId = category.parentId,
            availableParents = availableParents,
            onParentSelected = { parentId ->
                onParentChange(parentId ?: "")
                showParentPicker = false
            },
            onDismiss = { showParentPicker = false }
        )
    }
}

@Composable
private fun ColorPicker(
    selectedColor: String?,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#4CAF50", // Green
        "#2196F3", // Blue
        "#9C27B0", // Purple
        "#FF9800", // Orange
        "#E91E63", // Pink
        "#00BCD4", // Cyan
        "#FF5722", // Deep Orange
        "#795548", // Brown
        "#607D8B", // Blue Grey
        "#F44336"  // Red
    )
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(colors) { colorHex ->
            val color = parseColor(colorHex)
            val isSelected = selectedColor == colorHex
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorHex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPickerDialog(
    selectedIcon: String?,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val icons = listOf(
        // Food & Drink
        "🍔", "🍕", "🍜", "☕", "🍺", "🍷", "🍰", "🥗",
        // Shopping
        "🛒", "🛍️", "👕", "👗", "👟", "💄", "💎", "🎁",
        // Transport
        "🚗", "🚌", "✈️", "🚇", "⛽", "🚕", "🚲", "🛵",
        // Home
        "🏠", "🏢", "🔧", "💡", "🛋️", "🧹", "🌿", "🔑",
        // Entertainment
        "🎬", "🎮", "🎵", "📚", "🎨", "🎭", "🏋️", "⚽",
        // Health
        "💊", "🏥", "🩺", "🧘", "💪", "🦷", "👓", "🧴",
        // Finance
        "💰", "💳", "📈", "🏦", "💵", "🧾", "📊", "💹",
        // Other
        "📱", "💻", "📧", "🎓", "✂️", "🐾", "👶", "❤️"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Icon") },
        text = {
            Column {
                for (row in icons.chunked(8)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { icon ->
                            val isSelected = icon == selectedIcon
                            
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent
                                    )
                                    .clickable { onIconSelected(icon) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = icon,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ParentPickerDialog(
    selectedParentId: String?,
    availableParents: List<CategoryUiModel>,
    onParentSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Parent Category") },
        text = {
            Column {
                // None option (top-level)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selectedParentId == null) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                Color.Transparent
                        )
                        .clickable { onParentSelected(null) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedParentId == null) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = "None (top-level)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selectedParentId == null) FontWeight.Medium else FontWeight.Normal
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Available parents
                availableParents.forEach { parent ->
                    val isSelected = parent.id == selectedParentId
                    val indentDp = (parent.depth * 16).dp
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentDp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    Color.Transparent
                            )
                            .clickable { onParentSelected(parent.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        // Category color indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    parent.color?.let { parseColor(it) }
                                        ?: MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = parent.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
