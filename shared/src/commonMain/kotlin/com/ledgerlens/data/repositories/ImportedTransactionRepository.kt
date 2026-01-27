package com.ledgerlens.data.repositories

import com.ledgerlens.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Domain model for an immutable imported transaction (provenance record).
 */
data class ImportedTransactionEntity(
    val id: String,
    val importJobId: String,
    val sourceFileId: String,
    val sourceRowRef: String,
    val fingerprint: String,
    val accountId: String?,
    val postedDate: LocalDate,
    val transactionDate: LocalDate?,
    val descriptionRaw: String,
    val merchantNormalized: String,
    val merchantId: String?,
    val amount: Money,
    val balanceAfterMinorUnits: Long?,
    val categoryIdAuto: String?,
    val categoryConfidence: Float?,
    val categoryReason: String?,
    val parseWarnings: String?,
    val importedAt: Instant
)

data class ImportedTransactionMatchRef(
    val id: String,
    val sourceFileId: String
)

interface ImportedTransactionRepository {
    suspend fun insert(transaction: ImportedTransactionEntity)
    suspend fun insertBatch(transactions: List<ImportedTransactionEntity>)

    suspend fun findByFingerprint(fingerprint: String): ImportedTransactionEntity?
    suspend fun findMatchRefsByFingerprint(fingerprint: String): List<ImportedTransactionMatchRef>
    suspend fun updateAutoCategory(
        transactionId: String,
        categoryId: String,
        confidence: Float,
        reason: String
    )
    fun getByImportJob(importJobId: String): Flow<List<ImportedTransactionEntity>>
    fun getRecent(limit: Int = 50): Flow<List<ImportedTransactionEntity>>
}

