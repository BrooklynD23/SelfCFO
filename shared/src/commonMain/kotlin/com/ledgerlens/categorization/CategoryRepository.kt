package com.ledgerlens.categorization

/**
 * Repository interface for category CRUD operations and tree traversal.
 */
interface CategoryRepository {
    /**
     * Returns all categories ordered by sortOrder and name.
     */
    suspend fun getAll(): List<Category>

    /**
     * Returns a category by ID, or null if not found.
     */
    suspend fun getById(id: String): Category?

    /**
     * Returns children of a given parent category.
     * Pass null to get top-level categories.
     */
    suspend fun getChildren(parentId: String?): List<Category>

    /**
     * Returns all top-level categories (categories with no parent).
     */
    suspend fun getTopLevel(): List<Category>

    /**
     * Creates a new category.
     *
     * @param category The category to create
     * @return The created category
     * @throws CategoryException if validation fails or parent doesn't exist
     */
    suspend fun create(category: Category): Category

    /**
     * Updates an existing category.
     * Only name, icon, color, and sortOrder can be updated.
     *
     * @param category The category with updated fields
     * @return The updated category
     * @throws CategoryException if category not found or validation fails
     */
    suspend fun update(category: Category): Category

    /**
     * Deletes a category by ID.
     * System default categories cannot be deleted.
     * Categories with children cannot be deleted unless cascade is true.
     *
     * @param id The category ID to delete
     * @param cascade If true, also delete all descendants
     * @throws CategoryException if category is system default or has children without cascade
     */
    suspend fun delete(id: String, cascade: Boolean = false)

    /**
     * Seeds default categories if they don't exist.
     * This should be called on first app launch.
     */
    suspend fun seedDefaults()

    /**
     * Checks if default categories have been seeded.
     */
    suspend fun hasDefaults(): Boolean

    /**
     * Returns the full path from root to the given category.
     */
    suspend fun getPath(categoryId: String): List<Category>

    /**
     * Builds and returns the complete category tree.
     */
    suspend fun getTree(): CategoryTree

    /**
     * Moves a category to a new parent.
     *
     * @param categoryId The category to move
     * @param newParentId The new parent ID, or null to make it top-level
     * @throws CategoryException if move would create circular reference or exceed depth
     */
    suspend fun move(categoryId: String, newParentId: String?)
}

/**
 * Exception thrown for category operations.
 */
class CategoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Result type for category operations that may fail.
 */
sealed class CategoryResult<out T> {
    data class Success<T>(val value: T) : CategoryResult<T>()
    data class Failure(val error: CategoryException) : CategoryResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): CategoryResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): CategoryResult<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (CategoryException) -> Unit): CategoryResult<T> {
        if (this is Failure) action(error)
        return this
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error
    }
}
