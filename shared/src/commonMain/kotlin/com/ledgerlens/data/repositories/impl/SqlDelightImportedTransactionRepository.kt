package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ledgerlens.data.mappers.ImportedTransactionMapper
import com.ledgerlens.data.repositories.ImportedTransactionEntity
import com.ledgerlens.data.repositories.ImportedTransactionMatchRef
import com.ledgerlens.data.repositories.ImportedTransactionRepository
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightImportedTransactionRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ImportedTransactionRepository {

    private val queries = database.importedTransactionQueries

    override suspend fun insert(transaction: ImportedTransactionEntity) = withContext(dispatcher) {
        insertInternal(transaction)
    }

    override suspend fun insertBatch(transactions: List<ImportedTransactionEntity>) = withContext(dispatcher) {
        database.transaction {
            transactions.forEach { insertInternal(it) }
        }
    }

    override suspend fun findByFingerprint(fingerprint: String): ImportedTransactionEntity? = withContext(dispatcher) {
        queries.selectByFingerprint(fingerprint)
            .executeAsOneOrNull()
            ?.let(ImportedTransactionMapper::toDomain)
    }

    override suspend fun findMatchRefsByFingerprint(fingerprint: String): List<ImportedTransactionMatchRef> =
        withContext(dispatcher) {
            queries.selectMatchRefsByFingerprint(fingerprint)
                .executeAsList()
                .map { row -> ImportedTransactionMatchRef(id = row.id, sourceFileId = row.source_file_id) }
        }

    override suspend fun updateAutoCategory(
        transactionId: String,
        categoryId: String,
        confidence: Float,
        reason: String
    ) = withContext(dispatcher) {
        queries.updateCategory(
            category_id_auto = categoryId,
            category_confidence = confidence.toDouble(),
            category_reason = reason,
            id = transactionId
        )
    }

    override fun getByImportJob(importJobId: String): Flow<List<ImportedTransactionEntity>> {
        return queries.selectByImportJob(importJobId)
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(ImportedTransactionMapper::toDomain) }
    }

    override fun getRecent(limit: Int): Flow<List<ImportedTransactionEntity>> {
        return queries.selectRecent(limit.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { list -> list.map(ImportedTransactionMapper::toDomain) }
    }

    private fun insertInternal(transaction: ImportedTransactionEntity) {
        queries.insert(
            id = transaction.id,
            import_job_id = transaction.importJobId,
            source_file_id = transaction.sourceFileId,
            source_row_ref = transaction.sourceRowRef,
            fingerprint = transaction.fingerprint,
            account_id = transaction.accountId,
            posted_date = ImportedTransactionMapper.localDateToEpochMillis(transaction.postedDate),
            transaction_date = transaction.transactionDate?.let(ImportedTransactionMapper::localDateToEpochMillis),
            description_raw = transaction.descriptionRaw,
            merchant_normalized = transaction.merchantNormalized,
            merchant_id = transaction.merchantId,
            amount_minor_units = transaction.amount.minorUnits,
            currency_code = transaction.amount.currencyCode,
            balance_after = transaction.balanceAfterMinorUnits,
            category_id_auto = transaction.categoryIdAuto,
            category_confidence = transaction.categoryConfidence?.toDouble(),
            category_reason = transaction.categoryReason,
            parse_warnings = transaction.parseWarnings,
            imported_at = transaction.importedAt.toEpochMilliseconds()
        )
    }
}
