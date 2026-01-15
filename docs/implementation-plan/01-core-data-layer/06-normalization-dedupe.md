# 06: Normalization & Deduplication

## Overview

Implement transaction and merchant normalization, fingerprint computation, and duplicate detection per [02a-data-model-addendum.md §4](../../PRDs/02a-data-model-addendum.md#4-import-idempotency-specification).

---

## Implementation Steps

### Step 1: Merchant Normalizer

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/MerchantNormalizer.kt
package com.ledgerlens.import

class MerchantNormalizer {

    // Common noise patterns to remove
    private val noisePatterns = listOf(
        Regex("""\b\d{4,}\b"""),  // Long numbers (terminal IDs)
        Regex("""\b[A-Z]{2}\s*\d{5}\b"""),  // State + ZIP
        Regex("""\b(CA|NY|TX|FL|IL|PA|OH|GA|NC|MI|NJ|VA|WA|AZ|MA|TN|IN|MO|MD|WI|CO|MN|SC|AL|LA|KY|OR|OK|CT|IA|UT|NV|AR|MS|KS|NM|NE|WV|ID|HI|NH|ME|MT|RI|DE|SD|ND|AK|DC|VT|WY|PR)\b"""),  // State codes
        Regex("""#\d+"""),  // Store numbers
        Regex("""\*+\d+"""),  // Masked card numbers
        Regex("""\b(POS|DEBIT|CREDIT|PURCHASE|CHECKCARD|ACH|ONLINE|MOBILE)\b""", RegexOption.IGNORE_CASE),
        Regex("""\d{2}/\d{2}"""),  // MM/DD dates in description
    )

    // Known merchant aliases
    private val merchantAliases = mapOf(
        "AMZN" to "Amazon",
        "AMAZON.COM" to "Amazon",
        "AMZN MKTP" to "Amazon Marketplace",
        "APPLE.COM" to "Apple",
        "UBER EATS" to "Uber Eats",
        "UBER   TRIP" to "Uber",
        "LYFT" to "Lyft",
        "DOORDASH" to "DoorDash",
        "NETFLIX" to "Netflix",
        "SPOTIFY" to "Spotify",
        "GOOGLE *" to "Google",
        "PAYPAL *" to "PayPal",
    )

    fun normalize(rawDescription: String): NormalizedMerchant {
        var cleaned = rawDescription.uppercase()

        // Remove noise patterns
        for (pattern in noisePatterns) {
            cleaned = pattern.replace(cleaned, " ")
        }

        // Collapse whitespace
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

        // Check for known aliases
        for ((alias, canonical) in merchantAliases) {
            if (cleaned.startsWith(alias)) {
                return NormalizedMerchant(
                    canonical = canonical,
                    raw = rawDescription,
                    matchType = MatchType.ALIAS
                )
            }
        }

        // Basic normalization: take first meaningful segment
        val normalized = extractMerchantName(cleaned)

        return NormalizedMerchant(
            canonical = normalized.lowercase().split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            },
            raw = rawDescription,
            matchType = MatchType.HEURISTIC
        )
    }

    private fun extractMerchantName(text: String): String {
        // Take up to the first delimiter or 30 chars
        val delimiters = listOf(" - ", " * ", "  ", "/")
        var result = text

        for (delimiter in delimiters) {
            val idx = result.indexOf(delimiter)
            if (idx > 3) {  // Minimum merchant name length
                result = result.substring(0, idx)
                break
            }
        }

        return result.take(50).trim()
    }
}

data class NormalizedMerchant(
    val canonical: String,
    val raw: String,
    val matchType: MatchType
)

enum class MatchType {
    EXACT,
    ALIAS,
    HEURISTIC
}
```

### Step 2: Fingerprint Generator

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/FingerprintGenerator.kt
package com.ledgerlens.import

import java.security.MessageDigest

/**
 * Generates transaction fingerprints for deduplication.
 * Per 02a-data-model-addendum.md §4.1
 */
class FingerprintGenerator {

    /**
     * Generate fingerprint for a transaction.
     *
     * Formula: SHA256(merchant + "|" + date + "|" + abs(amount) + "|" + currency + "|" + account?)
     */
    fun generate(
        merchantNormalized: String,
        postedDate: LocalDate,
        amount: Money,
        accountId: String? = null
    ): String {
        val components = listOf(
            merchantNormalized.lowercase().trim(),
            postedDate.toString(),  // ISO 8601
            amount.minorUnits.absoluteValue.toString(),
            amount.currencyCode,
            accountId ?: ""
        )

        val input = components.joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))

        return hash.joinToString("") { "%02x".format(it) }
    }
}
```

### Step 3: Duplicate Detector

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/DuplicateDetector.kt
package com.ledgerlens.import

class DuplicateDetector(
    private val transactionRepository: TransactionRepository,
    private val fingerprintGenerator: FingerprintGenerator
) {

    /**
     * Check for duplicates of a transaction.
     * @param transaction The transaction to check
     * @param dateWindow Days to look around the posted date (±)
     * @return Duplicate detection result
     */
    suspend fun checkDuplicate(
        transaction: ParsedTransaction,
        accountId: String?,
        dateWindow: Int = 2
    ): DuplicateCheckResult {
        val fingerprint = fingerprintGenerator.generate(
            merchantNormalized = transaction.merchantNormalized,
            postedDate = transaction.postedDate,
            amount = transaction.amount,
            accountId = accountId
        )

        // Check exact fingerprint match
        val exactMatch = transactionRepository.findByFingerprint(fingerprint)
        if (exactMatch != null) {
            return DuplicateCheckResult.ExactDuplicate(
                existingId = exactMatch.id,
                fingerprint = fingerprint
            )
        }

        // Check near matches (same merchant + amount, different date within window)
        val nearMatches = transactionRepository.findSimilar(
            merchantNormalized = transaction.merchantNormalized,
            amount = transaction.amount,
            dateFrom = transaction.postedDate.minusDays(dateWindow.toLong()),
            dateTo = transaction.postedDate.plusDays(dateWindow.toLong()),
            accountId = accountId
        )

        if (nearMatches.isNotEmpty()) {
            return DuplicateCheckResult.PossibleDuplicate(
                candidates = nearMatches.map { existing ->
                    DuplicateCandidate(
                        existingId = existing.id,
                        similarity = calculateSimilarity(transaction, existing),
                        dateDistance = ChronoUnit.DAYS.between(
                            transaction.postedDate,
                            existing.postedDate
                        ).absoluteValue.toInt()
                    )
                },
                fingerprint = fingerprint
            )
        }

        return DuplicateCheckResult.Unique(fingerprint = fingerprint)
    }

    private fun calculateSimilarity(
        new: ParsedTransaction,
        existing: ImportedTransaction
    ): Float {
        var score = 0f

        // Amount match (must be exact for high similarity)
        if (new.amount.minorUnits == existing.amountMinorUnits) {
            score += 0.4f
        }

        // Merchant similarity
        val merchantSimilarity = stringSimilarity(
            new.merchantNormalized,
            existing.merchantNormalized
        )
        score += merchantSimilarity * 0.4f

        // Date proximity
        val daysDiff = ChronoUnit.DAYS.between(new.postedDate, existing.postedDate).absoluteValue
        score += when {
            daysDiff == 0L -> 0.2f
            daysDiff == 1L -> 0.15f
            daysDiff == 2L -> 0.1f
            else -> 0f
        }

        return score
    }

    private fun stringSimilarity(a: String, b: String): Float {
        val longer = maxOf(a.length, b.length)
        if (longer == 0) return 1.0f
        val distance = levenshteinDistance(a.lowercase(), b.lowercase())
        return (longer - distance).toFloat() / longer
    }
}

sealed class DuplicateCheckResult {
    abstract val fingerprint: String

    data class Unique(
        override val fingerprint: String
    ) : DuplicateCheckResult()

    data class ExactDuplicate(
        val existingId: String,
        override val fingerprint: String
    ) : DuplicateCheckResult()

    data class PossibleDuplicate(
        val candidates: List<DuplicateCandidate>,
        override val fingerprint: String
    ) : DuplicateCheckResult()
}

data class DuplicateCandidate(
    val existingId: String,
    val similarity: Float,
    val dateDistance: Int
)
```

### Step 4: Import Orchestrator

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/ImportOrchestrator.kt
package com.ledgerlens.import

class ImportOrchestrator(
    private val sourceFileRepository: SourceFileRepository,
    private val importJobRepository: ImportJobRepository,
    private val transactionRepository: TransactionRepository,
    private val merchantNormalizer: MerchantNormalizer,
    private val fingerprintGenerator: FingerprintGenerator,
    private val duplicateDetector: DuplicateDetector,
    private val categorizer: TransactionCategorizer  // From Sprint 02
) {

    suspend fun importFile(
        fileData: ByteArray,
        filename: String,
        accountId: String?
    ): ImportResult {
        // 1. Check for duplicate file
        val contentHash = fileData.sha256()
        val existingFile = sourceFileRepository.findByHash(contentHash)
        if (existingFile != null) {
            return ImportResult.FileAlreadyImported(existingFile.id)
        }

        // 2. Create source file record
        val sourceFile = sourceFileRepository.create(
            contentHash = contentHash,
            filename = filename,
            fileType = detectFileType(filename),
            sizeBytes = fileData.size.toLong(),
            accountId = accountId
        )

        // 3. Create import job
        val importJob = importJobRepository.create(sourceFileId = sourceFile.id)

        try {
            // 4. Parse file
            importJobRepository.updateProgress(importJob.id, 10, "Extracting")
            val parseResult = parseFile(fileData, filename)

            when (parseResult) {
                is ParseResult.Success -> {
                    // 5. Normalize and dedupe
                    importJobRepository.updateProgress(importJob.id, 40, "Normalizing")
                    val normalized = normalizeTransactions(parseResult.transactions)

                    importJobRepository.updateProgress(importJob.id, 60, "Deduplicating")
                    val deduped = deduplicateTransactions(normalized, accountId)

                    // 6. Categorize
                    importJobRepository.updateProgress(importJob.id, 80, "Categorizing")
                    val categorized = categorizeTransactions(deduped.unique)

                    // 7. Save
                    importJobRepository.updateProgress(importJob.id, 90, "Saving")
                    saveTransactions(categorized, sourceFile.id, importJob.id, accountId)

                    // 8. Complete
                    importJobRepository.complete(
                        id = importJob.id,
                        transactionsFound = parseResult.transactions.size,
                        transactionsNew = deduped.unique.size,
                        transactionsDupe = deduped.duplicates.size
                    )

                    return ImportResult.Success(
                        importJobId = importJob.id,
                        transactionsImported = deduped.unique.size,
                        duplicatesSkipped = deduped.duplicates.size,
                        needsReview = deduped.needsReview
                    )
                }
                is ParseResult.NeedsReview -> {
                    importJobRepository.updateStatus(importJob.id, "needs_review")
                    return ImportResult.NeedsReview(
                        importJobId = importJob.id,
                        issues = parseResult.issues
                    )
                }
                is ParseResult.Failure -> {
                    importJobRepository.fail(importJob.id, parseResult.error.message)
                    return ImportResult.Failure(parseResult.error)
                }
            }
        } catch (e: Exception) {
            importJobRepository.fail(importJob.id, e.message ?: "Unknown error")
            return ImportResult.Failure(ParseError.Unknown(e.message ?: "Unknown error"))
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Merchant normalization removes noise
- [ ] Known merchant aliases mapped
- [ ] Fingerprint generation is deterministic
- [ ] Exact duplicates detected
- [ ] Near-duplicates flagged for review
- [ ] Import is idempotent (re-import same file = no duplicates)
- [ ] Cross-file duplicates detected

---

## Dependencies

- Sprint 01 database schema
- Kotlinx DateTime

---

## Estimated Complexity

**Medium** - Well-defined algorithms with comprehensive testing needed.
