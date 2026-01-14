package com.ledgerlens.categorization.rules

import kotlinx.datetime.Clock

/**
 * Repository interface for managing CategoryRule persistence.
 *
 * Implementations may use SQLDelight, in-memory storage, or other backends.
 */
interface RuleRepository {
    /**
     * Retrieves all rules.
     */
    suspend fun getAll(): List<CategoryRule>

    /**
     * Retrieves all enabled rules, sorted by priority (descending).
     */
    suspend fun getEnabled(): List<CategoryRule>

    /**
     * Retrieves a rule by its ID.
     */
    suspend fun getById(id: String): CategoryRule?

    /**
     * Retrieves rules by source type.
     */
    suspend fun getBySource(source: RuleSource): List<CategoryRule>

    /**
     * Retrieves rules that set a specific category.
     */
    suspend fun getByCategoryId(categoryId: String): List<CategoryRule>

    /**
     * Saves a new rule.
     *
     * @throws RuleAlreadyExistsException if a rule with the same ID exists
     */
    suspend fun insert(rule: CategoryRule)

    /**
     * Updates an existing rule.
     *
     * @throws RuleNotFoundException if the rule doesn't exist
     */
    suspend fun update(rule: CategoryRule)

    /**
     * Saves a rule (insert or update).
     */
    suspend fun upsert(rule: CategoryRule)

    /**
     * Deletes a rule by ID.
     *
     * @return true if a rule was deleted
     */
    suspend fun delete(id: String): Boolean

    /**
     * Deletes all rules with the given source.
     *
     * @return number of rules deleted
     */
    suspend fun deleteBySource(source: RuleSource): Int

    /**
     * Updates the match statistics for a rule.
     */
    suspend fun recordMatch(id: String)

    /**
     * Enables or disables a rule.
     */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /**
     * Updates the priority of a rule.
     */
    suspend fun setPriority(id: String, priority: Int)

    /**
     * Returns the count of all rules.
     */
    suspend fun count(): Int

    /**
     * Deletes all rules (use with caution).
     */
    suspend fun deleteAll()
}

/**
 * Exception thrown when attempting to insert a rule that already exists.
 */
class RuleAlreadyExistsException(id: String) : Exception("Rule with ID '$id' already exists")

/**
 * Exception thrown when attempting to update a rule that doesn't exist.
 */
class RuleNotFoundException(id: String) : Exception("Rule with ID '$id' not found")

/**
 * In-memory implementation of RuleRepository for testing and development.
 */
class InMemoryRuleRepository : RuleRepository {
    private val rules = mutableMapOf<String, CategoryRule>()

    override suspend fun getAll(): List<CategoryRule> {
        return rules.values.toList().sortedByDescending { it.priority }
    }

    override suspend fun getEnabled(): List<CategoryRule> {
        return rules.values
            .filter { it.enabled }
            .sortedByDescending { it.priority }
    }

    override suspend fun getById(id: String): CategoryRule? {
        return rules[id]
    }

    override suspend fun getBySource(source: RuleSource): List<CategoryRule> {
        return rules.values
            .filter { it.source == source }
            .sortedByDescending { it.priority }
    }

    override suspend fun getByCategoryId(categoryId: String): List<CategoryRule> {
        return rules.values
            .filter { it.categoryId == categoryId }
            .sortedByDescending { it.priority }
    }

    override suspend fun insert(rule: CategoryRule) {
        if (rules.containsKey(rule.id)) {
            throw RuleAlreadyExistsException(rule.id)
        }
        rules[rule.id] = rule
    }

    override suspend fun update(rule: CategoryRule) {
        if (!rules.containsKey(rule.id)) {
            throw RuleNotFoundException(rule.id)
        }
        rules[rule.id] = rule.withUpdate()
    }

    override suspend fun upsert(rule: CategoryRule) {
        val existingRule = rules[rule.id]
        rules[rule.id] = if (existingRule != null) {
            rule.withUpdate()
        } else {
            rule
        }
    }

    override suspend fun delete(id: String): Boolean {
        return rules.remove(id) != null
    }

    override suspend fun deleteBySource(source: RuleSource): Int {
        val toDelete = rules.values.filter { it.source == source }.map { it.id }
        toDelete.forEach { rules.remove(it) }
        return toDelete.size
    }

    override suspend fun recordMatch(id: String) {
        rules[id]?.let { rule ->
            rules[id] = rule.withMatch()
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        rules[id]?.let { rule ->
            rules[id] = rule.copy(enabled = enabled, updatedAt = Clock.System.now())
        }
    }

    override suspend fun setPriority(id: String, priority: Int) {
        rules[id]?.let { rule ->
            rules[id] = rule.copy(priority = priority, updatedAt = Clock.System.now())
        }
    }

    override suspend fun count(): Int = rules.size

    override suspend fun deleteAll() {
        rules.clear()
    }

    /**
     * Bulk insert for testing.
     */
    suspend fun insertAll(ruleList: List<CategoryRule>) {
        ruleList.forEach { upsert(it) }
    }
}
