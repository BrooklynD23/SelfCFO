package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.CategoryMapper
import com.ledgerlens.data.repositories.CategoryEntity
import com.ledgerlens.data.repositories.CategoryRepository
import com.ledgerlens.data.repositories.CategoryWithStats
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight implementation of CategoryRepository.
 */
class SqlDelightCategoryRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CategoryRepository {

    private val categoryQueries = database.categoryQueries
    private val viewsQueries = database.viewsQueries

    override fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryQueries.selectAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { categories -> categories.map(CategoryMapper::toDomain) }
    }

    override fun getCategory(id: String): Flow<CategoryEntity?> {
        return categoryQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(CategoryMapper::toDomain) }
    }

    override fun getCategoriesByParent(parentId: String): Flow<List<CategoryEntity>> {
        return categoryQueries.selectByParent(parentId)
            .asFlow()
            .mapToList(dispatcher)
            .map { categories -> categories.map(CategoryMapper::toDomain) }
    }

    override fun getTopLevelCategories(): Flow<List<CategoryEntity>> {
        return categoryQueries.selectTopLevel()
            .asFlow()
            .mapToList(dispatcher)
            .map { categories -> categories.map(CategoryMapper::toDomain) }
    }

    override fun getCategoriesWithStats(): Flow<List<CategoryWithStats>> {
        return viewsQueries.selectCategorySummary()
            .asFlow()
            .mapToList(dispatcher)
            .map { summaries -> summaries.map(CategoryMapper::toCategoryWithStats) }
    }

    override suspend fun insertCategory(category: CategoryEntity) = withContext(dispatcher) {
        val params = CategoryMapper.toDbParams(category)
        categoryQueries.insert(
            id = params.id,
            name = params.name,
            parent_id = params.parentId,
            system_default = params.systemDefault,
            user_custom = params.userCustom,
            icon = params.icon,
            color = params.color,
            sort_order = params.sortOrder
        )
    }

    override suspend fun updateCategory(category: CategoryEntity) = withContext(dispatcher) {
        val params = CategoryMapper.toDbParams(category)
        categoryQueries.update(
            name = params.name,
            icon = params.icon,
            color = params.color,
            sort_order = params.sortOrder,
            id = params.id
        )
    }

    override suspend fun deleteCategory(id: String) = withContext(dispatcher) {
        categoryQueries.delete(id)
    }

    override suspend fun seedDefaultCategories() = withContext(dispatcher) {
        categoryQueries.insertDefaults()
    }

    override suspend fun exists(id: String): Boolean = withContext(dispatcher) {
        categoryQueries.exists(id).executeAsOne()
    }
}
