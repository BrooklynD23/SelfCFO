package com.ledgerlens.import

/**
 * Normalizes raw merchant descriptions from bank statements into canonical names.
 * Handles common noise patterns, known aliases, and heuristic extraction.
 */
class MerchantNormalizer {

    // Common noise patterns to remove
    private val noisePatterns = listOf(
        Regex("""\b\d{4,}\b"""),  // Long numbers (terminal IDs)
        Regex("""\b[A-Z]{2}\s*\d{5}\b"""),  // State + ZIP
        Regex("""\b(CA|NY|TX|FL|IL|PA|OH|GA|NC|MI|NJ|VA|WA|AZ|MA|TN|IN|MO|MD|WI|CO|MN|SC|AL|LA|KY|OR|OK|CT|IA|UT|NV|AR|MS|KS|NM|NE|WV|ID|HI|NH|ME|MT|RI|DE|SD|ND|AK|DC|VT|WY|PR)\b"""),  // State codes
        Regex("""#\d+"""),  // Store numbers
        Regex("""\*+\d+"""),  // Masked card numbers
        Regex("""\b(POS|DEBIT|CREDIT|PURCHASE|CHECKCARD|ACH|ONLINE|MOBILE|WITHDRAWAL|TRANSFER|PAYMENT|PMT|AUTOPAY|RECURRING|MEMO|REF|TXN|TRANS)\b""", RegexOption.IGNORE_CASE),
        Regex("""\d{2}/\d{2}"""),  // MM/DD dates in description
        Regex("""\d{2}-\d{2}-\d{2,4}"""),  // Various date formats
        Regex("""\b\d{1,2}:\d{2}\b"""),  // Time patterns
        Regex("""XX+\d{4}"""),  // Masked account numbers (XX1234)
    )

    // Known merchant aliases mapping to canonical names
    private val merchantAliases = mapOf(
        "AMZN" to "Amazon",
        "AMAZON.COM" to "Amazon",
        "AMZN MKTP" to "Amazon Marketplace",
        "AMAZON MKTPLACE" to "Amazon Marketplace",
        "PRIME VIDEO" to "Amazon Prime Video",
        "APPLE.COM" to "Apple",
        "APPLE.COM/BILL" to "Apple",
        "UBER EATS" to "Uber Eats",
        "UBER   TRIP" to "Uber",
        "UBER TRIP" to "Uber",
        "LYFT" to "Lyft",
        "DOORDASH" to "DoorDash",
        "GRUBHUB" to "Grubhub",
        "NETFLIX" to "Netflix",
        "NETFLIX.COM" to "Netflix",
        "SPOTIFY" to "Spotify",
        "SPOTIFY USA" to "Spotify",
        "GOOGLE *" to "Google",
        "GOOGLE PLAY" to "Google Play",
        "PAYPAL *" to "PayPal",
        "SQ *" to "Square",
        "SQUARE *" to "Square",
        "TST*" to "Toast",
        "VENMO" to "Venmo",
        "ZELLE" to "Zelle",
        "COSTCO WHSE" to "Costco",
        "COSTCO GAS" to "Costco Gas",
        "WAL-MART" to "Walmart",
        "WALMART" to "Walmart",
        "WM SUPERCENTER" to "Walmart",
        "TARGET" to "Target",
        "STARBUCKS" to "Starbucks",
        "SBUX" to "Starbucks",
        "MCDONALD'S" to "McDonald's",
        "MCDONALDS" to "McDonald's",
        "CHICK-FIL-A" to "Chick-fil-A",
        "CHICKFILA" to "Chick-fil-A",
        "CVS/PHARMACY" to "CVS",
        "WALGREENS" to "Walgreens",
        "CHEVRON" to "Chevron",
        "SHELL OIL" to "Shell",
        "EXXONMOBIL" to "Exxon",
        "BP#" to "BP",
    )

    /**
     * Normalize a raw transaction description to a canonical merchant name.
     *
     * @param rawDescription The raw description from the bank statement
     * @return NormalizedMerchant with canonical name and match metadata
     */
    fun normalize(rawDescription: String): NormalizedMerchant {
        if (rawDescription.isBlank()) {
            return NormalizedMerchant(
                canonical = "Unknown",
                raw = rawDescription,
                matchType = MatchType.HEURISTIC
            )
        }

        var cleaned = rawDescription.uppercase()

        // Remove noise patterns
        for (pattern in noisePatterns) {
            cleaned = pattern.replace(cleaned, " ")
        }

        // Collapse whitespace
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

        // Check for known aliases (longest match first)
        val sortedAliases = merchantAliases.entries.sortedByDescending { it.key.length }
        for ((alias, canonical) in sortedAliases) {
            if (cleaned.startsWith(alias.uppercase())) {
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
            canonical = titleCase(normalized),
            raw = rawDescription,
            matchType = MatchType.HEURISTIC
        )
    }

    /**
     * Extract the primary merchant name from cleaned text.
     */
    private fun extractMerchantName(text: String): String {
        // Take up to the first delimiter or 50 chars
        val delimiters = listOf(" - ", " * ", "  ", "/", " @ ")
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

    /**
     * Convert to title case, preserving common acronyms.
     */
    private fun titleCase(text: String): String {
        val preserveCase = setOf("LLC", "INC", "USA", "NYC", "ATM", "ACH")

        return text.lowercase().split(" ").joinToString(" ") { word ->
            when {
                word.uppercase() in preserveCase -> word.uppercase()
                word.isNotEmpty() -> word.replaceFirstChar { it.uppercase() }
                else -> word
            }
        }
    }

    /**
     * Generate a description prefix for fingerprinting (first 20 chars normalized).
     */
    fun normalizedDescriptionPrefix(rawDescription: String): String {
        val normalized = normalize(rawDescription)
        return normalized.canonical.lowercase().take(20)
    }
}

/**
 * Result of merchant normalization.
 *
 * @property canonical The normalized/canonical merchant name
 * @property raw The original raw description
 * @property matchType How the canonical name was determined
 */
data class NormalizedMerchant(
    val canonical: String,
    val raw: String,
    val matchType: MatchType
)

/**
 * How the merchant name was matched/normalized.
 */
enum class MatchType {
    /** Exact match from user-defined merchant rules */
    EXACT,
    /** Matched via known alias table */
    ALIAS,
    /** Derived through heuristic extraction */
    HEURISTIC
}
