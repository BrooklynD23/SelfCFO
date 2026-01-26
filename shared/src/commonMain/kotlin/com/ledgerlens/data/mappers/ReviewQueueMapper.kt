package com.ledgerlens.data.mappers

import com.ledgerlens.categorization.pipeline.AmountBucket
import com.ledgerlens.categorization.pipeline.CategoryScore
import com.ledgerlens.categorization.pipeline.ClassificationExplanation
import com.ledgerlens.categorization.pipeline.ClassificationResult
import com.ledgerlens.categorization.pipeline.ReviewItemMetadata
import com.ledgerlens.categorization.pipeline.ReviewQueueItem
import com.ledgerlens.categorization.pipeline.ReviewStatus
import com.ledgerlens.categorization.pipeline.TransactionFeatures
import com.ledgerlens.db.Review_queue
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Maps ReviewQueueItem domain models to/from database Review_queue records.
 */
object ReviewQueueMapper {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Convert database Review_queue to domain ReviewQueueItem.
     */
    fun toDomain(db: Review_queue): ReviewQueueItem {
        val features = parseFeaturesJson(db.features_json)
        val alternatives = parseAlternativesJson(db.alternatives_json)
        val metadata = parseMetadataJson(db.metadata_json)
        val explanation = ClassificationExplanation(
            classifierUsed = db.explanation_classifier,
            reason = db.explanation_reason
        )
        val classification = ClassificationResult(
            categoryId = db.suggested_category_id,
            confidence = db.suggested_confidence.toFloat(),
            alternatives = alternatives,
            explanation = explanation
        )
        val status = when (db.status) {
            "PENDING" -> ReviewStatus.PENDING
            "ACCEPTED" -> ReviewStatus.ACCEPTED
            "REJECTED" -> ReviewStatus.REJECTED
            "DEFERRED" -> ReviewStatus.DEFERRED
            else -> ReviewStatus.PENDING
        }

        return ReviewQueueItem(
            transactionId = db.transaction_id,
            features = features,
            suggestedCategory = classification,
            alternatives = alternatives,
            priority = db.priority.toFloat(),
            enqueuedAt = Instant.fromEpochMilliseconds(db.enqueued_at),
            status = status,
            reviewedAt = db.reviewed_at?.let { Instant.fromEpochMilliseconds(it) },
            metadata = metadata
        )
    }

    /**
     * Convert domain ReviewQueueItem to database parameters for insert.
     */
    fun toDbParams(item: ReviewQueueItem): ReviewQueueDbParams {
        return ReviewQueueDbParams(
            transactionId = item.transactionId,
            featuresJson = serializeFeaturesJson(item.features),
            suggestedCategoryId = item.suggestedCategory.categoryId,
            suggestedConfidence = item.suggestedCategory.confidence.toDouble(),
            explanationClassifier = item.suggestedCategory.explanation.classifierUsed,
            explanationReason = item.suggestedCategory.explanation.reason,
            alternativesJson = serializeAlternativesJson(item.alternatives),
            priority = item.priority.toDouble(),
            status = when (item.status) {
                ReviewStatus.PENDING -> "PENDING"
                ReviewStatus.ACCEPTED -> "ACCEPTED"
                ReviewStatus.REJECTED -> "REJECTED"
                ReviewStatus.DEFERRED -> "DEFERRED"
            },
            enqueuedAt = item.enqueuedAt.toEpochMilliseconds(),
            reviewedAt = item.reviewedAt?.toEpochMilliseconds(),
            metadataJson = serializeMetadataJson(item.metadata)
        )
    }

    /**
     * Parse TransactionFeatures from JSON.
     */
    private fun parseFeaturesJson(jsonStr: String): TransactionFeatures {
        return try {
            val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
            TransactionFeatures(
                merchantNormalized = jsonObj["merchantNormalized"]?.jsonPrimitive?.content ?: "",
                descriptionRaw = jsonObj["descriptionRaw"]?.jsonPrimitive?.content ?: "",
                descriptionTokens = jsonObj["descriptionTokens"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                amountCents = jsonObj["amountCents"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                amountBucket = AmountBucket.valueOf(jsonObj["amountBucket"]?.jsonPrimitive?.content ?: "MEDIUM"),
                isDebit = jsonObj["isDebit"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                dayOfWeek = jsonObj["dayOfWeek"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                dayOfMonth = jsonObj["dayOfMonth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                accountId = jsonObj["accountId"]?.jsonPrimitive?.content
            )
        } catch (e: Exception) {
            // Fallback to minimal features
            TransactionFeatures(
                merchantNormalized = "",
                descriptionRaw = "",
                descriptionTokens = emptyList(),
                amountCents = 0L,
                amountBucket = AmountBucket.MEDIUM,
                isDebit = true,
                dayOfWeek = 0,
                dayOfMonth = 0,
                accountId = null
            )
        }
    }

    /**
     * Serialize TransactionFeatures to JSON.
     */
    private fun serializeFeaturesJson(features: TransactionFeatures): String {
        return buildJsonObject {
            put("merchantNormalized", features.merchantNormalized)
            put("descriptionRaw", features.descriptionRaw)
            put("descriptionTokens", JsonArray(features.descriptionTokens.map { JsonPrimitive(it) }))
            put("amountCents", JsonPrimitive(features.amountCents))
            put("amountBucket", features.amountBucket.name)
            put("isDebit", JsonPrimitive(features.isDebit))
            put("dayOfWeek", JsonPrimitive(features.dayOfWeek))
            put("dayOfMonth", JsonPrimitive(features.dayOfMonth))
            features.accountId?.let { put("accountId", it) }
        }.toString()
    }

    /**
     * Parse alternatives (List<CategoryScore>) from JSON.
     */
    private fun parseAlternativesJson(jsonStr: String): List<CategoryScore> {
        return try {
            if (jsonStr.isBlank() || jsonStr == "[]") {
                emptyList()
            } else {
                json.parseToJsonElement(jsonStr).jsonArray.map { obj ->
                    val item = obj.jsonObject
                    CategoryScore(
                        categoryId = item["categoryId"]?.jsonPrimitive?.content ?: "",
                        score = item["score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize alternatives to JSON.
     */
    private fun serializeAlternativesJson(alternatives: List<CategoryScore>): String {
        return if (alternatives.isEmpty()) {
            "[]"
        } else {
            alternatives.joinToString(prefix = "[", postfix = "]") { alt ->
                """{"categoryId":"${alt.categoryId}","score":"${alt.score}"}"""
            }
        }
    }

    /**
     * Parse ReviewItemMetadata from JSON.
     */
    private fun parseMetadataJson(jsonStr: String): ReviewItemMetadata {
        return try {
            if (jsonStr.isBlank() || jsonStr == "{}") {
                ReviewItemMetadata()
            } else {
                val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
                ReviewItemMetadata(
                    accountId = jsonObj["accountId"]?.jsonPrimitive?.content,
                    importBatchId = jsonObj["importBatchId"]?.jsonPrimitive?.content,
                    source = jsonObj["source"]?.jsonPrimitive?.content,
                    tags = jsonObj["tags"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
                )
            }
        } catch (e: Exception) {
            ReviewItemMetadata()
        }
    }

    /**
     * Serialize ReviewItemMetadata to JSON.
     */
    private fun serializeMetadataJson(metadata: ReviewItemMetadata): String {
        return buildJsonObject {
            metadata.accountId?.let { put("accountId", it) }
            metadata.importBatchId?.let { put("importBatchId", it) }
            metadata.source?.let { put("source", it) }
            if (metadata.tags.isNotEmpty()) {
                put("tags", JsonArray(metadata.tags.toList().map { JsonPrimitive(it) }))
            }
        }.toString()
    }
}

/**
 * Database parameters for ReviewQueue insert/update operations.
 */
data class ReviewQueueDbParams(
    val transactionId: String,
    val featuresJson: String,
    val suggestedCategoryId: String,
    val suggestedConfidence: Double,
    val explanationClassifier: String,
    val explanationReason: String,
    val alternativesJson: String,
    val priority: Double,
    val status: String,
    val enqueuedAt: Long,
    val reviewedAt: Long?,
    val metadataJson: String
)
