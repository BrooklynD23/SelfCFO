package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.RuleEntity
import com.ledgerlens.db.Rule

/**
 * Maps database Rule entities to domain RuleEntity models.
 */
object RuleMapper {
    /**
     * Convert database Rule to domain RuleEntity.
     */
    fun toDomain(db: Rule): RuleEntity = RuleEntity(
        id = db.id,
        name = db.match_expression, // Using match_expression as name since no name column
        conditionsJson = buildConditionsJson(db.rule_type, db.match_expression),
        targetCategoryId = db.target_category_id,
        priority = db.priority.toInt(),
        isEnabled = db.enabled == 1L,
        matchCount = 0, // No match_count column in schema
        createdAt = db.created_at,
        updatedAt = db.created_at // No updated_at in schema
    )

    /**
     * Convert domain RuleEntity to database parameters.
     */
    fun toDbParams(entity: RuleEntity): RuleDbParams {
        val (ruleType, matchExpression) = parseConditionsJson(entity.conditionsJson)
        return RuleDbParams(
            id = entity.id,
            ruleType = ruleType,
            matchExpression = matchExpression,
            targetCategoryId = entity.targetCategoryId,
            priority = entity.priority.toLong(),
            createdByUser = 1L,
            enabled = if (entity.isEnabled) 1L else 0L,
            createdAt = entity.createdAt
        )
    }

    /**
     * Build a JSON conditions string from rule type and expression.
     */
    private fun buildConditionsJson(ruleType: String, matchExpression: String): String {
        return """{"type":"$ruleType","value":"$matchExpression"}"""
    }

    /**
     * Parse conditions JSON to extract rule type and expression.
     * Simple parser - assumes format {"type":"...","value":"..."}
     */
    private fun parseConditionsJson(json: String): Pair<String, String> {
        return try {
            val typeRegex = """"type"\s*:\s*"([^"]+)"""".toRegex()
            val valueRegex = """"value"\s*:\s*"([^"]+)"""".toRegex()
            val type = typeRegex.find(json)?.groupValues?.get(1) ?: "merchant_contains"
            val value = valueRegex.find(json)?.groupValues?.get(1) ?: ""
            Pair(type, value)
        } catch (e: Exception) {
            Pair("merchant_contains", json)
        }
    }
}

/**
 * Data class to hold database insert/update parameters.
 */
data class RuleDbParams(
    val id: String,
    val ruleType: String,
    val matchExpression: String,
    val targetCategoryId: String,
    val priority: Long,
    val createdByUser: Long,
    val enabled: Long,
    val createdAt: Long
)
