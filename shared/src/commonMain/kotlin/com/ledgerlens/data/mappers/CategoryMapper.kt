package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.CategoryEntity
import com.ledgerlens.data.repositories.CategoryWithStats
import com.ledgerlens.db.Category
import com.ledgerlens.db.Category_summary

/**
 * Maps database Category entities to domain CategoryEntity models.
 */
object CategoryMapper {
    /**
     * Convert database Category to domain CategoryEntity.
     */
    fun toDomain(db: Category): CategoryEntity = CategoryEntity(
        id = db.id,
        name = db.name,
        parentId = db.parent_id,
        isSystemDefault = db.system_default == 1L,
        isUserCustom = db.user_custom == 1L,
        icon = db.icon,
        color = db.color,
        sortOrder = db.sort_order.toInt()
    )

    /**
     * Convert domain CategoryEntity to database parameters.
     */
    fun toDbParams(entity: CategoryEntity): CategoryDbParams = CategoryDbParams(
        id = entity.id,
        name = entity.name,
        parentId = entity.parentId,
        systemDefault = if (entity.isSystemDefault) 1L else 0L,
        userCustom = if (entity.isUserCustom) 1L else 0L,
        icon = entity.icon,
        color = entity.color,
        sortOrder = entity.sortOrder.toLong()
    )

    /**
     * Convert category_summary view to CategoryWithStats.
     */
    fun toCategoryWithStats(summary: Category_summary): CategoryWithStats {
        val category = CategoryEntity(
            id = summary.category_id,
            name = summary.category_name,
            parentId = summary.parent_id,
            isSystemDefault = false, // Not available in summary view
            isUserCustom = false,
            icon = summary.icon,
            color = summary.color,
            sortOrder = 0
        )
        return CategoryWithStats(
            category = category,
            transactionCount = summary.transaction_count?.toInt() ?: 0,
            totalSpentMinorUnits = (summary.total_spent_minor as? Long) ?: (summary.total_spent_minor?.toLong() ?: 0L),
            totalIncomeMinorUnits = (summary.total_income_minor as? Long) ?: (summary.total_income_minor?.toLong() ?: 0L)
        )
    }
}

/**
 * Data class to hold database insert/update parameters.
 */
data class CategoryDbParams(
    val id: String,
    val name: String,
    val parentId: String?,
    val systemDefault: Long,
    val userCustom: Long,
    val icon: String?,
    val color: String?,
    val sortOrder: Long
)
