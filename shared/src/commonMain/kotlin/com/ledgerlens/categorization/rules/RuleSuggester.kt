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
        for ((categoryId, catCorrections) in corrections.groupBy { it.newCategoryId }) {
            if (catCorrections.size < config.minCorrectionsForSuggestion) continue
            suggestions.addAll(suggestMerchantRules(categoryId, catCorrections))
            suggestions.addAll(suggestKeywordRules(categoryId, catCorrections))
        }
        return suggestions.filter { it.confidence >= config.minSuggestionConfidence }.sortedByDescending { it.confidence }.take(config.maxSuggestionsToReturn)
    }

    fun clearCorrections() = corrections.clear()
    fun correctionCount() = corrections.size

    private fun suggestMerchantRules(categoryId: String, corrections: List<UserCorrection>): List<RuleSuggestion> {
        val suggestions = mutableListOf<RuleSuggestion>()
        for ((pattern, count) in findCommonPatterns(corrections.map { it.features.merchantNormalized.lowercase() })) {
            if (count < config.minCorrectionsForSuggestion || pattern.length < config.minPatternLength) continue
            val confidence = calculateConfidence(count, corrections.size, pattern.length)
            val matchCount = corrections.count { it.features.merchantNormalized.lowercase().contains(pattern) }
            val rule = RuleBuilder("suggested-merchant-${pattern.hashCode()}").name("$pattern → $categoryId").description("Auto-suggested: Merchant '$pattern'").whenMerchantContains(pattern).thenSetCategory(categoryId, confidence.coerceAtMost(0.85f)).suggestedRule().priority(80).build()
            suggestions.add(RuleSuggestion(rule, confidence, matchCount, "Merchant '$pattern' was corrected to '$categoryId' $matchCount times"))
        }
        return suggestions
    }

    private fun suggestKeywordRules(categoryId: String, corrections: List<UserCorrection>): List<RuleSuggestion> {
        val tokenCounts = corrections.flatMap { it.features.descriptionTokens }.groupingBy { it }.eachCount()
        val frequentTokens = tokenCounts.filter { (t, c) -> c >= config.minCorrectionsForSuggestion && t.length >= config.minPatternLength && t !in STOP_WORDS }.toList().sortedByDescending { it.second }.take(5)
        if (frequentTokens.isEmpty()) return emptyList()
        val topKeywords = frequentTokens.map { it.first }
        val confidence = calculateConfidence(frequentTokens.sumOf { it.second }, corrections.size * topKeywords.size, 10)
        val rule = RuleBuilder("suggested-keywords-${topKeywords.hashCode()}").name("Keywords [${topKeywords.take(3).joinToString(", ")}] → $categoryId").description("Auto-suggested keywords").whenDescriptionContains(*topKeywords.toTypedArray()).thenSetCategory(categoryId, (confidence * 0.8f).coerceAtMost(0.75f)).suggestedRule().priority(60).build()
        return listOf(RuleSuggestion(rule, confidence, corrections.size, "Keywords ${topKeywords.take(3)} frequently appear in corrections to '$categoryId'"))
    }

    private fun findCommonPatterns(strings: List<String>): List<Pair<String, Int>> {
        val patterns = mutableMapOf<String, Int>()
        for (s in strings) { for (word in s.split(Regex("\\s+"))) if (word.length >= config.minPatternLength) patterns[word] = (patterns[word] ?: 0) + 1 }
        return patterns.toList().filter { it.second >= config.minCorrectionsForSuggestion }.sortedByDescending { it.second }
    }

    private fun calculateConfidence(matches: Int, total: Int, strength: Int): Float {
        if (total == 0) return 0f
        return ((matches.toFloat() / total) * 0.7f + ((strength.coerceIn(3, 20) - 3) / 17f) * 0.3f).coerceIn(0f, 1f)
    }

    companion object { val STOP_WORDS = setOf("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "from", "pos", "debit", "credit", "card", "purchase", "payment") }
}

data class SuggesterConfig(val minCorrectionsForSuggestion: Int = 3, val minPatternLength: Int = 4, val minSuggestionConfidence: Float = 0.3f, val maxSuggestionsToReturn: Int = 10, val maxCorrectionsToTrack: Int = 1000)
data class UserCorrection(val transactionId: String, val features: TransactionFeatures, val originalCategoryId: String?, val newCategoryId: String, val timestamp: Instant = Clock.System.now())
data class RuleSuggestion(val rule: CategoryRule, val confidence: Float, val basedOnCorrections: Int, val reason: String) {
    val isHighConfidence get() = confidence >= 0.8f
    val shouldShowToUser get() = confidence >= 0.3f
}

class RuleSuggestionService(private val suggester: RuleSuggester, private val repository: RuleRepository) {
    suspend fun recordCorrectionAndSuggest(correction: UserCorrection) = suggester.also { it.recordCorrection(correction) }.generateSuggestions()
    suspend fun acceptSuggestion(suggestion: RuleSuggestion) { repository.upsert(suggestion.rule) }
    suspend fun getPendingSuggestions(): List<RuleSuggestion> { val existing = repository.getBySource(RuleSource.SUGGESTED).map { it.id }.toSet(); return suggester.generateSuggestions().filter { it.rule.id !in existing } }
}
