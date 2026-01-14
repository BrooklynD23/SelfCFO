package com.ledgerlens.receipts

/**
 * Result of OCR text extraction from an image.
 *
 * This is a sealed class hierarchy representing either successful
 * extraction or various failure modes.
 */
sealed class OcrResult {

    /**
     * Successful OCR extraction.
     *
     * @property text The full extracted text
     * @property confidence Overall confidence score (0.0 to 1.0)
     * @property regions Individual text regions with positions
     * @property lines Text organized by lines
     * @property blocks Text organized by semantic blocks
     * @property processingTimeMs Time taken for OCR in milliseconds
     * @property imageWidth Original image width in pixels
     * @property imageHeight Original image height in pixels
     */
    data class Success(
        val text: String,
        val confidence: Float,
        val regions: List<TextRegion>,
        val lines: List<TextLine> = emptyList(),
        val blocks: List<TextBlock> = emptyList(),
        val processingTimeMs: Long = 0,
        val imageWidth: Int = 0,
        val imageHeight: Int = 0
    ) : OcrResult() {
        /**
         * Whether the extraction has high enough confidence for auto-processing.
         */
        val isHighConfidence: Boolean get() = confidence >= HIGH_CONFIDENCE_THRESHOLD

        /**
         * Whether the result needs user review due to low confidence.
         */
        val needsReview: Boolean get() = confidence < REVIEW_THRESHOLD

        /**
         * Count of recognized text regions.
         */
        val regionCount: Int get() = regions.size

        /**
         * Count of recognized lines.
         */
        val lineCount: Int get() = lines.size

        /**
         * Get text from a specific line number (1-indexed).
         */
        fun getLine(lineNumber: Int): TextLine? =
            lines.find { it.lineNumber == lineNumber }

        /**
         * Find regions containing specific text (case-insensitive).
         */
        fun findRegions(searchText: String): List<TextRegion> =
            regions.filter { it.text.contains(searchText, ignoreCase = true) }

        /**
         * Get regions within a confidence range.
         */
        fun getRegionsByConfidence(minConfidence: Float, maxConfidence: Float = 1.0f): List<TextRegion> =
            regions.filter { it.confidence in minConfidence..maxConfidence }

        companion object {
            const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
            const val REVIEW_THRESHOLD = 0.5f
        }
    }

    /**
     * OCR extraction needs manual review due to quality issues.
     *
     * @property partialText Any text that was partially extracted
     * @property confidence Low confidence score
     * @property reason Explanation of why review is needed
     * @property regions Regions that were recognized (may be incomplete)
     */
    data class NeedsReview(
        val partialText: String,
        val confidence: Float,
        val reason: String,
        val regions: List<TextRegion> = emptyList()
    ) : OcrResult()

    /**
     * OCR extraction failed.
     *
     * @property error The error that occurred
     * @property errorCode Categorized error code
     * @property details Additional error details
     */
    data class Failure(
        val error: OcrError,
        val errorCode: OcrErrorCode = OcrErrorCode.UNKNOWN,
        val details: String? = null
    ) : OcrResult()
}

/**
 * Error information for OCR failures.
 *
 * @property message Human-readable error message
 * @property cause Underlying exception if available
 */
data class OcrError(
    val message: String,
    val cause: Throwable? = null
)

/**
 * Categorized error codes for OCR failures.
 */
enum class OcrErrorCode {
    /** Image format not supported */
    UNSUPPORTED_FORMAT,

    /** Image is too small for OCR */
    IMAGE_TOO_SMALL,

    /** Image is too large to process */
    IMAGE_TOO_LARGE,

    /** Image is corrupted or unreadable */
    CORRUPTED_IMAGE,

    /** No text detected in the image */
    NO_TEXT_DETECTED,

    /** OCR engine initialization failed */
    ENGINE_INIT_FAILED,

    /** Processing timeout exceeded */
    TIMEOUT,

    /** Out of memory during processing */
    OUT_OF_MEMORY,

    /** Language not supported */
    UNSUPPORTED_LANGUAGE,

    /** Unknown error */
    UNKNOWN
}

/**
 * Confidence levels for OCR results.
 */
enum class OcrConfidenceLevel {
    /** Very high confidence (>= 0.9) */
    VERY_HIGH,

    /** High confidence (0.8 - 0.9) */
    HIGH,

    /** Medium confidence (0.6 - 0.8) */
    MEDIUM,

    /** Low confidence (0.4 - 0.6) */
    LOW,

    /** Very low confidence (< 0.4) */
    VERY_LOW;

    companion object {
        fun fromScore(confidence: Float): OcrConfidenceLevel = when {
            confidence >= 0.9f -> VERY_HIGH
            confidence >= 0.8f -> HIGH
            confidence >= 0.6f -> MEDIUM
            confidence >= 0.4f -> LOW
            else -> VERY_LOW
        }
    }
}
