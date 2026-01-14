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

class ReceiptOcrAndroid(private val config: OcrEngineConfig = OcrEngineConfig()) : ReceiptOcr {
    private var recognizer: TextRecognizer? = null
    private var initialized = false
    override val engineName = "ML Kit"
    override val engineVersion = "18.0.2"
    override val supportedFormats = setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.BMP)

    override suspend fun isReady() = initialized && recognizer != null
    override suspend fun initialize() = try { recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); initialized = true; true } catch (e: Exception) { initialized = false; false }

    override suspend fun extractText(image: ByteArray, options: OcrOptions) = withContext(Dispatchers.IO) {
        try {
            val rec = recognizer ?: run { if (!initialize()) return@withContext OcrResult.Failure(OcrError("Init failed"), OcrErrorCode.ENGINE_INIT_FAILED); recognizer!! }
            if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) return@withContext OcrResult.Failure(OcrError("Image too large"), OcrErrorCode.IMAGE_TOO_LARGE)
            val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size) ?: return@withContext OcrResult.Failure(OcrError("Decode failed"), OcrErrorCode.CORRUPTED_IMAGE)
            if (bitmap.width < OcrLimits.MIN_IMAGE_DIMENSION || bitmap.height < OcrLimits.MIN_IMAGE_DIMENSION) return@withContext OcrResult.Failure(OcrError("Image too small"), OcrErrorCode.IMAGE_TOO_SMALL)
            val startTime = System.currentTimeMillis()
            val result = suspendCancellableCoroutine { cont -> rec.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) } } ?: return@withContext OcrResult.Failure(OcrError("ML Kit failed"), OcrErrorCode.UNKNOWN)
            if (result.text.isBlank()) return@withContext OcrResult.Failure(OcrError("No text detected"), OcrErrorCode.NO_TEXT_DETECTED)
            val regions = mutableListOf<TextRegion>(); val lines = mutableListOf<TextLine>(); var lineNum = 0
            for (block in result.textBlocks) for (line in block.lines) { lineNum++; val lr = mutableListOf<TextRegion>()
                for (el in line.elements) { val bb = el.boundingBox?.let { BoundingBox(it.left, it.top, it.width(), it.height()) } ?: BoundingBox.EMPTY; val r = TextRegion(el.text, bb, el.confidence ?: 0.8f, lineNum); if (r.confidence >= options.minConfidence) { regions.add(r); lr.add(r) } }
                if (lr.isNotEmpty()) lines.add(TextLine(lr, lineNum)) }
            val avgConf = if (regions.isNotEmpty()) regions.map { it.confidence }.average().toFloat() else 0.8f
            if (avgConf < OcrResult.Success.REVIEW_THRESHOLD) return@withContext OcrResult.NeedsReview(result.text, avgConf, "Low confidence", regions)
            OcrResult.Success(result.text, avgConf, regions, lines, processingTimeMs = System.currentTimeMillis() - startTime, imageWidth = bitmap.width, imageHeight = bitmap.height)
        } catch (e: Exception) { OcrResult.Failure(OcrError("Error: ${e.message}", e), OcrErrorCode.UNKNOWN) }
    }

    override fun close() { recognizer?.close(); recognizer = null; initialized = false }
}

actual object ReceiptOcrFactory { actual fun create(config: OcrEngineConfig) = ReceiptOcrAndroid(config) }
