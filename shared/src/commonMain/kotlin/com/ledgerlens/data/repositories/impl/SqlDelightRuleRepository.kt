package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.RuleMapper
import com.ledgerlens.data.repositories.RuleEntity
import com.ledgerlens.data.repositories.RuleRepository
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight implementation of RuleRepository.
 */
class SqlDelightRuleRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : RuleRepository {

    private val ruleQueries = database.ruleQueries

    override fun getAllRules(): Flow<List<RuleEntity>> {
        return ruleQueries.selectAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { rules -> rules.map(RuleMapper::toDomain) }
    }

    override fun getEnabledRules(): Flow<List<RuleEntity>> {
        return ruleQueries.selectEnabled()
            .asFlow()
            .mapToList(dispatcher)
            .map { rules -> rules.map(RuleMapper::toDomain) }
    }

    override fun getRule(id: String): Flow<RuleEntity?> {
        return ruleQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(RuleMapper::toDomain) }
    }

    override suspend fun insertRule(rule: RuleEntity) = withContext(dispatcher) {
        val params = RuleMapper.toDbParams(rule)
        ruleQueries.insert(
            id = params.id,
            rule_type = params.ruleType,
            match_expression = params.matchExpression,
            target_category_id = params.targetCategoryId,
            priority = params.priority,
            created_by_user = params.createdByUser,
            enabled = params.enabled,
            created_at = params.createdAt
        )
    }

    override suspend fun updateRule(rule: RuleEntity) = withContext(dispatcher) {
        val params = RuleMapper.toDbParams(rule)
        ruleQueries.update(
            rule_type = params.ruleType,
            match_expression = params.matchExpression,
            target_category_id = params.targetCategoryId,
            priority = params.priority,
            enabled = params.enabled,
            id = params.id
        )
    }

    override suspend fun deleteRule(id: String) = withContext(dispatcher) {
        ruleQueries.delete(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = withContext(dispatcher) {
        if (enabled) {
            ruleQueries.enable(id)
        } else {
            ruleQueries.disable(id)
        }
    }

    override suspend fun updatePriority(id: String, priority: Int) = withContext(dispatcher) {
        ruleQueries.updatePriority(priority.toLong(), id)
    }

    override suspend fun incrementMatchCount(id: String) = withContext(dispatcher) {
        // Note: The schema doesn't have a match_count column
        // This is a no-op placeholder for now
        ruleQueries.incrementMatchCount(id)
    }

    override suspend fun reorderRules(ruleIds: List<String>) = withContext(dispatcher) {
        database.transaction {
            ruleIds.forEachIndexed { index, id ->
                ruleQueries.updatePriority((index + 1).toLong(), id)
            }
        }
    }
}
