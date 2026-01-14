package com.ledgerlens.ocr

sealed class OcrResult {
    data class Success(
        val text: String, val confidence: Float, val regions: List<TextRegion>,
        val lines: List<TextLine> = emptyList(), val blocks: List<TextBlock> = emptyList(),
        val processingTimeMs: Long = 0, val imageWidth: Int = 0, val imageHeight: Int = 0
    ) : OcrResult() {
        val isHighConfidence: Boolean get() = confidence >= 0.8f
        val needsReview: Boolean get() = confidence < 0.5f
        val regionCount: Int get() = regions.size
        val lineCount: Int get() = lines.size
        fun getLine(lineNumber: Int): TextLine? = lines.find { it.lineNumber == lineNumber }
        fun findRegions(searchText: String): List<TextRegion> = regions.filter { it.text.contains(searchText, ignoreCase = true) }
        fun getRegionsByConfidence(min: Float, max: Float = 1f): List<TextRegion> = regions.filter { it.confidence in min..max }
        companion object { const val HIGH_CONFIDENCE_THRESHOLD = 0.8f; const val REVIEW_THRESHOLD = 0.5f }
    }

    data class NeedsReview(val partialText: String, val confidence: Float, val reason: String, val regions: List<TextRegion> = emptyList()) : OcrResult()
    data class Failure(val error: OcrError, val errorCode: OcrErrorCode = OcrErrorCode.UNKNOWN, val details: String? = null) : OcrResult()
}

data class OcrError(val message: String, val cause: Throwable? = null)

enum class OcrErrorCode { UNSUPPORTED_FORMAT, IMAGE_TOO_SMALL, IMAGE_TOO_LARGE, CORRUPTED_IMAGE, NO_TEXT_DETECTED, ENGINE_INIT_FAILED, TIMEOUT, OUT_OF_MEMORY, UNSUPPORTED_LANGUAGE, UNKNOWN }

enum class OcrConfidenceLevel {
    VERY_HIGH, HIGH, MEDIUM, LOW, VERY_LOW;
    companion object {
        fun fromScore(c: Float): OcrConfidenceLevel = when { c >= 0.9f -> VERY_HIGH; c >= 0.8f -> HIGH; c >= 0.6f -> MEDIUM; c >= 0.4f -> LOW; else -> VERY_LOW }
    }
}
