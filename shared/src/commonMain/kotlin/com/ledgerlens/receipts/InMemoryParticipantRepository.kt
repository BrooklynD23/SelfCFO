package com.ledgerlens.receipts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryParticipantRepository : ParticipantRepository {
    private val mutex = Mutex()
    private val participants = mutableMapOf<String, Participant>()
    private var colorIndex = 0

    override suspend fun getAll(): List<Participant> = mutex.withLock {
        participants.values.toList().sortedWith(compareByDescending<Participant> { it.lastUsedAt ?: 0 }.thenBy { it.name })
    }
    override suspend fun getById(id: String): Participant? = mutex.withLock { participants[id] }
    override suspend fun getSelf(): Participant = mutex.withLock { participants.getOrPut(Participant.SELF_ID) { Participant.createSelf() } }
    override suspend fun getFavorites(): List<Participant> = mutex.withLock { participants.values.filter { it.isFavorite }.sortedBy { it.name } }
    override suspend fun getRecent(limit: Int): List<Participant> = mutex.withLock { participants.values.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(limit) }
    override suspend fun search(query: String): List<Participant> = mutex.withLock {
        if (query.isBlank()) emptyList() else participants.values.filter { it.name.lowercase().contains(query.lowercase()) }.sortedBy { it.name }
    }
    override suspend fun create(participant: Participant): Participant = mutex.withLock {
        when (val v = participant.validate()) { is ParticipantValidationResult.Invalid -> throw ParticipantException("Invalid: ${v.reason}"); else -> {} }
        if (participants.containsKey(participant.id)) throw ParticipantException("ID '${participant.id}' exists")
        participants[participant.id] = participant; participant
    }
    override suspend fun quickAdd(name: String): Participant = mutex.withLock {
        val n = name.trim(); if (n.isBlank()) throw ParticipantException("Name cannot be blank")
        val p = Participant.quickAdd(n, colorIndex++); participants[p.id] = p; p
    }
    override suspend fun update(participant: Participant): Participant = mutex.withLock {
        val existing = participants[participant.id] ?: throw ParticipantException("Not found: ${participant.id}")
        when (val v = participant.validate()) { is ParticipantValidationResult.Invalid -> throw ParticipantException("Invalid: ${v.reason}"); else -> {} }
        val updated = participant.copy(isSelf = existing.isSelf, createdAt = existing.createdAt)
        participants[participant.id] = updated; updated
    }
    override suspend fun delete(id: String) = mutex.withLock {
        val p = participants[id] ?: throw ParticipantException("Not found: $id")
        if (p.isSelf) throw ParticipantException("Cannot delete self"); participants.remove(id); Unit
    }
    override suspend fun markUsed(id: String) = mutex.withLock { participants[id]?.let { participants[id] = it.copy(lastUsedAt = System.currentTimeMillis()) }; Unit }
    override suspend fun toggleFavorite(id: String): Participant = mutex.withLock {
        val p = participants[id] ?: throw ParticipantException("Not found: $id")
        val updated = p.copy(isFavorite = !p.isFavorite); participants[id] = updated; updated
    }
    override suspend fun updateSelfName(name: String): Participant = mutex.withLock {
        val n = name.trim(); if (n.isBlank()) throw ParticipantException("Name cannot be blank")
        val self = participants.getOrPut(Participant.SELF_ID) { Participant.createSelf() }
        val updated = self.copy(name = n); participants[Participant.SELF_ID] = updated; updated
    }
    override suspend fun getByIds(ids: List<String>): List<Participant> = mutex.withLock { ids.mapNotNull { participants[it] } }
    override suspend fun count(): Int = mutex.withLock { participants.values.count { !it.isSelf } }
    suspend fun clear() = mutex.withLock { participants.clear(); colorIndex = 0 }
}
