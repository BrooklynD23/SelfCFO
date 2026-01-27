# 06: Normalization & Deduplication

## Overview

Implement transaction and merchant normalization, fingerprint computation, and duplicate detection per [02a-data-model-addendum.md §4](../../PRDs/02a-data-model-addendum.md#4-import-idempotency-specification).

**Current implementation:** `shared/src/commonMain/kotlin/com/ledgerlens/import/MerchantNormalizer.kt`, `FingerprintGenerator.kt`, and `ImportService.kt` (uses `ImportedTransactionRepository` + `DuplicateCandidateRepository`).

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

import com.ledgerlens.domain.Money
import com.ledgerlens.security.sha256Hex
import kotlinx.datetime.LocalDate

/**
 * Deterministic transaction fingerprint for idempotent import.
 *
 * Strategy (stable):
 * sha256(merchantNormalized|postedDate|abs(amountMinor)|currency|accountId?)
 */
object FingerprintGenerator {
    fun fingerprint(
        merchantNormalized: String,
        postedDate: LocalDate,
        amount: Money,
        accountId: String?
    ): String {
        val s = buildString {
            append(merchantNormalized.trim().lowercase())
            append('|')
            append(postedDate.toString())
            append('|')
            append(kotlin.math.abs(amount.minorUnits))
            append('|')
            append(amount.currencyCode.uppercase())
            append('|')
            append(accountId ?: "")
        }
        return sha256Hex(s.encodeToByteArray())
    }
}
```

### Step 3: Duplicate Candidate Tracking (Fingerprint Matches)

LedgerLens uses a stable transaction fingerprint to detect potential duplicates across imports without deleting data automatically.

- Compute `FingerprintGenerator.fingerprint(...)` for each parsed transaction
- Query `ImportedTransactionRepository.findMatchRefsByFingerprint(fingerprint)` for prior imports
- If matches exist in a different `sourceFileId`, insert `duplicate_candidate` rows via `DuplicateCandidateRepository.insertFingerprintMatches(...)`
- Review/resolve in the Review Inbox UI

```kotlin
// See: shared/src/commonMain/kotlin/com/ledgerlens/import/ImportService.kt
val fingerprint = FingerprintGenerator.fingerprint(merchantNormalized, postedDate, amount, accountId)
val matchIds = importedTransactionRepository.findMatchRefsByFingerprint(fingerprint)
    .filter { it.sourceFileId != sourceFileId }
    .map { it.id }

if (matchIds.isNotEmpty()) {
    duplicateCandidateRepository.insertFingerprintMatches(
        transactionId = txnId,
        matchedTransactionIds = matchIds,
        fingerprint = fingerprint,
        metadataJson = candidateMetadata
    )
}
```

### Step 4: Import Orchestrator (ImportService)

The import entrypoint is `ImportService`, which orchestrates:

1. File-hash idempotency (SHA-256 of raw bytes)
2. Create `source_file` + `import_job`
3. Parse CSV via `CsvParser`
4. Persist immutable `imported_transaction` rows
5. Run the categorization pipeline and enqueue low-confidence items to the review queue

---

## Acceptance Criteria

- [x] Merchant normalization removes noise
- [x] Known merchant aliases mapped
- [x] Fingerprint generation is deterministic
- [x] Exact duplicates detected
- [x] Near-duplicates flagged for review
- [x] Import is idempotent (re-import same file = no duplicates)
- [x] Cross-file duplicates detected

---

## Dependencies

- Sprint 01 database schema
- Kotlinx DateTime

---

## Estimated Complexity

**Medium** - Well-defined algorithms with comprehensive testing needed.
