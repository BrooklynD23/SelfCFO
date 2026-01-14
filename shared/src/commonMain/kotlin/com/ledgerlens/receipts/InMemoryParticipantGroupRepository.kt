package com.ledgerlens.receipts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of ParticipantGroupRepository.
 */
class InMemoryParticipantGroupRepository : ParticipantGroupRepository {
    private val mutex = Mutex()
    private val groups = mutableMapOf<String, ParticipantGroup>()

    override suspend fun getAll(): List<ParticipantGroup> = mutex.withLock {
        groups.values.toList().sortedWith(compareByDescending<ParticipantGroup> { it.usageCount }.thenBy { it.name })
    }

    override suspend fun getById(id: String): ParticipantGroup? = mutex.withLock { groups[id] }

    override suspend fun getRecent(limit: Int): List<ParticipantGroup> = mutex.withLock {
        groups.values.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(limit)
    }

    override suspend fun search(query: String): List<ParticipantGroup> = mutex.withLock {
        if (query.isBlank()) return@withLock emptyList()
        val lowerQuery = query.lowercase()
        groups.values.filter { it.name.lowercase().contains(lowerQuery) }.sortedBy { it.name }
    }

    override suspend fun create(group: ParticipantGroup): ParticipantGroup = mutex.withLock {
        when (val v = group.validate()) {
            is GroupValidationResult.Valid -> {}
            is GroupValidationResult.Invalid -> throw GroupException("Invalid group: ${v.reason}")
        }
        if (groups.containsKey(group.id)) throw GroupException("Group with ID '${group.id}' already exists")
        groups[group.id] = group
        group
    }

    override suspend fun createQuick(name: String, participantIds: List<String>): ParticipantGroup = mutex.withLock {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw GroupException("Group name cannot be blank")
        if (participantIds.isEmpty()) throw GroupException("Group must have at least one participant")
        val group = ParticipantGroup.create(trimmedName, participantIds)
        groups[group.id] = group
        group
    }

    override suspend fun update(group: ParticipantGroup): ParticipantGroup = mutex.withLock {
        if (!groups.containsKey(group.id)) throw GroupException("Group '${group.id}' not found")
        when (val v = group.validate()) {
            is GroupValidationResult.Valid -> {}
            is GroupValidationResult.Invalid -> throw GroupException("Invalid group: ${v.reason}")
        }
        groups[group.id] = group
        group
    }

    override suspend fun delete(id: String) = mutex.withLock {
        if (!groups.containsKey(id)) throw GroupException("Group '$id' not found")
        groups.remove(id)
    }

    override suspend fun markUsed(id: String): ParticipantGroup = mutex.withLock {
        val group = groups[id] ?: throw GroupException("Group '$id' not found")
        val updated = group.markUsed()
        groups[id] = updated
        updated
    }

    override suspend fun addParticipant(groupId: String, participantId: String): ParticipantGroup = mutex.withLock {
        val group = groups[groupId] ?: throw GroupException("Group '$groupId' not found")
        if (group.participantIds.size >= ParticipantGroup.MAX_PARTICIPANTS) throw GroupException("Group cannot have more than ${ParticipantGroup.MAX_PARTICIPANTS} participants")
        val updated = group.withParticipant(participantId)
        groups[groupId] = updated
        updated
    }

    override suspend fun removeParticipant(groupId: String, participantId: String): ParticipantGroup = mutex.withLock {
        val group = groups[groupId] ?: throw GroupException("Group '$groupId' not found")
        val updated = group.withoutParticipant(participantId)
        if (updated.isEmpty) throw GroupException("Cannot remove last participant from group")
        groups[groupId] = updated
        updated
    }

    override suspend fun getGroupsContaining(participantId: String): List<ParticipantGroup> = mutex.withLock {
        groups.values.filter { it.contains(participantId) }.sortedBy { it.name }
    }

    override suspend fun count(): Int = mutex.withLock { groups.size }

    suspend fun clear() = mutex.withLock { groups.clear() }
}
