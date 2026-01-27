package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate

/**
 * Manages import idempotency to prevent duplicate imports.
 * 
 * Provides:
 * 1. File-level deduplication via content hash
 * 2. Transaction-level deduplication via fingerprints
 * 3. Import session tracking for atomicity
 * 
 * Per 02a-data-model-addendum.md §4
 */
class ImportIdempotency(
    private val fingerprintGenerator: TransactionFingerprint = TransactionFingerprint(),
    private val merchantNormalizer: MerchantNormalizer = MerchantNormalizer(),
    private val duplicateDetector: DuplicateDetector = DuplicateDetector()
) {

    /**
     * Generate a content hash for a file to detect duplicate imports.
     */
    fun computeFileHash(fileContent: ByteArray): String {
        val hash = Sha256.digest(fileContent)
        return hash.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /**
     * Process a batch of raw transaction data for import.
     * Normalizes, fingerprints, and checks for duplicates.
     *
     * @param rawTransactions List of raw transaction data from parser
     * @param accountId Account to scope duplicates to
     * @param existingTransactions Existing transactions in the database
     * @return Import batch result with categorized transactions
     */
    fun processBatch(
        rawTransactions: List<RawTransactionData>,
        accountId: String,
        existingTransactions: List<ExistingTransaction>
    ): ImportBatchResult {
        val unique = mutableListOf<ProcessedTransaction>()
        val duplicates = mutableListOf<ProcessedTransaction>()
        val needsReview = mutableListOf<ReviewableTransaction>()
        val errors = mutableListOf<ImportError>()

        // Track fingerprints within this batch to detect intra-batch duplicates
        val batchFingerprints = mutableSetOf<String>()

        for ((index, raw) in rawTransactions.withIndex()) {
            try {
                val processed = processTransaction(raw, accountId)

                // Check for intra-batch duplicate
                if (processed.fingerprint in batchFingerprints) {
                    duplicates.add(processed)
                    continue
                }

                // Check against existing transactions
                val result = duplicateDetector.checkDuplicate(
                    transaction = processed.parsed,
                    accountId = accountId,
                    existingTransactions = existingTransactions
                )

                when (result) {
                    is DuplicateCheckResult.Unique -> {
                        batchFingerprints.add(processed.fingerprint)
                        unique.add(processed)
                    }
                    is DuplicateCheckResult.ExactDuplicate -> {
                        duplicates.add(processed.copy(
                            duplicateOf = result.existingId
                        ))
                    }
                    is DuplicateCheckResult.PossibleDuplicate -> {
                        if (result.confidence == DuplicateConfidence.HIGH) {
                            // High confidence = treat as duplicate but flag
                            duplicates.add(processed.copy(
                                duplicateOf = result.candidates.first().existingId
                            ))
                        } else {
                            // Medium/Low = needs user review
                            needsReview.add(ReviewableTransaction(
                                transaction = processed,
                                candidates = result.candidates,
                                confidence = result.confidence
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add(ImportError(
                    rowIndex = index,
                    rawData = raw,
                    message = e.message ?: "Unknown error"
                ))
            }
        }

        return ImportBatchResult(
            unique = unique,
            duplicates = duplicates,
            needsReview = needsReview,
            errors = errors
        )
    }

    /**
     * Process a single raw transaction into normalized form.
     */
    private fun processTransaction(
        raw: RawTransactionData,
        accountId: String
    ): ProcessedTransaction {
        val normalized = merchantNormalizer.normalize(raw.description)

        val parsed = ParsedTransaction(
            rawDescription = raw.description,
            merchantNormalized = normalized.canonical,
            postedDate = raw.postedDate,
            transactionDate = raw.transactionDate,
            amount = raw.amount,
            type = raw.type,
            memo = raw.memo,
            checkNumber = raw.checkNumber,
            referenceNumber = raw.referenceNumber
        )

        val fp = fingerprintGenerator.generate(
            accountId = accountId,
            postedDate = raw.postedDate,
            amount = raw.amount,
            rawDescription = raw.description
        )

        return ProcessedTransaction(
            parsed = parsed,
            fingerprint = fp,
            matchType = normalized.matchType,
            duplicateOf = null
        )
    }

    /**
     * Resolve a reviewable transaction as unique or duplicate.
     */
    fun resolveReview(
        transaction: ReviewableTransaction,
        resolution: ReviewResolution
    ): ProcessedTransaction {
        return when (resolution) {
            is ReviewResolution.AcceptAsUnique -> {
                transaction.transaction
            }
            is ReviewResolution.MarkAsDuplicate -> {
                transaction.transaction.copy(duplicateOf = resolution.existingId)
            }
        }
    }
}

/**
 * Raw transaction data from file parser.
 */
data class RawTransactionData(
    val description: String,
    val postedDate: LocalDate,
    val transactionDate: LocalDate? = null,
    val amount: Money,
    val type: TransactionType,
    val memo: String? = null,
    val checkNumber: String? = null,
    val referenceNumber: String? = null
)

/**
 * Processed transaction ready for database insertion.
 */
data class ProcessedTransaction(
    val parsed: ParsedTransaction,
    val fingerprint: String,
    val matchType: MatchType,
    val duplicateOf: String?
)

/**
 * Transaction that needs user review for duplicate decision.
 */
data class ReviewableTransaction(
    val transaction: ProcessedTransaction,
    val candidates: List<DuplicateCandidate>,
    val confidence: DuplicateConfidence
)

/**
 * Result of processing an import batch.
 */
data class ImportBatchResult(
    val unique: List<ProcessedTransaction>,
    val duplicates: List<ProcessedTransaction>,
    val needsReview: List<ReviewableTransaction>,
    val errors: List<ImportError>
) {
    val totalProcessed: Int get() = unique.size + duplicates.size + needsReview.size
    val successRate: Float get() = if (totalProcessed + errors.size == 0) 1f
        else totalProcessed.toFloat() / (totalProcessed + errors.size)
}

/**
 * Error during import processing.
 */
data class ImportError(
    val rowIndex: Int,
    val rawData: RawTransactionData,
    val message: String
)

/**
 * User resolution for a reviewable transaction.
 */
sealed class ReviewResolution {
    object AcceptAsUnique : ReviewResolution()
    data class MarkAsDuplicate(val existingId: String) : ReviewResolution()
}
