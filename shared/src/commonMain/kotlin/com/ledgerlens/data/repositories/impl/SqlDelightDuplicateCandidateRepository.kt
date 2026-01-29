package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.DuplicateCandidateMapper
import com.ledgerlens.data.repositories.DuplicateCandidateEntity
import com.ledgerlens.data.repositories.DuplicateCandidateRepository
import com.ledgerlens.data.repositories.DuplicateCandidateStatus
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class SqlDelightDuplicateCandidateRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : DuplicateCandidateRepository {

    private val queries = database.duplicateCandidateQueries

    override suspend fun insertFingerprintMatches(
        transactionId: String,
        matchedTransactionIds: List<String>,
        fingerprint: String,
        score: Float,
        metadataJson: String
    ) = withContext(dispatcher) {
        if (matchedTransactionIds.isEmpty()) return@withContext

        val now = Clock.System.now().toEpochMilliseconds()
        database.transaction {
            matchedTransactionIds.forEach { otherId ->
                val (a, b) = orderedPair(transactionId, otherId)
                queries.insertOrIgnore(
                    transaction_id_a = a,
                    transaction_id_b = b,
                    fingerprint = fingerprint,
                    score = score.toDouble(),
                    status = "PENDING",
                    created_at = now,
                    reviewed_at = null,
                    metadata_json = metadataJson
                )
            }
        }
    }

    override fun getPending(limit: Int): Flow<List<DuplicateCandidateEntity>> {
        return queries.selectPending(limit.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(DuplicateCandidateMapper::toDomain) }
    }

    override fun getByTransactionId(transactionId: String): Flow<List<DuplicateCandidateEntity>> {
        return queries.selectByTransactionId(transactionId, transactionId)
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(DuplicateCandidateMapper::toDomain) }
    }

    override suspend fun updateStatus(
        transactionIdA: String,
        transactionIdB: String,
        status: DuplicateCandidateStatus
    ): Boolean = withContext(dispatcher) {
        val (a, b) = orderedPair(transactionIdA, transactionIdB)
        val exists = queries.selectByKey(a, b).executeAsOneOrNull() != null
        if (!exists) return@withContext false

        val reviewedAt = Clock.System.now().toEpochMilliseconds()
        val statusStr = when (status) {
            DuplicateCandidateStatus.PENDING -> "PENDING"
            DuplicateCandidateStatus.CONFIRMED -> "CONFIRMED"
            DuplicateCandidateStatus.DISMISSED -> "DISMISSED"
        }
        queries.updateStatus(
            status = statusStr,
            reviewed_at = reviewedAt,
            transaction_id_a = a,
            transaction_id_b = b
        )
        true
    }

    override val pendingCount: Flow<Int>
        get() = queries.countPending()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.toInt() ?: 0 }

    override suspend fun clear() = withContext(dispatcher) {
        queries.clear()
    }

    private fun orderedPair(a: String, b: String): Pair<String, String> {
        return if (a < b) a to b else b to a
    }
}
