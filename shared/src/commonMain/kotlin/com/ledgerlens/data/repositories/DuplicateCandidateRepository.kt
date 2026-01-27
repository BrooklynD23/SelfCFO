package com.ledgerlens.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

data class DuplicateCandidateEntity(
    val transactionIdA: String,
    val transactionIdB: String,
    val fingerprint: String,
    val score: Float,
    val status: DuplicateCandidateStatus,
    val createdAt: Instant,
    val reviewedAt: Instant?,
    val metadataJson: String = "{}"
)

enum class DuplicateCandidateStatus {
    PENDING,
    CONFIRMED,
    DISMISSED
}

interface DuplicateCandidateRepository {
    suspend fun insertFingerprintMatches(
        transactionId: String,
        matchedTransactionIds: List<String>,
        fingerprint: String,
        score: Float = 0.9f,
        metadataJson: String = "{}"
    )

    fun getPending(limit: Int = 100): Flow<List<DuplicateCandidateEntity>>
    fun getByTransactionId(transactionId: String): Flow<List<DuplicateCandidateEntity>>
    suspend fun updateStatus(
        transactionIdA: String,
        transactionIdB: String,
        status: DuplicateCandidateStatus
    ): Boolean

    val pendingCount: Flow<Int>
    suspend fun clear()
}

