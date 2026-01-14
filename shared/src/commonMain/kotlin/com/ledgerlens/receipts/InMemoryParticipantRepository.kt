package com.ledgerlens.receipts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of ParticipantRepository.
 */
class InMemoryParticipantRepository : ParticipantRepository {
    private val mutex = Mutex()
    private val participants = mutableMapOf<String, Participant>()
    private var colorIndex = 0

    override suspend fun getAll(): List<Participant> = mutex.withLock {
        participants.values.toList().sortedWith(compareByDescending<Participant> { it.lastUsedAt ?: 0 }.thenBy { it.name })
    }

    override suspend fun getById(id: String): Participant? = mutex.withLock { participants[id] }

    override suspend fun getSelf(): Participant = mutex.withLock {
        participants.getOrPut(Participant.SELF_ID) { Participant.createSelf() }
    }

    override suspend fun getFavorites(): List<Participant> = mutex.withLock {
        participants.values.filter { it.isFavorite }.sortedBy { it.name }
    }

    override suspend fun getRecent(limit: Int): List<Participant> = mutex.withLock {
        participants.values.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(limit)
    }

    override suspend fun search(query: String): List<Participant> = mutex.withLock {
        if (query.isBlank()) return@withLock emptyList()
        val lowerQuery = query.lowercase()
        participants.values.filter { it.name.lowercase().contains(lowerQuery) }.sortedBy { it.name }
    }

    override suspend fun create(participant: Participant): Participant = mutex.withLock {
        when (val v = participant.validate()) {
            is ParticipantValidationResult.Valid -> {}
            is ParticipantValidationResult.Invalid -> throw ParticipantException("Invalid participant: ${v.reason}")
        }
        if (participants.containsKey(participant.id)) throw ParticipantException("Participant with ID '${participant.id}' already exists")
        participants[participant.id] = participant
        participant
    }

    override suspend fun quickAdd(name: String): Participant = mutex.withLock {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw ParticipantException("Participant name cannot be blank")
        val participant = Participant.quickAdd(trimmedName, colorIndex++)
        participants[participant.id] = participant
        participant
    }

    override suspend fun update(participant: Participant): Participant = mutex.withLock {
        val existing = participants[participant.id] ?: throw ParticipantException("Participant '${participant.id}' not found")
        when (val v = participant.validate()) {
            is ParticipantValidationResult.Valid -> {}
            is ParticipantValidationResult.Invalid -> throw ParticipantException("Invalid participant: ${v.reason}")
        }
        val updated = participant.copy(isSelf = existing.isSelf, createdAt = existing.createdAt)
        participants[participant.id] = updated
        updated
    }

    override suspend fun delete(id: String) = mutex.withLock {
        val participant = participants[id] ?: throw ParticipantException("Participant '$id' not found")
        if (participant.isSelf) throw ParticipantException("Cannot delete the 'self' participant")
        participants.remove(id)
    }

    override suspend fun markUsed(id: String) = mutex.withLock {
        val participant = participants[id] ?: return@withLock
        participants[id] = participant.copy(lastUsedAt = System.currentTimeMillis())
    }

    override suspend fun toggleFavorite(id: String): Participant = mutex.withLock {
        val participant = participants[id] ?: throw ParticipantException("Participant '$id' not found")
        val updated = participant.copy(isFavorite = !participant.isFavorite)
        participants[id] = updated
        updated
    }

    override suspend fun updateSelfName(name: String): Participant = mutex.withLock {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw ParticipantException("Self name cannot be blank")
        val self = participants.getOrPut(Participant.SELF_ID) { Participant.createSelf() }
        val updated = self.copy(name = trimmedName)
        participants[Participant.SELF_ID] = updated
        updated
    }

    override suspend fun getByIds(ids: List<String>): List<Participant> = mutex.withLock {
        ids.mapNotNull { participants[it] }
    }

    override suspend fun count(): Int = mutex.withLock { participants.values.count { !it.isSelf } }

    suspend fun clear() = mutex.withLock { participants.clear(); colorIndex = 0 }
    suspend fun seed(selfName: String = Participant.SELF_DEFAULT_NAME) = mutex.withLock {
        if (!participants.containsKey(Participant.SELF_ID)) participants[Participant.SELF_ID] = Participant.createSelf(selfName)
    }
}
