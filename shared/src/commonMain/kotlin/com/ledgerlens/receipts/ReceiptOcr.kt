package com.ledgerlens.receipts

/**
 * Interface for OCR text extraction from receipt images.
 *
 * Platform-specific implementations use native OCR engines:
 * - Android: ML Kit Text Recognition
 * - Desktop: Tesseract via Tess4j
 *
 * Usage:
 * ```
 * val ocr = ReceiptOcrFactory.create()
 * val result = ocr.extractText(imageBytes, OcrOptions())
 * when (result) {
 *     is OcrResult.Success -> processText(result.text)
 *     is OcrResult.NeedsReview -> requestUserReview(result)
 *     is OcrResult.Failure -> handleError(result.error)
 * }
 * ```
 */
interface ReceiptOcr {

    /**
     * Extract text from a receipt image.
     *
     * @param image Raw image bytes (JPEG, PNG, WebP, or BMP)
     * @param options OCR processing options
     * @return OCR result with extracted text or error
     */
    suspend fun extractText(image: ByteArray, options: OcrOptions = OcrOptions()): OcrResult

    /**
     * Check if the OCR engine is ready for use.
     *
     * Some engines require initialization (e.g., downloading models).
     */
    suspend fun isReady(): Boolean

    /**
     * Initialize the OCR engine if needed.
     *
     * This may download required models or initialize native libraries.
     * Should be called before first use if [isReady] returns false.
     */
    suspend fun initialize(): Boolean

    /**
     * Release resources held by the OCR engine.
     *
     * Should be called when OCR is no longer needed to free memory.
     */
    fun close()

    /**
     * Get supported image formats.
     */
    val supportedFormats: Set<ImageFormat>

    /**
     * Get the name of the underlying OCR engine.
     */
    val engineName: String

    /**
     * Get the version of the OCR engine.
     */
    val engineVersion: String
}

/**
 * Options for OCR text extraction.
 *
 * @property language Primary language for recognition (ISO 639-1 code)
 * @property recognitionMode Balance between speed and accuracy
 * @property detectOrientation Automatically detect and correct image orientation
 * @property preprocessImage Apply preprocessing (grayscale, contrast, etc.)
 * @property timeoutMs Maximum processing time in milliseconds (0 = no timeout)
 * @property minConfidence Minimum confidence threshold for accepting text
 * @property segmentationMode How to segment the image for text detection
 */
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

/**
 * Recognition mode affecting speed vs accuracy tradeoff.
 */
enum class RecognitionMode {
    /** Fastest recognition, may sacrifice accuracy */
    FAST,

    /** Balance between speed and accuracy (default) */
    BALANCED,

    /** Most accurate recognition, slower */
    ACCURATE
}

/**
 * How the OCR engine should segment the image for text detection.
 */
enum class SegmentationMode {
    /** Automatic segmentation (recommended for receipts) */
    AUTO,

    /** Treat as a single uniform block of text */
    SINGLE_BLOCK,

    /** Treat as a single text line */
    SINGLE_LINE,

    /** Treat as a single word */
    SINGLE_WORD,

    /** Sparse text - find as much text as possible */
    SPARSE_TEXT,

    /** Sparse text with OSD (orientation & script detection) */
    SPARSE_TEXT_OSD
}

/**
 * Supported image formats for OCR.
 */
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

        fun fromMimeType(mimeType: String): ImageFormat? {
            return entries.find { it.mimeType == mimeType }
        }

        val COMMON = setOf(JPEG, PNG, WEBP, BMP)
    }
}

/**
 * Security and processing limits for OCR.
 */
object OcrLimits {
    /** Maximum image file size in bytes (50 MB) */
    const val MAX_IMAGE_SIZE_BYTES = 50 * 1024 * 1024L

    /** Maximum image dimension in pixels */
    const val MAX_IMAGE_DIMENSION = 10_000

    /** Minimum image dimension for reliable OCR */
    const val MIN_IMAGE_DIMENSION = 50

    /** Maximum processing time in milliseconds (60 seconds) */
    const val MAX_TIMEOUT_MS = 60_000L

    /** Optimal image width for OCR processing */
    const val OPTIMAL_WIDTH = 2000

    /** Optimal DPI for OCR processing */
    const val OPTIMAL_DPI = 300
}
