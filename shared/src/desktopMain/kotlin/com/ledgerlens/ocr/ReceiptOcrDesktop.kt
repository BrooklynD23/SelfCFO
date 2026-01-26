package com.ledgerlens.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop implementation of ReceiptOcr.
 *
 * TODO: Integrate Tesseract4J (net.sourceforge.tess4j:tess4j) for actual OCR functionality.
 * Current implementation is a stub that returns a "not implemented" result.
 *
 * To enable Tesseract OCR:
 * 1. Add tess4j dependency to shared/build.gradle.kts desktopMain dependencies
 * 2. Install Tesseract on the system and set TESSDATA_PREFIX environment variable
 * 3. Implement actual OCR using Tesseract class from tess4j
 */
class ReceiptOcrDesktop(private val config: OcrEngineConfig = OcrEngineConfig()) : ReceiptOcr {
    private var initialized = false

    override val engineName = "Tesseract (Stub)"
    override val engineVersion = "not-installed"
    override val supportedFormats =
        setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.TIFF, ImageFormat.BMP, ImageFormat.GIF)

    override suspend fun isReady() = initialized

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        // Stub: Always succeeds but OCR won't work
        initialized = true
        true
    }

    override suspend fun extractText(image: ByteArray, options: OcrOptions): OcrResult = withContext(Dispatchers.IO) {
        // Validate basic image constraints
        if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) {
            return@withContext OcrResult.Failure(
                OcrError("Image exceeds maximum size of ${OcrLimits.MAX_IMAGE_SIZE_BYTES} bytes"),
                OcrErrorCode.IMAGE_TOO_LARGE
            )
        }

        if (image.isEmpty()) {
            return@withContext OcrResult.Failure(
                OcrError("Empty image data"),
                OcrErrorCode.CORRUPTED_IMAGE
            )
        }

        // Return stub result indicating OCR is not yet implemented
        OcrResult.Failure(
            OcrError(
                "Desktop OCR not yet implemented. " +
                    "Tesseract4J dependency needs to be added. " +
                    "See ReceiptOcrDesktop.kt for setup instructions."
            ),
            OcrErrorCode.ENGINE_INIT_FAILED
        )
    }

    override fun close() {
        initialized = false
    }
}

actual object ReceiptOcrFactory {
    actual fun create(config: OcrEngineConfig): ReceiptOcr = ReceiptOcrDesktop(config)
}
