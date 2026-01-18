package com.ledgerlens.data.repositories

import kotlinx.coroutines.flow.Flow

/**
 * Domain model for a categorization rule.
 */
data class RuleEntity(
    val id: String,
    val name: String,
    val conditionsJson: String,
    val targetCategoryId: String,
    val priority: Int,
    val isEnabled: Boolean,
    val matchCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Repository interface for rule data access.
 */
interface RuleRepository {
    /**
     * Get all rules ordered by priority.
     */
    fun getAllRules(): Flow<List<RuleEntity>>

    /**
     * Get only enabled rules.
     */
    fun getEnabledRules(): Flow<List<RuleEntity>>

    /**
     * Get a rule by ID.
     */
    fun getRule(id: String): Flow<RuleEntity?>

    /**
     * Insert a new rule.
     */
    suspend fun insertRule(rule: RuleEntity)

    /**
     * Update a rule.
     */
    suspend fun updateRule(rule: RuleEntity)

    /**
     * Delete a rule.
     */
    suspend fun deleteRule(id: String)

    /**
     * Toggle a rule's enabled state.
     */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /**
     * Update rule priority.
     */
    suspend fun updatePriority(id: String, priority: Int)

    /**
     * Increment the match count for a rule.
     */
    suspend fun incrementMatchCount(id: String)

    /**
     * Reorder rules by updating their priorities.
     */
    suspend fun reorderRules(ruleIds: List<String>)
}
