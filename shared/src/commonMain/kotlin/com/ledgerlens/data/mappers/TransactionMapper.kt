package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.Transaction
import com.ledgerlens.db.Transaction_view
import com.ledgerlens.domain.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps database transaction_view to domain Transaction models.
 */
object TransactionMapper {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Convert database Transaction_view to domain Transaction.
     */
    fun toDomain(db: Transaction_view): Transaction {
        val currencyCode = db.currency_code ?: "USD"
        return Transaction(
            id = db.id,
            accountId = db.account_id,
            postedDate = epochMillisToLocalDate(db.posted_date),
            transactionDate = db.transaction_date?.let { epochMillisToLocalDate(it) },
            descriptionRaw = db.description_raw,
            merchantDisplay = db.merchant_display ?: db.merchant_normalized ?: "",
            merchantNormalized = db.merchant_normalized ?: "",
            amount = Money(db.amount_minor_units, currencyCode),
            categoryId = db.category_id,
            categoryConfidence = db.category_confidence?.toFloat(),
            categoryReason = db.category_reason,
            tags = parseTagsJson(db.tags ?: "[]"),
            notes = db.notes ?: "",
            isTransfer = db.is_transfer == true,
            isExcluded = db.is_excluded != 0L,
            isReviewed = db.is_reviewed != 0L,
            hashFingerprint = db.hash_fingerprint,
            importedAt = db.imported_at
        )
    }

    /**
     * Convert epoch milliseconds to LocalDate.
     */
    private fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
        return Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    }

    /**
     * Convert LocalDate to epoch milliseconds (start of day).
     */
    fun localDateToEpochMillis(date: LocalDate): Long {
        return date.toEpochDays() * 24 * 60 * 60 * 1000L
    }

    /**
     * Parse tags JSON array to list of strings.
     */
    private fun parseTagsJson(tagsJson: String): List<String> {
        return try {
            if (tagsJson.isBlank() || tagsJson == "[]") {
                emptyList()
            } else {
                json.parseToJsonElement(tagsJson).jsonArray.map { it.jsonPrimitive.content }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Convert list of tags to JSON array string.
     */
    fun tagsToJson(tags: List<String>): String {
        return if (tags.isEmpty()) {
            "[]"
        } else {
            tags.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        }
    }
}
