package com.ledgerlens.categorization.rules

import kotlinx.datetime.Clock

interface RuleRepository {
    suspend fun getAll(): List<CategoryRule>
    suspend fun getEnabled(): List<CategoryRule>
    suspend fun getById(id: String): CategoryRule?
    suspend fun getBySource(source: RuleSource): List<CategoryRule>
    suspend fun getByCategoryId(categoryId: String): List<CategoryRule>
    suspend fun insert(rule: CategoryRule)
    suspend fun update(rule: CategoryRule)
    suspend fun upsert(rule: CategoryRule)
    suspend fun delete(id: String): Boolean
    suspend fun deleteBySource(source: RuleSource): Int
    suspend fun recordMatch(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun setPriority(id: String, priority: Int)
    suspend fun count(): Int
    suspend fun deleteAll()
}

class RuleAlreadyExistsException(id: String) : Exception("Rule with ID '$id' already exists")
class RuleNotFoundException(id: String) : Exception("Rule with ID '$id' not found")

class InMemoryRuleRepository : RuleRepository {
    private val rules = mutableMapOf<String, CategoryRule>()
    override suspend fun getAll() = rules.values.sortedByDescending { it.priority }
    override suspend fun getEnabled() = rules.values.filter { it.enabled }.sortedByDescending { it.priority }
    override suspend fun getById(id: String) = rules[id]
    override suspend fun getBySource(source: RuleSource) = rules.values.filter { it.source == source }.sortedByDescending { it.priority }
    override suspend fun getByCategoryId(categoryId: String) = rules.values.filter { it.categoryId == categoryId }.sortedByDescending { it.priority }
    override suspend fun insert(rule: CategoryRule) { if (rules.containsKey(rule.id)) throw RuleAlreadyExistsException(rule.id); rules[rule.id] = rule }
    override suspend fun update(rule: CategoryRule) { if (!rules.containsKey(rule.id)) throw RuleNotFoundException(rule.id); rules[rule.id] = rule.withUpdate() }
    override suspend fun upsert(rule: CategoryRule) { rules[rule.id] = if (rules.containsKey(rule.id)) rule.withUpdate() else rule }
    override suspend fun delete(id: String) = rules.remove(id) != null
    override suspend fun deleteBySource(source: RuleSource): Int { val toDelete = rules.values.filter { it.source == source }.map { it.id }; toDelete.forEach { rules.remove(it) }; return toDelete.size }
    override suspend fun recordMatch(id: String) { rules[id]?.let { rules[id] = it.withMatch() } }
    override suspend fun setEnabled(id: String, enabled: Boolean) { rules[id]?.let { rules[id] = it.copy(enabled = enabled, updatedAt = Clock.System.now()) } }
    override suspend fun setPriority(id: String, priority: Int) { rules[id]?.let { rules[id] = it.copy(priority = priority, updatedAt = Clock.System.now()) } }
    override suspend fun count() = rules.size
    override suspend fun deleteAll() = rules.clear()
    suspend fun insertAll(ruleList: List<CategoryRule>) = ruleList.forEach { upsert(it) }
}
