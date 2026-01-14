package com.ledgerlens.receipts

/**
 * Repository interface for participant CRUD operations.
 */
interface ParticipantRepository {
    suspend fun getAll(): List<Participant>
    suspend fun getById(id: String): Participant?
    suspend fun getSelf(): Participant
    suspend fun getFavorites(): List<Participant>
    suspend fun getRecent(limit: Int = 10): List<Participant>
    suspend fun search(query: String): List<Participant>
    suspend fun create(participant: Participant): Participant
    suspend fun quickAdd(name: String): Participant
    suspend fun update(participant: Participant): Participant
    suspend fun delete(id: String)
    suspend fun markUsed(id: String)
    suspend fun toggleFavorite(id: String): Participant
    suspend fun updateSelfName(name: String): Participant
    suspend fun getByIds(ids: List<String>): List<Participant>
    suspend fun count(): Int
}

class ParticipantException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed class ParticipantResult<out T> {
    data class Success<T>(val value: T) : ParticipantResult<T>()
    data class Failure(val error: ParticipantException) : ParticipantResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ParticipantResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun getOrNull(): T? = when (this) { is Success -> value; is Failure -> null }
    fun getOrThrow(): T = when (this) { is Success -> value; is Failure -> throw error }
}
