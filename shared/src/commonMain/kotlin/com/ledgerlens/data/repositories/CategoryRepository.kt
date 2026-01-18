package com.ledgerlens.data.repositories

import kotlinx.coroutines.flow.Flow

/**
 * Domain model for a category.
 */
data class CategoryEntity(
    val id: String,
    val name: String,
    val parentId: String?,
    val isSystemDefault: Boolean,
    val isUserCustom: Boolean,
    val icon: String?,
    val color: String?,
    val sortOrder: Int
)

/**
 * Category with usage statistics.
 */
data class CategoryWithStats(
    val category: CategoryEntity,
    val transactionCount: Int,
    val totalSpentMinorUnits: Long,
    val totalIncomeMinorUnits: Long
)

/**
 * Repository interface for category data access.
 */
interface CategoryRepository {
    /**
     * Get all categories ordered by sort order and name.
     */
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /**
     * Get a category by ID.
     */
    fun getCategory(id: String): Flow<CategoryEntity?>

    /**
     * Get child categories of a parent.
     */
    fun getCategoriesByParent(parentId: String): Flow<List<CategoryEntity>>

    /**
     * Get top-level categories (no parent).
     */
    fun getTopLevelCategories(): Flow<List<CategoryEntity>>

    /**
     * Get categories with usage statistics.
     */
    fun getCategoriesWithStats(): Flow<List<CategoryWithStats>>

    /**
     * Insert a new category.
     */
    suspend fun insertCategory(category: CategoryEntity)

    /**
     * Update a category.
     */
    suspend fun updateCategory(category: CategoryEntity)

    /**
     * Delete a category.
     * Note: Categories with children should be handled specially.
     */
    suspend fun deleteCategory(id: String)

    /**
     * Seed default categories if not present.
     */
    suspend fun seedDefaultCategories()

    /**
     * Check if a category ID exists.
     */
    suspend fun exists(id: String): Boolean
}
