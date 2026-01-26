package com.ledgerlens.receipts

interface ParticipantGroupRepository {
    suspend fun getAll(): List<ParticipantGroup>
    suspend fun getById(id: String): ParticipantGroup?
    suspend fun getRecent(limit: Int = 5): List<ParticipantGroup>
    suspend fun search(query: String): List<ParticipantGroup>
    suspend fun create(group: ParticipantGroup): ParticipantGroup
    suspend fun createQuick(name: String, participantIds: List<String>): ParticipantGroup
    suspend fun update(group: ParticipantGroup): ParticipantGroup
    suspend fun delete(id: String)
    suspend fun markUsed(id: String): ParticipantGroup
    suspend fun addParticipant(groupId: String, participantId: String): ParticipantGroup
    suspend fun removeParticipant(groupId: String, participantId: String): ParticipantGroup
    suspend fun getGroupsContaining(participantId: String): List<ParticipantGroup>
    suspend fun count(): Int
}

class GroupException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed class GroupResult<out T> {
    data class Success<T>(val value: T) : GroupResult<T>()
    data class Failure(val error: GroupException) : GroupResult<Nothing>()
    inline fun <R> map(transform: (T) -> R): GroupResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error
    }
}
