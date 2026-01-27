package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.ImportedTransactionEntity
import com.ledgerlens.db.Imported_transaction
import com.ledgerlens.domain.Money
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object ImportedTransactionMapper {
    fun toDomain(db: Imported_transaction): ImportedTransactionEntity {
        val currency = db.currency_code
        return ImportedTransactionEntity(
            id = db.id,
            importJobId = db.import_job_id,
            sourceFileId = db.source_file_id,
            sourceRowRef = db.source_row_ref,
            fingerprint = db.fingerprint,
            accountId = db.account_id,
            postedDate = epochMillisToLocalDate(db.posted_date),
            transactionDate = db.transaction_date?.let { epochMillisToLocalDate(it) },
            descriptionRaw = db.description_raw,
            merchantNormalized = db.merchant_normalized,
            merchantId = db.merchant_id,
            amount = Money.fromMinorUnits(db.amount_minor_units, currency),
            balanceAfterMinorUnits = db.balance_after,
            categoryIdAuto = db.category_id_auto,
            categoryConfidence = db.category_confidence?.toFloat(),
            categoryReason = db.category_reason,
            parseWarnings = db.parse_warnings,
            importedAt = Instant.fromEpochMilliseconds(db.imported_at)
        )
    }

    fun localDateToEpochMillis(date: LocalDate): Long = TransactionMapper.localDateToEpochMillis(date)

    private fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
        // We store LocalDate as epochDays * millisPerDay (see TransactionMapper.localDateToEpochMillis)
        return Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.UTC)
            .date
    }

    fun now(): Instant = Clock.System.now()
}

