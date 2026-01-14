package com.ledgerlens.categorization

/**
 * Domain model for a category in the transaction categorization hierarchy.
 * Categories can have parent-child relationships forming a tree structure.
 *
 * @property id Unique identifier (stable UUID or slug for system defaults)
 * @property name Display name of the category
 * @property parentId ID of parent category, null for top-level categories
 * @property isSystemDefault True if this is a built-in system category
 * @property isUserCustom True if this category was created by the user
 * @property icon Optional icon identifier (e.g., "home", "car", "restaurant")
 * @property color Optional hex color code (e.g., "#4CAF50")
 * @property sortOrder Sort position within siblings (lower = first)
 */
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
    /**
     * Returns true if this is a top-level (root) category.
     */
    val isTopLevel: Boolean
        get() = parentId == null

    /**
     * Returns true if this category can be deleted.
     * System default categories cannot be deleted.
     */
    val canDelete: Boolean
        get() = !isSystemDefault

    /**
     * Returns true if this category can be edited.
     * Both system and user categories can be edited (name, icon, color).
     */
    val canEdit: Boolean
        get() = true

    companion object {
        const val MAX_HIERARCHY_DEPTH = 3
        const val UNCATEGORIZED_ID = "uncategorized"
        const val TRANSFER_ID = "transfer"
        const val INCOME_ID = "income"
    }
}

/**
 * Validation result for category operations.
 */
sealed class CategoryValidationResult {
    data object Valid : CategoryValidationResult()
    data class Invalid(val reason: String) : CategoryValidationResult()
}

/**
 * Validates a category for creation or update.
 */
fun Category.validate(): CategoryValidationResult {
    if (id.isBlank()) {
        return CategoryValidationResult.Invalid("Category ID cannot be blank")
    }
    if (name.isBlank()) {
        return CategoryValidationResult.Invalid("Category name cannot be blank")
    }
    if (name.length > 100) {
        return CategoryValidationResult.Invalid("Category name cannot exceed 100 characters")
    }
    if (id == parentId) {
        return CategoryValidationResult.Invalid("Category cannot be its own parent")
    }
    color?.let {
        if (!it.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            return CategoryValidationResult.Invalid("Invalid color format. Expected hex color (e.g., #4CAF50)")
        }
    }
    return CategoryValidationResult.Valid
}
