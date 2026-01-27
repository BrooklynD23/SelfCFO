package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlin.math.absoluteValue

/**
 * Detects duplicate transactions using fingerprints and fuzzy matching.
 * 
 * Duplicate detection strategy:
 * 1. Exact fingerprint match → definite duplicate
 * 2. Same merchant + amount within ±2 day window → possible duplicate
 * 
 * Per 02a-data-model-addendum.md §4
 */
class DuplicateDetector(
    private val fingerprint: TransactionFingerprint = TransactionFingerprint(),
    private val dateWindowDays: Int = DEFAULT_DATE_WINDOW_DAYS
) {

    companion object {
        const val DEFAULT_DATE_WINDOW_DAYS = 2
        const val HIGH_SIMILARITY_THRESHOLD = 0.85f
        const val MEDIUM_SIMILARITY_THRESHOLD = 0.70f
    }

    /**
     * Check if a transaction is a duplicate against a set of existing transactions.
     *
     * @param transaction The new transaction to check
     * @param accountId The account scope for fingerprinting
     * @param existingTransactions Existing transactions to check against
     * @return DuplicateCheckResult indicating if duplicate, possible duplicate, or unique
     */
    fun checkDuplicate(
        transaction: ParsedTransaction,
        accountId: String,
        existingTransactions: List<ExistingTransaction>
    ): DuplicateCheckResult {
        val txFingerprint = fingerprint.generate(
            accountId = accountId,
            postedDate = transaction.postedDate,
            amount = transaction.amount,
            rawDescription = transaction.rawDescription
        )

        // Check exact fingerprint match
        val exactMatch = existingTransactions.find { it.fingerprint == txFingerprint }
        if (exactMatch != null) {
            return DuplicateCheckResult.ExactDuplicate(
                existingId = exactMatch.id,
                fingerprint = txFingerprint
            )
        }

        // Check near matches within date window
        val dateFrom = transaction.postedDate.minus(dateWindowDays, DateTimeUnit.DAY)
        val dateTo = transaction.postedDate.plus(dateWindowDays, DateTimeUnit.DAY)

        val nearMatches = existingTransactions.filter { existing ->
            existing.postedDate >= dateFrom &&
            existing.postedDate <= dateTo &&
            existing.amountMinorUnits == transaction.amount.minorUnits.absoluteValue &&
            existing.currencyCode == transaction.amount.currencyCode
        }

        if (nearMatches.isNotEmpty()) {
            val candidates = nearMatches.map { existing ->
                val similarity = calculateSimilarity(transaction, existing)
                val dateDistance = daysBetween(transaction.postedDate, existing.postedDate)
                DuplicateCandidate(
                    existingId = existing.id,
                    similarity = similarity,
                    dateDistance = dateDistance
                )
            }.sortedByDescending { it.similarity }

            // If any candidate has very high similarity, treat as likely duplicate
            val highConfidenceMatch = candidates.find { it.similarity >= HIGH_SIMILARITY_THRESHOLD }
            if (highConfidenceMatch != null) {
                return DuplicateCheckResult.PossibleDuplicate(
                    candidates = candidates,
                    fingerprint = txFingerprint,
                    confidence = DuplicateConfidence.HIGH
                )
            }

            return DuplicateCheckResult.PossibleDuplicate(
                candidates = candidates,
                fingerprint = txFingerprint,
                confidence = if (candidates.any { it.similarity >= MEDIUM_SIMILARITY_THRESHOLD })
                    DuplicateConfidence.MEDIUM else DuplicateConfidence.LOW
            )
        }

        return DuplicateCheckResult.Unique(fingerprint = txFingerprint)
    }

    /**
     * Batch check multiple transactions for duplicates.
     */
    fun checkBatch(
        transactions: List<ParsedTransaction>,
        accountId: String,
        existingTransactions: List<ExistingTransaction>
    ): List<Pair<ParsedTransaction, DuplicateCheckResult>> {
        return transactions.map { tx ->
            tx to checkDuplicate(tx, accountId, existingTransactions)
        }
    }

    /**
     * Calculate similarity score between new and existing transaction.
     * Returns value between 0.0 (no match) and 1.0 (identical).
     */
    private fun calculateSimilarity(
        new: ParsedTransaction,
        existing: ExistingTransaction
    ): Float {
        var score = 0f

        // Amount match (40% weight) - must be exact for any similarity
        if (new.amount.minorUnits.absoluteValue == existing.amountMinorUnits) {
            score += 0.4f
        } else {
            return 0f  // Different amounts = no match
        }

        // Merchant similarity (40% weight)
        val merchantSimilarity = stringSimilarity(
            new.merchantNormalized.lowercase(),
            existing.merchantNormalized.lowercase()
        )
        score += merchantSimilarity * 0.4f

        // Date proximity (20% weight)
        val daysDiff = daysBetween(new.postedDate, existing.postedDate)
        score += when (daysDiff) {
            0 -> 0.20f
            1 -> 0.15f
            2 -> 0.10f
            else -> 0f
        }

        return score
    }

    /**
     * Calculate string similarity using Levenshtein distance ratio.
     */
    private fun stringSimilarity(a: String, b: String): Float {
        if (a == b) return 1.0f
        val longer = maxOf(a.length, b.length)
        if (longer == 0) return 1.0f
        val distance = levenshteinDistance(a, b)
        return (longer - distance).toFloat() / longer
    }

    /**
     * Compute Levenshtein edit distance between two strings.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[a.length][b.length]
    }

    /**
     * Calculate absolute days between two dates.
     */
    private fun daysBetween(a: LocalDate, b: LocalDate): Int {
        val aEpoch = a.toEpochDays()
        val bEpoch = b.toEpochDays()
        return (aEpoch - bEpoch).absoluteValue.toInt()
    }
}

/**
 * Represents an existing transaction for duplicate checking.
 */
data class ExistingTransaction(
    val id: String,
    val fingerprint: String,
    val merchantNormalized: String,
    val postedDate: LocalDate,
    val amountMinorUnits: Long,
    val currencyCode: String
)

/**
 * Result of duplicate detection check.
 */
sealed class DuplicateCheckResult {
    abstract val fingerprint: String

    /**
     * Transaction is unique - no duplicates found.
     */
    data class Unique(
        override val fingerprint: String
    ) : DuplicateCheckResult()

    /**
     * Exact fingerprint match found - definite duplicate.
     */
    data class ExactDuplicate(
        val existingId: String,
        override val fingerprint: String
    ) : DuplicateCheckResult()

    /**
     * Similar transactions found within date window - needs review.
     */
    data class PossibleDuplicate(
        val candidates: List<DuplicateCandidate>,
        override val fingerprint: String,
        val confidence: DuplicateConfidence
    ) : DuplicateCheckResult()
}

/**
 * A potential duplicate candidate.
 */
data class DuplicateCandidate(
    val existingId: String,
    val similarity: Float,
    val dateDistance: Int
)

/**
 * Confidence level for possible duplicate detection.
 */
enum class DuplicateConfidence {
    /** High similarity (≥85%) - very likely duplicate */
    HIGH,
    /** Medium similarity (70-85%) - probable duplicate */
    MEDIUM,
    /** Low similarity (<70%) - possible but uncertain */
    LOW
}
