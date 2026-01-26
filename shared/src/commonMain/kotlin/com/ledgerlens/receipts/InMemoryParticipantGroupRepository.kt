package com.ledgerlens.receipts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        if (query.isBlank()) {
            emptyList()
        } else {
            groups.values.filter {
                it.name.lowercase().contains(query.lowercase())
            }.sortedBy { it.name }
        }
    }
    override suspend fun create(group: ParticipantGroup): ParticipantGroup = mutex.withLock {
        when (val v = group.validate()) {
            is GroupValidationResult.Invalid -> throw GroupException("Invalid: ${v.reason}")
            else -> {}
        }
        if (groups.containsKey(group.id)) throw GroupException("ID '${group.id}' exists")
        groups[group.id] = group
        group
    }
    override suspend fun createQuick(name: String, participantIds: List<String>): ParticipantGroup = mutex.withLock {
        val n = name.trim()
        if (n.isBlank()) throw GroupException("Name cannot be blank")
        if (participantIds.isEmpty()) throw GroupException("Must have at least one participant")
        val g = ParticipantGroup.create(n, participantIds)
        groups[g.id] = g
        g
    }
    override suspend fun update(group: ParticipantGroup): ParticipantGroup = mutex.withLock {
        if (!groups.containsKey(group.id)) throw GroupException("Not found: ${group.id}")
        when (val v = group.validate()) {
            is GroupValidationResult.Invalid -> throw GroupException("Invalid: ${v.reason}")
            else -> {}
        }
        groups[group.id] = group
        group
    }
    override suspend fun delete(id: String) = mutex.withLock {
        if (!groups.containsKey(id)) throw GroupException("Not found: $id")
        groups.remove(id)
        Unit
    }
    override suspend fun markUsed(id: String): ParticipantGroup = mutex.withLock {
        val g = groups[id] ?: throw GroupException("Not found: $id")
        val updated = g.markUsed()
        groups[id] = updated
        updated
    }
    override suspend fun addParticipant(groupId: String, participantId: String): ParticipantGroup = mutex.withLock {
        val g = groups[groupId] ?: throw GroupException("Not found: $groupId")
        if (g.participantIds.size >= ParticipantGroup.MAX_PARTICIPANTS) throw GroupException("Max participants exceeded")
        val updated = g.withParticipant(participantId)
        groups[groupId] = updated
        updated
    }
    override suspend fun removeParticipant(groupId: String, participantId: String): ParticipantGroup = mutex.withLock {
        val g = groups[groupId] ?: throw GroupException("Not found: $groupId")
        val updated = g.withoutParticipant(participantId)
        if (updated.isEmpty) throw GroupException("Cannot remove last participant")
        groups[groupId] = updated
        updated
    }
    override suspend fun getGroupsContaining(participantId: String): List<ParticipantGroup> =
        mutex.withLock { groups.values.filter { it.contains(participantId) }.sortedBy { it.name } }
    override suspend fun count(): Int = mutex.withLock { groups.size }
    suspend fun clear() = mutex.withLock { groups.clear() }
}
