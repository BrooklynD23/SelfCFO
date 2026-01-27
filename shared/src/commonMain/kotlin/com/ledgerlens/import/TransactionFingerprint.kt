package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate

/**
 * Generates deterministic fingerprints for transaction deduplication.
 * 
 * Fingerprint formula: SHA-256(account_id | date | normalized_amount | normalized_description_prefix)
 * Per 02a-data-model-addendum.md §4.1
 */
class TransactionFingerprint {

    private val merchantNormalizer = MerchantNormalizer()

    /**
     * Generate a fingerprint for a transaction.
     *
     * @param accountId The account ID (or empty string if not account-scoped)
     * @param postedDate The transaction posted date
     * @param amount The transaction amount (uses absolute minor units)
     * @param rawDescription The raw merchant/description from statement
     * @return Hex-encoded SHA-256 fingerprint
     */
    fun generate(
        accountId: String,
        postedDate: LocalDate,
        amount: Money,
        rawDescription: String
    ): String {
        val normalizedPrefix = merchantNormalizer.normalizedDescriptionPrefix(rawDescription)
        
        val components = listOf(
            accountId,
            postedDate.toString(),  // ISO 8601: YYYY-MM-DD
            amount.minorUnits.let { kotlin.math.abs(it) }.toString(),
            normalizedPrefix
        )

        val input = components.joinToString("|")
        return sha256Hex(input)
    }

    /**
     * Generate fingerprint from pre-normalized data.
     */
    fun generateFromNormalized(
        accountId: String,
        postedDate: LocalDate,
        absoluteAmountMinorUnits: Long,
        normalizedDescriptionPrefix: String
    ): String {
        val components = listOf(
            accountId,
            postedDate.toString(),
            absoluteAmountMinorUnits.toString(),
            normalizedDescriptionPrefix.lowercase().take(20)
        )

        val input = components.joinToString("|")
        return sha256Hex(input)
    }

    /**
     * Compute SHA-256 hash and return as hex string.
     * Uses expect/actual for platform-specific implementation.
     */
    private fun sha256Hex(input: String): String {
        val bytes = input.encodeToByteArray()
        val hash = Sha256.digest(bytes)
        return hash.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

/**
 * Platform-agnostic SHA-256 implementation.
 * Uses expect/actual pattern for JVM/Native implementations.
 */
expect object Sha256 {
    /**
     * Compute SHA-256 digest of the input bytes.
     * @return 32-byte digest
     */
    fun digest(input: ByteArray): ByteArray
}

/**
 * Parsed transaction data ready for fingerprinting and duplicate detection.
 */
data class ParsedTransaction(
    val rawDescription: String,
    val merchantNormalized: String,
    val postedDate: LocalDate,
    val transactionDate: LocalDate? = null,
    val amount: Money,
    val type: TransactionType,
    val memo: String? = null,
    val checkNumber: String? = null,
    val referenceNumber: String? = null
)

/**
 * Transaction type enumeration.
 */
enum class TransactionType {
    DEBIT,
    CREDIT,
    TRANSFER,
    FEE,
    INTEREST,
    ATM,
    CHECK,
    OTHER
}
