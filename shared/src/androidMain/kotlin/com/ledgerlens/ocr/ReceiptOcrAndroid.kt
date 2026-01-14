package com.ledgerlens.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android implementation of ReceiptOcr using ML Kit Text Recognition.
 */
class ReceiptOcrAndroid(private val config: OcrEngineConfig = OcrEngineConfig()) : ReceiptOcr {
    private var recognizer: TextRecognizer? = null
    private var initialized = false

    override val engineName: String = "ML Kit"
    override val engineVersion: String = "18.0.2"
    override val supportedFormats: Set<ImageFormat> = setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.BMP)

    override suspend fun isReady(): Boolean = initialized && recognizer != null

    override suspend fun initialize(): Boolean = try {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        initialized = true; true
    } catch (e: Exception) { initialized = false; false }

    override suspend fun extractText(image: ByteArray, options: OcrOptions): OcrResult = withContext(Dispatchers.IO) {
        try {
            val textRecognizer = recognizer ?: run {
                if (!initialize()) return@withContext OcrResult.Failure(OcrError("Failed to initialize ML Kit"), OcrErrorCode.ENGINE_INIT_FAILED)
                recognizer!!
            }

            if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) return@withContext OcrResult.Failure(OcrError("Image exceeds maximum size"), OcrErrorCode.IMAGE_TOO_LARGE)

            val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
                ?: return@withContext OcrResult.Failure(OcrError("Failed to decode image"), OcrErrorCode.CORRUPTED_IMAGE)

            if (bitmap.width < OcrLimits.MIN_IMAGE_DIMENSION || bitmap.height < OcrLimits.MIN_IMAGE_DIMENSION)
                return@withContext OcrResult.Failure(OcrError("Image too small"), OcrErrorCode.IMAGE_TOO_SMALL)

            val startTime = System.currentTimeMillis()
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val result = suspendCancellableCoroutine { continuation ->
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            } ?: return@withContext OcrResult.Failure(OcrError("ML Kit processing failed"), OcrErrorCode.UNKNOWN)

            if (result.text.isBlank()) return@withContext OcrResult.Failure(OcrError("No text detected"), OcrErrorCode.NO_TEXT_DETECTED)

            val regions = mutableListOf<TextRegion>()
            val lines = mutableListOf<TextLine>()
            var lineNumber = 0

            for (block in result.textBlocks) for (line in block.lines) {
                lineNumber++
                val lineRegions = mutableListOf<TextRegion>()
                for (element in line.elements) {
                    val boundingBox = element.boundingBox?.let { BoundingBox(it.left, it.top, it.width(), it.height()) } ?: BoundingBox.EMPTY
                    val region = TextRegion(element.text, boundingBox, element.confidence ?: 0.8f, lineNumber)
                    if (region.confidence >= options.minConfidence) { regions.add(region); lineRegions.add(region) }
                }
                if (lineRegions.isNotEmpty()) lines.add(TextLine(lineRegions, lineNumber))
            }

            val avgConfidence = if (regions.isNotEmpty()) regions.map { it.confidence }.average().toFloat() else 0.8f
            val processingTime = System.currentTimeMillis() - startTime

            if (avgConfidence < OcrResult.Success.REVIEW_THRESHOLD)
                return@withContext OcrResult.NeedsReview(result.text, avgConfidence, "Low confidence (${(avgConfidence * 100).toInt()}%)", regions)

            OcrResult.Success(result.text, avgConfidence, regions, lines, processingTimeMs = processingTime, imageWidth = bitmap.width, imageHeight = bitmap.height)
        } catch (e: Exception) { OcrResult.Failure(OcrError("Error: ${e.message}", e), OcrErrorCode.UNKNOWN) }
    }

    override fun close() { recognizer?.close(); recognizer = null; initialized = false }
}

actual object ReceiptOcrFactory {
    actual fun create(config: OcrEngineConfig): ReceiptOcr = ReceiptOcrAndroid(config)
}
