package com.ledgerlens.categorization

data class Category(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val isSystemDefault: Boolean = false,
    val isUserCustom: Boolean = false,
    val icon: String? = null,
    val color: String? = null,
    val sortOrder: Int = 0
) {
    val isTopLevel: Boolean get() = parentId == null
    val canDelete: Boolean get() = !isSystemDefault
    val canEdit: Boolean get() = true

    companion object {
        const val MAX_HIERARCHY_DEPTH = 3
        const val UNCATEGORIZED_ID = "uncategorized"
        const val TRANSFER_ID = "transfer"
        const val INCOME_ID = "income"
    }
}

sealed class CategoryValidationResult {
    data object Valid : CategoryValidationResult()
    data class Invalid(val reason: String) : CategoryValidationResult()
}

fun Category.validate(): CategoryValidationResult {
    if (id.isBlank()) return CategoryValidationResult.Invalid("Category ID cannot be blank")
    if (name.isBlank()) return CategoryValidationResult.Invalid("Category name cannot be blank")
    if (name.length > 100) return CategoryValidationResult.Invalid("Category name cannot exceed 100 characters")
    if (id == parentId) return CategoryValidationResult.Invalid("Category cannot be its own parent")
    color?.let {
        if (!it.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            return CategoryValidationResult.Invalid("Invalid color format. Expected hex color (e.g., #4CAF50)")
        }
    }
    return CategoryValidationResult.Valid
}
