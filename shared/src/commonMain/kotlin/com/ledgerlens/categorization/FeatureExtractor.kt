package com.ledgerlens.categorization

import com.ledgerlens.domain.Money
import com.ledgerlens.import.MerchantNormalizer
import com.ledgerlens.import.ParsedTransaction
import kotlinx.datetime.DayOfWeek

/**
 * Extracts features from transactions for classification.
 *
 * Features include normalized merchant, tokenized description, amount bucket,
 * temporal features, and other signals useful for categorization.
 */
class FeatureExtractor(
    private val merchantNormalizer: MerchantNormalizer = MerchantNormalizer()
) {
    /**
     * Extract features from a parsed transaction.
     */
    fun extract(transaction: ParsedTransaction, accountId: String? = null): TransactionFeatures {
        val normalized = merchantNormalizer.normalize(transaction.descriptionRaw)
        val tokens = tokenize(transaction.descriptionRaw)
        val amountCents = transaction.amount.minorUnits

        return TransactionFeatures(
            merchantNormalized = normalized.canonical,
            descriptionRaw = transaction.descriptionRaw,
            descriptionTokens = tokens,
            amountCents = amountCents,
            amountBucket = AmountBucket.fromCents(amountCents),
            isDebit = amountCents < 0,
            dayOfWeek = transaction.postedDate.dayOfWeek.ordinal,
            dayOfMonth = transaction.postedDate.dayOfMonth,
            accountId = accountId
        )
    }

    /**
     * Extract features from raw transaction data.
     */
    fun extract(
        descriptionRaw: String,
        amount: Money,
        dayOfWeek: Int = 0,
        dayOfMonth: Int = 1,
        accountId: String? = null
    ): TransactionFeatures {
        val normalized = merchantNormalizer.normalize(descriptionRaw)
        val tokens = tokenize(descriptionRaw)
        val amountCents = amount.minorUnits

        return TransactionFeatures(
            merchantNormalized = normalized.canonical,
            descriptionRaw = descriptionRaw,
            descriptionTokens = tokens,
            amountCents = amountCents,
            amountBucket = AmountBucket.fromCents(amountCents),
            isDebit = amountCents < 0,
            dayOfWeek = dayOfWeek,
            dayOfMonth = dayOfMonth,
            accountId = accountId
        )
    }

    /**
     * Tokenize a description into normalized words for text matching.
     */
    fun tokenize(description: String): List<String> {
        return description
            .lowercase()
            .replace(Regex("""[^\w\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length >= MIN_TOKEN_LENGTH }
            .filter { it !in STOP_WORDS }
            .distinct()
    }

    /**
     * Extract n-grams from tokens for phrase matching.
     */
    fun extractNgrams(tokens: List<String>, n: Int = 2): List<String> {
        if (tokens.size < n) return emptyList()
        return tokens.windowed(n) { it.joinToString(" ") }
    }

    companion object {
        const val MIN_TOKEN_LENGTH = 2

        val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can", "need",
            "pos", "debit", "credit", "purchase", "payment", "pmt", "ach",
            "checkcard", "online", "mobile", "withdrawal", "transfer", "memo",
            "ref", "txn", "trans", "recurring", "autopay"
        )
    }
}
