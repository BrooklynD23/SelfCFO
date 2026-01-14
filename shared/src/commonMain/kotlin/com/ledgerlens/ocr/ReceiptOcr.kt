package com.ledgerlens.ocr

/**
 * Interface for OCR text extraction from receipt images.
 *
 * Platform implementations:
 * - Android: ML Kit Text Recognition
 * - Desktop: Tesseract via Tess4j
 */
interface ReceiptOcr {
    suspend fun extractText(image: ByteArray, options: OcrOptions = OcrOptions()): OcrResult
    suspend fun isReady(): Boolean
    suspend fun initialize(): Boolean
    fun close()

    val supportedFormats: Set<ImageFormat>
    val engineName: String
    val engineVersion: String
}

data class OcrOptions(
    val language: String = "en",
    val recognitionMode: RecognitionMode = RecognitionMode.BALANCED,
    val detectOrientation: Boolean = true,
    val preprocessImage: Boolean = true,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    val segmentationMode: SegmentationMode = SegmentationMode.AUTO
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_MIN_CONFIDENCE = 0.3f

        val FAST = OcrOptions(
            recognitionMode = RecognitionMode.FAST,
            preprocessImage = false
        )

        val ACCURATE = OcrOptions(
            recognitionMode = RecognitionMode.ACCURATE,
            detectOrientation = true,
            preprocessImage = true
        )
    }
}

enum class RecognitionMode { FAST, BALANCED, ACCURATE }

enum class SegmentationMode {
    AUTO, SINGLE_BLOCK, SINGLE_LINE, SINGLE_WORD, SPARSE_TEXT, SPARSE_TEXT_OSD
}

enum class ImageFormat(val mimeType: String, vararg val extensions: String) {
    JPEG("image/jpeg", "jpg", "jpeg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    BMP("image/bmp", "bmp"),
    TIFF("image/tiff", "tiff", "tif"),
    GIF("image/gif", "gif");

    companion object {
        fun fromExtension(extension: String): ImageFormat? {
            val ext = extension.lowercase().removePrefix(".")
            return entries.find { format -> ext in format.extensions }
        }

        fun fromMimeType(mimeType: String): ImageFormat? =
            entries.find { it.mimeType == mimeType }

        val COMMON = setOf(JPEG, PNG, WEBP, BMP)
    }
}

object OcrLimits {
    const val MAX_IMAGE_SIZE_BYTES = 50 * 1024 * 1024L
    const val MAX_IMAGE_DIMENSION = 10_000
    const val MIN_IMAGE_DIMENSION = 50
    const val MAX_TIMEOUT_MS = 60_000L
    const val OPTIMAL_WIDTH = 2000
    const val OPTIMAL_DPI = 300
}
