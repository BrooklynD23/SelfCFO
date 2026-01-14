package com.ledgerlens.categorization.rules

import com.ledgerlens.categorization.TransactionFeatures
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class RuleSuggester(private val config: SuggesterConfig = SuggesterConfig()) {
    private val corrections = mutableListOf<UserCorrection>()

    fun recordCorrection(correction: UserCorrection) {
        corrections.add(correction)
        while (corrections.size > config.maxCorrectionsToTrack) corrections.removeAt(0)
    }

    fun generateSuggestions(): List<RuleSuggestion> {
        val suggestions = mutableListOf<RuleSuggestion>()
        val correctionsByCategory = corrections.groupBy { it.newCategoryId }
        for ((categoryId, catCorrections) in correctionsByCategory) {
            if (catCorrections.size < config.minCorrectionsForSuggestion) continue
            suggestions.addAll(suggestMerchantRules(categoryId, catCorrections))
            suggestions.addAll(suggestKeywordRules(categoryId, catCorrections))
            suggestions.addAll(suggestAmountRules(categoryId, catCorrections))
        }
        return suggestions.filter { it.confidence >= config.minSuggestionConfidence }.sortedByDescending { it.confidence }.take(config.maxSuggestionsToReturn)
    }

    fun clearCorrections() = corrections.clear()
    fun correctionCount(): Int = corrections.size

    private fun suggestMerchantRules(categoryId: String, corrections: List<UserCorrection>): List<RuleSuggestion> {
        val suggestions = mutableListOf<RuleSuggestion>()
        val merchantPatterns = findCommonPatterns(corrections.map { it.features.merchantNormalized.lowercase() })
        for ((pattern, count) in merchantPatterns) {
            if (count < config.minCorrectionsForSuggestion || pattern.length < config.minPatternLength) continue
            val confidence = calculateConfidence(count, corrections.size, pattern.length)
            val matchCount = corrections.count { it.features.merchantNormalized.lowercase().contains(pattern) }
            val rule = RuleBuilder("suggested-merchant-${pattern.hashCode()}")
                .name("$pattern → $categoryId")
                .description("Auto-suggested: Merchant contains '$pattern' (matched $matchCount corrections)")
                .whenMerchantContains(pattern).thenSetCategory(categoryId, confidence.coerceAtMost(0.85f)).suggestedRule().priority(80).build()
            suggestions.add(RuleSuggestion(rule = rule, confidence = confidence, basedOnCorrections = matchCount, reason = "Merchant '$pattern' was corrected to '$categoryId' $matchCount times"))
        }
        return suggestions
    }

    private fun suggestKeywordRules(categoryId: String, corrections: List<UserCorrection>): List<RuleSuggestion> {
        val suggestions = mutableListOf<RuleSuggestion>()
        val allTokens = corrections.flatMap { it.features.descriptionTokens }
        val tokenCounts = allTokens.groupingBy { it }.eachCount()
        val frequentTokens = tokenCounts.filter { (token, count) -> count >= config.minCorrectionsForSuggestion && token.length >= config.minPatternLength && token !in COMMON_STOP_WORDS }.toList().sortedByDescending { it.second }.take(5)
        if (frequentTokens.isEmpty()) return suggestions
        val topKeywords = frequentTokens.map { it.first }
        val totalMatches = frequentTokens.sumOf { it.second }
        val confidence = calculateConfidence(totalMatches, corrections.size * topKeywords.size, 10)
        val rule = RuleBuilder("suggested-keywords-${topKeywords.hashCode()}")
            .name("Keywords [${topKeywords.take(3).joinToString(", ")}] → $categoryId")
            .description("Auto-suggested: Description contains common keywords")
            .whenDescriptionContains(*topKeywords.toTypedArray()).thenSetCategory(categoryId, (confidence * 0.8f).coerceAtMost(0.75f)).suggestedRule().priority(60).build()
        suggestions.add(RuleSuggestion(rule = rule, confidence = confidence, basedOnCorrections = corrections.size, reason = "Keywords ${topKeywords.take(3)} frequently appear in corrections to '$categoryId'"))
        return suggestions
    }

    private fun suggestAmountRules(categoryId: String, corrections: List<UserCorrection>): List<RuleSuggestion> {
        val suggestions = mutableListOf<RuleSuggestion>()
        val amounts = corrections.map { kotlin.math.abs(it.features.amountCents) }
        if (amounts.size < config.minCorrectionsForSuggestion) return suggestions
        val sortedAmounts = amounts.sorted()
        val median = sortedAmounts[sortedAmounts.size / 2]
        val variance = amounts.map { (it - median) * (it - median) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        if (stdDev < median * 0.3 && median > 0) {
            val minAmount = (median - stdDev * 2).toLong().coerceAtLeast(0)
            val maxAmount = (median + stdDev * 2).toLong()
            val matchCount = amounts.count { it in minAmount..maxAmount }
            if (matchCount < config.minCorrectionsForSuggestion) return suggestions
            val confidence = calculateConfidence(matchCount, corrections.size, 5)
            val commonMerchant = findMostCommonPattern(corrections.map { it.features.merchantNormalized.lowercase() })
            val ruleBuilder = RuleBuilder("suggested-amount-${categoryId.hashCode()}-${median}").description("Auto-suggested: Amount around ${formatCents(median)}").suggestedRule().priority(50)
            if (commonMerchant != null && commonMerchant.second >= config.minCorrectionsForSuggestion) {
                ruleBuilder.name("${commonMerchant.first} ~${formatCents(median)} → $categoryId").whenMerchantContains(commonMerchant.first).andAmountBetween(minAmount, maxAmount).thenSetCategory(categoryId, (confidence * 0.85f).coerceAtMost(0.8f))
            } else {
                ruleBuilder.name("Amount ~${formatCents(median)} → $categoryId").whenAmountBetween(minAmount, maxAmount).thenSetCategory(categoryId, (confidence * 0.7f).coerceAtMost(0.6f))
            }
            suggestions.add(RuleSuggestion(rule = ruleBuilder.build(), confidence = confidence, basedOnCorrections = matchCount, reason = "Transactions around ${formatCents(median)} were corrected to '$categoryId'"))
        }
        return suggestions
    }

    private fun findCommonPatterns(strings: List<String>): List<Pair<String, Int>> {
        val patterns = mutableMapOf<String, Int>()
        for (s in strings) {
            val words = s.split(Regex("\\s+"))
            for (word in words) if (word.length >= config.minPatternLength) patterns[word] = (patterns[word] ?: 0) + 1
            for (i in 0 until words.size - 1) {
                val pair = "${words[i]} ${words[i + 1]}"
                if (pair.length >= config.minPatternLength) patterns[pair] = (patterns[pair] ?: 0) + 1
            }
        }
        return patterns.toList().filter { it.second >= config.minCorrectionsForSuggestion }.sortedByDescending { it.second }
    }

    private fun findMostCommonPattern(strings: List<String>): Pair<String, Int>? = findCommonPatterns(strings).firstOrNull()
    private fun calculateConfidence(matches: Int, total: Int, patternStrength: Int): Float {
        if (total == 0) return 0f
        val matchRatio = matches.toFloat() / total
        val strengthFactor = (patternStrength.coerceIn(3, 20) - 3) / 17f
        return (matchRatio * 0.7f + strengthFactor * 0.3f).coerceIn(0f, 1f)
    }
    private fun formatCents(cents: Long): String { val dollars = cents / 100; val remainder = cents % 100; return "\$${dollars}.${remainder.toString().padStart(2, '0')}" }

    companion object {
        val COMMON_STOP_WORDS = setOf("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "from", "as", "is", "was", "are", "were", "pos", "debit", "credit", "card", "purchase", "payment")
    }
}

data class SuggesterConfig(val minCorrectionsForSuggestion: Int = 3, val minPatternLength: Int = 4, val minSuggestionConfidence: Float = 0.3f, val maxSuggestionsToReturn: Int = 10, val maxCorrectionsToTrack: Int = 1000)

data class UserCorrection(val transactionId: String, val features: TransactionFeatures, val originalCategoryId: String?, val newCategoryId: String, val timestamp: Instant = Clock.System.now())

data class RuleSuggestion(val rule: CategoryRule, val confidence: Float, val basedOnCorrections: Int, val reason: String) {
    val isHighConfidence: Boolean get() = confidence >= 0.8f
    val shouldShowToUser: Boolean get() = confidence >= 0.3f
}

class RuleSuggestionService(private val suggester: RuleSuggester, private val repository: RuleRepository) {
    suspend fun recordCorrectionAndSuggest(correction: UserCorrection): List<RuleSuggestion> { suggester.recordCorrection(correction); return suggester.generateSuggestions() }
    suspend fun acceptSuggestion(suggestion: RuleSuggestion) { repository.upsert(suggestion.rule) }
    suspend fun acceptModifiedSuggestion(suggestion: RuleSuggestion, modifier: (CategoryRule) -> CategoryRule) { repository.upsert(modifier(suggestion.rule)) }
    suspend fun getPendingSuggestions(): List<RuleSuggestion> { val existingRuleIds = repository.getBySource(RuleSource.SUGGESTED).map { it.id }.toSet(); return suggester.generateSuggestions().filter { it.rule.id !in existingRuleIds } }
}
