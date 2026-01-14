package com.ledgerlens.categorization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of CategoryRepository for testing and initial development.
 * This implementation stores categories in memory and provides full CRUD functionality.
 */
class InMemoryCategoryRepository : CategoryRepository {

    private val mutex = Mutex()
    private val categories = mutableMapOf<String, Category>()

    override suspend fun getAll(): List<Category> = mutex.withLock {
        categories.values.toList().sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    override suspend fun getById(id: String): Category? = mutex.withLock {
        categories[id]
    }

    override suspend fun getChildren(parentId: String?): List<Category> = mutex.withLock {
        categories.values
            .filter { it.parentId == parentId }
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    override suspend fun getTopLevel(): List<Category> = getChildren(null)

    override suspend fun create(category: Category): Category = mutex.withLock {
        // Validate category
        when (val validation = category.validate()) {
            is CategoryValidationResult.Valid -> { /* proceed */ }
            is CategoryValidationResult.Invalid -> {
                throw CategoryException("Invalid category: ${validation.reason}")
            }
        }

        // Check for duplicate ID
        if (categories.containsKey(category.id)) {
            throw CategoryException("Category with ID '${category.id}' already exists")
        }

        // Validate parent exists if specified
        category.parentId?.let { parentId ->
            if (!categories.containsKey(parentId)) {
                throw CategoryException("Parent category '$parentId' does not exist")
            }
        }

        // Check depth constraint
        if (category.parentId != null) {
            val depth = calculateDepth(category.parentId)
            if (depth >= Category.MAX_HIERARCHY_DEPTH - 1) {
                throw CategoryException(
                    "Cannot add category: would exceed maximum hierarchy depth of ${Category.MAX_HIERARCHY_DEPTH}"
                )
            }
        }

        categories[category.id] = category
        category
    }

    override suspend fun update(category: Category): Category = mutex.withLock {
        val existing = categories[category.id]
            ?: throw CategoryException("Category '${category.id}' not found")

        // Validate category
        when (val validation = category.validate()) {
            is CategoryValidationResult.Valid -> { /* proceed */ }
            is CategoryValidationResult.Invalid -> {
                throw CategoryException("Invalid category: ${validation.reason}")
            }
        }

        // Only allow updating name, icon, color, sortOrder (preserve parentId, system flags)
        val updated = existing.copy(
            name = category.name,
            icon = category.icon,
            color = category.color,
            sortOrder = category.sortOrder
        )

        categories[category.id] = updated
        updated
    }

    override suspend fun delete(id: String, cascade: Boolean) = mutex.withLock {
        val category = categories[id]
            ?: throw CategoryException("Category '$id' not found")

        if (category.isSystemDefault) {
            throw CategoryException("Cannot delete system default category '${category.name}'")
        }

        val children = categories.values.filter { it.parentId == id }

        if (children.isNotEmpty() && !cascade) {
            throw CategoryException(
                "Cannot delete category '${category.name}': has ${children.size} child categories. " +
                "Use cascade=true to delete children as well."
            )
        }

        if (cascade) {
            // Recursively delete children
            deleteRecursive(id)
        } else {
            categories.remove(id)
        }
    }

    private fun deleteRecursive(id: String) {
        val children = categories.values.filter { it.parentId == id }
        for (child in children) {
            deleteRecursive(child.id)
        }
        categories.remove(id)
    }

    override suspend fun seedDefaults() = mutex.withLock {
        for (category in DefaultCategories.all) {
            if (!categories.containsKey(category.id)) {
                categories[category.id] = category
            }
        }
    }

    override suspend fun hasDefaults(): Boolean = mutex.withLock {
        categories.containsKey(Category.UNCATEGORIZED_ID)
    }

    override suspend fun getPath(categoryId: String): List<Category> = mutex.withLock {
        val path = mutableListOf<Category>()
        var current = categories[categoryId]

        while (current != null) {
            path.add(0, current)
            current = current.parentId?.let { categories[it] }
        }

        path
    }

    override suspend fun getTree(): CategoryTree {
        val allCategories = getAll()
        return CategoryTree.build(allCategories)
    }

    override suspend fun move(categoryId: String, newParentId: String?) = mutex.withLock {
        val category = categories[categoryId]
            ?: throw CategoryException("Category '$categoryId' not found")

        // Cannot move system defaults
        if (category.isSystemDefault) {
            throw CategoryException("Cannot move system default category '${category.name}'")
        }

        // Validate new parent exists
        newParentId?.let { parentId ->
            if (!categories.containsKey(parentId)) {
                throw CategoryException("Target parent category '$parentId' does not exist")
            }
        }

        // Check for circular reference
        if (newParentId != null && wouldCreateCircle(categoryId, newParentId)) {
            throw CategoryException("Cannot move category: would create circular reference")
        }

        // Check depth constraint
        if (newParentId != null) {
            val newParentDepth = calculateDepth(newParentId)
            val subtreeDepth = calculateSubtreeDepth(categoryId)

            if (newParentDepth + 1 + subtreeDepth > Category.MAX_HIERARCHY_DEPTH) {
                throw CategoryException(
                    "Cannot move category: would exceed maximum hierarchy depth of ${Category.MAX_HIERARCHY_DEPTH}"
                )
            }
        }

        categories[categoryId] = category.copy(parentId = newParentId)
    }

    private fun calculateDepth(categoryId: String): Int {
        var depth = 0
        var current = categories[categoryId]
        while (current?.parentId != null) {
            depth++
            current = categories[current.parentId]
        }
        return depth
    }

    private fun calculateSubtreeDepth(categoryId: String): Int {
        val children = categories.values.filter { it.parentId == categoryId }
        if (children.isEmpty()) return 0
        return 1 + (children.maxOfOrNull { calculateSubtreeDepth(it.id) } ?: 0)
    }

    private fun wouldCreateCircle(categoryId: String, newParentId: String): Boolean {
        var current: String? = newParentId
        while (current != null) {
            if (current == categoryId) return true
            current = categories[current]?.parentId
        }
        return false
    }

    /**
     * Clears all categories. Useful for testing.
     */
    suspend fun clear() = mutex.withLock {
        categories.clear()
    }

    /**
     * Returns the count of categories. Useful for testing.
     */
    suspend fun count(): Int = mutex.withLock {
        categories.size
    }
}
