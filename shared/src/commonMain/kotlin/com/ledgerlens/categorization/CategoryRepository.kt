package com.ledgerlens.categorization

interface CategoryRepository {
    suspend fun getAll(): List<Category>
    suspend fun getById(id: String): Category?
    suspend fun getChildren(parentId: String?): List<Category>
    suspend fun getTopLevel(): List<Category>
    suspend fun create(category: Category): Category
    suspend fun update(category: Category): Category
    suspend fun delete(id: String, cascade: Boolean = false)
    suspend fun seedDefaults()
    suspend fun hasDefaults(): Boolean
    suspend fun getPath(categoryId: String): List<Category>
    suspend fun getTree(): CategoryTree
    suspend fun move(categoryId: String, newParentId: String?)
}

class CategoryException(message: String) : Exception(message)
