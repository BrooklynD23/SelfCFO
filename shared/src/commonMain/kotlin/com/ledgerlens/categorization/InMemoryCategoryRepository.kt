package com.ledgerlens.categorization

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryCategoryRepository : CategoryRepository {
    private val mutex = Mutex()
    private val categories = mutableMapOf<String, Category>()

    override suspend fun getAll(): List<Category> = mutex.withLock {
        categories.values.toList().sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    override suspend fun getById(id: String): Category? = mutex.withLock { categories[id] }

    override suspend fun getChildren(parentId: String?): List<Category> = mutex.withLock {
        categories.values.filter { it.parentId == parentId }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    override suspend fun getTopLevel(): List<Category> = getChildren(null)

    override suspend fun create(category: Category): Category = mutex.withLock {
        when (val v = category.validate()) {
            is CategoryValidationResult.Valid -> {}
            is CategoryValidationResult.Invalid -> throw CategoryException("Invalid category: ${v.reason}")
        }
        if (categories.containsKey(category.id)) throw CategoryException("Category '${category.id}' already exists")
        category.parentId?.let { if (!categories.containsKey(it)) throw CategoryException("Parent '$it' not found") }
        if (category.parentId != null && calculateDepth(category.parentId) >= Category.MAX_HIERARCHY_DEPTH - 1) {
            throw CategoryException("Would exceed max hierarchy depth")
        }
        categories[category.id] = category
        category
    }

    override suspend fun update(category: Category): Category = mutex.withLock {
        val existing = categories[category.id] ?: throw CategoryException("Category '${category.id}' not found")
        when (val v = category.validate()) {
            is CategoryValidationResult.Valid -> {}
            is CategoryValidationResult.Invalid -> throw CategoryException("Invalid: ${v.reason}")
        }
        val updated = existing.copy(name = category.name, icon = category.icon, color = category.color, sortOrder = category.sortOrder)
        categories[category.id] = updated
        updated
    }

    override suspend fun delete(id: String, cascade: Boolean) = mutex.withLock {
        val cat = categories[id] ?: throw CategoryException("Category '$id' not found")
        if (cat.isSystemDefault) throw CategoryException("Cannot delete system default category")
        val children = categories.values.filter { it.parentId == id }
        if (children.isNotEmpty() && !cascade) throw CategoryException("Has children; use cascade=true")
        if (cascade) deleteRecursive(id) else categories.remove(id)
    }

    private fun deleteRecursive(id: String) {
        categories.values.filter { it.parentId == id }.forEach { deleteRecursive(it.id) }
        categories.remove(id)
    }

    override suspend fun seedDefaults() = mutex.withLock {
        DefaultCategories.all.forEach { if (!categories.containsKey(it.id)) categories[it.id] = it }
    }

    override suspend fun hasDefaults(): Boolean = mutex.withLock { categories.containsKey(Category.UNCATEGORIZED_ID) }

    override suspend fun getPath(categoryId: String): List<Category> = mutex.withLock {
        val path = mutableListOf<Category>()
        var current = categories[categoryId]
        while (current != null) { path.add(0, current); current = current.parentId?.let { categories[it] } }
        path
    }

    override suspend fun getTree(): CategoryTree = CategoryTree.build(getAll())

    override suspend fun move(categoryId: String, newParentId: String?) = mutex.withLock {
        val cat = categories[categoryId] ?: throw CategoryException("Category '$categoryId' not found")
        if (cat.isSystemDefault) throw CategoryException("Cannot move system default category")
        newParentId?.let { if (!categories.containsKey(it)) throw CategoryException("Parent '$it' not found") }
        if (newParentId != null && wouldCreateCircle(categoryId, newParentId)) throw CategoryException("Would create circular reference")
        if (newParentId != null) {
            val newDepth = calculateDepth(newParentId) + 1 + calculateSubtreeDepth(categoryId)
            if (newDepth > Category.MAX_HIERARCHY_DEPTH) throw CategoryException("Would exceed max depth")
        }
        categories[categoryId] = cat.copy(parentId = newParentId)
    }

    private fun calculateDepth(id: String): Int {
        var depth = 0; var c = categories[id]
        while (c?.parentId != null) { depth++; c = categories[c.parentId] }
        return depth
    }

    private fun calculateSubtreeDepth(id: String): Int {
        val children = categories.values.filter { it.parentId == id }
        return if (children.isEmpty()) 0 else 1 + (children.maxOfOrNull { calculateSubtreeDepth(it.id) } ?: 0)
    }

    private fun wouldCreateCircle(categoryId: String, newParentId: String): Boolean {
        var current: String? = newParentId
        while (current != null) { if (current == categoryId) return true; current = categories[current]?.parentId }
        return false
    }

    suspend fun clear() = mutex.withLock { categories.clear() }
    suspend fun count(): Int = mutex.withLock { categories.size }
}
