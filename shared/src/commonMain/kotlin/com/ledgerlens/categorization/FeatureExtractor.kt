package com.ledgerlens.categorization

/**
 * Extracts features from transaction data for ML classification.
 */
class FeatureExtractor(
    private val stopWords: Set<String> = DEFAULT_STOP_WORDS,
    private val minTokenLength: Int = MIN_TOKEN_LENGTH
) {
    /**
     * Extract features from raw transaction data.
     */
    fun extract(
        merchantName: String?,
        description: String,
        amountCents: Long,
        isDebit: Boolean,
        transactionDateMs: Long? = null,
        accountId: String? = null
    ): TransactionFeatures {
        val normalizedMerchant = normalizeMerchant(merchantName ?: description)
        val tokens = tokenize(description)
        val bucket = AmountBucket.fromCents(amountCents)

        val dayOfWeek = transactionDateMs?.let { getDayOfWeek(it) } ?: 0
        val dayOfMonth = transactionDateMs?.let { getDayOfMonth(it) } ?: 0

        return TransactionFeatures(
            merchantNormalized = normalizedMerchant,
            descriptionRaw = description,
            descriptionTokens = tokens,
            amountCents = amountCents,
            amountBucket = bucket,
            isDebit = isDebit,
            dayOfWeek = dayOfWeek,
            dayOfMonth = dayOfMonth,
            accountId = accountId
        )
    }

    /**
     * Normalize merchant name for consistent matching.
     */
    fun normalizeMerchant(merchant: String): String {
        return merchant
            .lowercase()
            .replace(NOISE_PATTERN, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_MERCHANT_LENGTH)
    }

    /**
     * Tokenize description into meaningful words.
     */
    fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= minTokenLength && it !in stopWords }
            .distinct()
            .take(MAX_TOKENS)
    }

    /**
     * Extract n-grams from tokens for phrase matching.
     */
    fun extractNGrams(tokens: List<String>, n: Int = 2): List<String> {
        if (tokens.size < n) return emptyList()
        return tokens.windowed(n).map { it.joinToString("_") }
    }

    /**
     * Create a feature vector suitable for similarity comparison.
     */
    fun toFeatureVector(features: TransactionFeatures): Map<String, Float> {
        val vector = mutableMapOf<String, Float>()

        // Merchant feature
        vector["merchant:${features.merchantNormalized}"] = 1.0f

        // Token features (TF-weighted)
        val tokenCounts = features.descriptionTokens.groupingBy { it }.eachCount()
        val maxCount = tokenCounts.values.maxOrNull() ?: 1
        for ((token, count) in tokenCounts) {
            vector["token:$token"] = count.toFloat() / maxCount
        }

        // Amount bucket feature
        vector["amount:${features.amountBucket.name}"] = 1.0f

        // Debit/credit feature
        vector["is_debit:${features.isDebit}"] = 1.0f

        // Day of week (normalized)
        if (features.dayOfWeek > 0) {
            vector["dow:${features.dayOfWeek}"] = 1.0f
        }

        return vector
    }

    /**
     * Calculate cosine similarity between two feature vectors.
     */
    fun cosineSimilarity(v1: Map<String, Float>, v2: Map<String, Float>): Float {
        val allKeys = v1.keys + v2.keys
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (key in allKeys) {
            val a = v1[key] ?: 0f
            val b = v2[key] ?: 0f
            dotProduct += a * b
            norm1 += a * a
            norm2 += b * b
        }

        if (norm1 == 0f || norm2 == 0f) return 0f
        return dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
    }

    private fun getDayOfWeek(timestampMs: Long): Int {
        // Simple day-of-week calculation (1 = Sunday, 7 = Saturday)
        val days = timestampMs / (24 * 60 * 60 * 1000)
        return ((days + 4) % 7 + 1).toInt() // Jan 1, 1970 was Thursday (5)
    }

    private fun getDayOfMonth(timestampMs: Long): Int {
        // Approximate day of month (1-31)
        val days = timestampMs / (24 * 60 * 60 * 1000)
        return ((days % 30) + 1).toInt()
    }

    companion object {
        const val MIN_TOKEN_LENGTH = 2
        const val MAX_TOKENS = 20
        const val MAX_MERCHANT_LENGTH = 100

        private val NOISE_PATTERN = Regex(
            """(\d{4,})|""" + // Long numbers (IDs, zip codes)
                """([*#]+\d+)|""" + // Masked card numbers
                """(\b[A-Z]{2}\s*\d{5}\b)|""" + // State + ZIP
                """(\bPOS\b)|(\bDEBIT\b)|(\bPURCHASE\b)|""" + // Common noise words
                """(\d{2}/\d{2})""", // Date patterns
            RegexOption.IGNORE_CASE
        )

        val DEFAULT_STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can", "this",
            "that", "these", "those", "it", "its", "payment", "purchase", "debit",
            "credit", "card", "pos", "ach", "fee", "ref", "id"
        )
    }
}
