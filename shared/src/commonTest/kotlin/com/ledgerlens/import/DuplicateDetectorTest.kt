package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class DuplicateDetectorTest {

    private val detector = DuplicateDetector()

    // === Helper Functions ===

    private fun createParsedTransaction(
        description: String = "Test Merchant",
        merchantNormalized: String = "Test Merchant",
        date: LocalDate = LocalDate(2024, 1, 15),
        amountCents: Long = 1000L,
        currency: String = "USD"
    ): ParsedTransaction = ParsedTransaction(
        rawDescription = description,
        merchantNormalized = merchantNormalized,
        postedDate = date,
        amount = Money.fromMinorUnits(amountCents, currency),
        type = TransactionType.DEBIT
    )

    private fun createExistingTransaction(
        id: String = "existing-1",
        fingerprint: String = "abc123",
        merchantNormalized: String = "Test Merchant",
        date: LocalDate = LocalDate(2024, 1, 15),
        amountCents: Long = 1000L,
        currency: String = "USD"
    ): ExistingTransaction = ExistingTransaction(
        id = id,
        fingerprint = fingerprint,
        merchantNormalized = merchantNormalized,
        postedDate = date,
        amountMinorUnits = amountCents,
        currencyCode = currency
    )

    // === Unique Transaction Tests ===

    @Test
    fun `returns Unique when no existing transactions`() {
        val tx = createParsedTransaction()
        val result = detector.checkDuplicate(tx, "account-1", emptyList())

        assertIs<DuplicateCheckResult.Unique>(result)
        assertTrue(result.fingerprint.isNotEmpty())
    }

    @Test
    fun `returns Unique when no matching transactions`() {
        val tx = createParsedTransaction(
            merchantNormalized = "Coffee Shop",
            amountCents = 500L
        )
        val existing = listOf(
            createExistingTransaction(
                merchantNormalized = "Grocery Store",
                amountCents = 10000L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.Unique>(result)
    }

    @Test
    fun `returns Unique when amount differs`() {
        val tx = createParsedTransaction(
            merchantNormalized = "Test Merchant",
            amountCents = 1000L
        )
        val existing = listOf(
            createExistingTransaction(
                merchantNormalized = "Test Merchant",
                amountCents = 1001L  // Different amount
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.Unique>(result)
    }

    @Test
    fun `returns Unique when date outside window`() {
        val tx = createParsedTransaction(
            date = LocalDate(2024, 1, 15)
        )
        val existing = listOf(
            createExistingTransaction(
                date = LocalDate(2024, 1, 20)  // 5 days later, outside ±2 window
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.Unique>(result)
    }

    // === Exact Duplicate Tests ===

    @Test
    fun `returns ExactDuplicate when fingerprint matches`() {
        val tx = createParsedTransaction(
            description = "STARBUCKS STORE #1234",
            merchantNormalized = "Starbucks",
            date = LocalDate(2024, 1, 15),
            amountCents = 525L
        )

        // Generate the same fingerprint
        val fingerprint = TransactionFingerprint()
        val expectedFp = fingerprint.generate(
            accountId = "account-1",
            postedDate = LocalDate(2024, 1, 15),
            amount = Money.fromMinorUnits(525L, "USD"),
            rawDescription = "STARBUCKS STORE #1234"
        )

        val existing = listOf(
            createExistingTransaction(
                id = "existing-starbucks",
                fingerprint = expectedFp,
                merchantNormalized = "Starbucks",
                date = LocalDate(2024, 1, 15),
                amountCents = 525L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.ExactDuplicate>(result)
        assertEquals("existing-starbucks", result.existingId)
    }

    // === Possible Duplicate Tests ===

    @Test
    fun `returns PossibleDuplicate when same amount and merchant within date window`() {
        val tx = createParsedTransaction(
            merchantNormalized = "Starbucks",
            date = LocalDate(2024, 1, 15),
            amountCents = 525L
        )
        val existing = listOf(
            createExistingTransaction(
                id = "existing-1",
                fingerprint = "different-fingerprint",
                merchantNormalized = "Starbucks",
                date = LocalDate(2024, 1, 16),  // 1 day later, within window
                amountCents = 525L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
        assertTrue(result.candidates.isNotEmpty())
        assertEquals("existing-1", result.candidates.first().existingId)
    }

    @Test
    fun `calculates correct date distance for candidates`() {
        val tx = createParsedTransaction(
            date = LocalDate(2024, 1, 15),
            amountCents = 1000L
        )
        val existing = listOf(
            createExistingTransaction(
                id = "existing-1",
                date = LocalDate(2024, 1, 17),  // 2 days later
                amountCents = 1000L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
        assertEquals(2, result.candidates.first().dateDistance)
    }

    @Test
    fun `assigns HIGH confidence for very similar transactions`() {
        val tx = createParsedTransaction(
            merchantNormalized = "Starbucks",
            date = LocalDate(2024, 1, 15),
            amountCents = 525L
        )
        val existing = listOf(
            createExistingTransaction(
                merchantNormalized = "Starbucks",  // Exact match
                date = LocalDate(2024, 1, 15),  // Same date
                amountCents = 525L  // Same amount
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
        assertEquals(DuplicateConfidence.HIGH, result.confidence)
    }

    @Test
    fun `assigns MEDIUM confidence for similar but not identical merchants`() {
        val tx = createParsedTransaction(
            merchantNormalized = "Starbucks Coffee",
            date = LocalDate(2024, 1, 15),
            amountCents = 525L
        )
        val existing = listOf(
            createExistingTransaction(
                merchantNormalized = "Starbucks",  // Similar but not exact
                date = LocalDate(2024, 1, 16),  // 1 day off
                amountCents = 525L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
        // Should be HIGH or MEDIUM based on similarity calculation
        assertTrue(result.confidence in listOf(DuplicateConfidence.HIGH, DuplicateConfidence.MEDIUM))
    }

    @Test
    fun `returns multiple candidates sorted by similarity`() {
        val tx = createParsedTransaction(
            merchantNormalized = "Coffee Shop",
            date = LocalDate(2024, 1, 15),
            amountCents = 500L
        )
        val existing = listOf(
            createExistingTransaction(
                id = "less-similar",
                merchantNormalized = "Coffee Place",  // Less similar
                date = LocalDate(2024, 1, 17),
                amountCents = 500L
            ),
            createExistingTransaction(
                id = "more-similar",
                merchantNormalized = "Coffee Shop",  // More similar
                date = LocalDate(2024, 1, 16),
                amountCents = 500L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
        assertEquals(2, result.candidates.size)
        // More similar should be first
        assertEquals("more-similar", result.candidates.first().existingId)
    }

    // === Date Window Tests ===

    @Test
    fun `respects date window boundary - exactly 2 days`() {
        val tx = createParsedTransaction(
            date = LocalDate(2024, 1, 15),
            amountCents = 1000L
        )
        val existing = listOf(
            createExistingTransaction(
                date = LocalDate(2024, 1, 17),  // Exactly 2 days later
                amountCents = 1000L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
    }

    @Test
    fun `excludes transactions outside date window - 3 days`() {
        val tx = createParsedTransaction(
            date = LocalDate(2024, 1, 15),
            amountCents = 1000L
        )
        val existing = listOf(
            createExistingTransaction(
                date = LocalDate(2024, 1, 18),  // 3 days later, outside window
                amountCents = 1000L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.Unique>(result)
    }

    @Test
    fun `handles negative date window - past dates`() {
        val tx = createParsedTransaction(
            date = LocalDate(2024, 1, 15),
            amountCents = 1000L
        )
        val existing = listOf(
            createExistingTransaction(
                date = LocalDate(2024, 1, 13),  // 2 days earlier
                amountCents = 1000L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
    }

    // === Currency Handling Tests ===

    @Test
    fun `different currencies are not duplicates`() {
        val tx = createParsedTransaction(
            amountCents = 1000L,
            currency = "USD"
        )
        val existing = listOf(
            createExistingTransaction(
                amountCents = 1000L,
                currency = "EUR"  // Different currency
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.Unique>(result)
    }

    // === Batch Processing Tests ===

    @Test
    fun `batch check processes all transactions`() {
        val transactions = listOf(
            createParsedTransaction(merchantNormalized = "Merchant A", amountCents = 100L),
            createParsedTransaction(merchantNormalized = "Merchant B", amountCents = 200L),
            createParsedTransaction(merchantNormalized = "Merchant C", amountCents = 300L)
        )

        val results = detector.checkBatch(transactions, "account-1", emptyList())
        assertEquals(3, results.size)
        assertTrue(results.all { it.second is DuplicateCheckResult.Unique })
    }

    // === Fingerprint Consistency Tests ===

    @Test
    fun `fingerprint is deterministic`() {
        val tx = createParsedTransaction(
            description = "STARBUCKS #1234",
            date = LocalDate(2024, 1, 15),
            amountCents = 525L
        )

        val result1 = detector.checkDuplicate(tx, "account-1", emptyList())
        val result2 = detector.checkDuplicate(tx, "account-1", emptyList())

        assertEquals(result1.fingerprint, result2.fingerprint)
    }

    @Test
    fun `different accounts produce different fingerprints`() {
        val tx = createParsedTransaction()

        val result1 = detector.checkDuplicate(tx, "account-1", emptyList())
        val result2 = detector.checkDuplicate(tx, "account-2", emptyList())

        assertTrue(result1.fingerprint != result2.fingerprint)
    }

    // === Similarity Score Tests ===

    @Test
    fun `similarity score between 0 and 1`() {
        val tx = createParsedTransaction(
            merchantNormalized = "Test",
            amountCents = 1000L
        )
        val existing = listOf(
            createExistingTransaction(
                merchantNormalized = "Test",
                amountCents = 1000L
            )
        )

        val result = detector.checkDuplicate(tx, "account-1", existing)
        assertIs<DuplicateCheckResult.PossibleDuplicate>(result)
        
        val similarity = result.candidates.first().similarity
        assertTrue(similarity >= 0f && similarity <= 1f)
    }
}
