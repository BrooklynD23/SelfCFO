package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.DuplicateCandidateEntity
import com.ledgerlens.data.repositories.DuplicateCandidateStatus
import com.ledgerlens.db.Duplicate_candidate
import kotlinx.datetime.Instant

object DuplicateCandidateMapper {
    fun toDomain(db: Duplicate_candidate): DuplicateCandidateEntity {
        val status = when (db.status) {
            "PENDING" -> DuplicateCandidateStatus.PENDING
            "CONFIRMED" -> DuplicateCandidateStatus.CONFIRMED
            "DISMISSED" -> DuplicateCandidateStatus.DISMISSED
            else -> DuplicateCandidateStatus.PENDING
        }

        return DuplicateCandidateEntity(
            transactionIdA = db.transaction_id_a,
            transactionIdB = db.transaction_id_b,
            fingerprint = db.fingerprint,
            score = db.score.toFloat(),
            status = status,
            createdAt = Instant.fromEpochMilliseconds(db.created_at),
            reviewedAt = db.reviewed_at?.let { Instant.fromEpochMilliseconds(it) },
            metadataJson = db.metadata_json
        )
    }
}
