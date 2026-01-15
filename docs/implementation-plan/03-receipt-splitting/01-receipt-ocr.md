# 01: Receipt OCR

## Overview

Implement camera capture and OCR extraction using platform-specific libraries (ML Kit on Android, Tesseract on Desktop) per ADR-002.

---

## Implementation Steps

### Step 1: OCR Service Interface

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/receipt/OcrService.kt
package com.ledgerlens.receipt

/**
 * Platform-agnostic OCR service.
 * Implemented via expect/actual pattern.
 */
expect class OcrService {
    /**
     * Extract text from an image.
     * @param imageData Raw image bytes (JPEG/PNG)
     * @return OCR result with text and confidence
     */
    suspend fun extractText(imageData: ByteArray): OcrResult

    /**
     * Extract text with block/line structure preserved.
     */
    suspend fun extractStructuredText(imageData: ByteArray): StructuredOcrResult
}

data class OcrResult(
    val rawText: String,
    val confidence: Float,
    val processingTimeMs: Long
)

data class StructuredOcrResult(
    val blocks: List<TextBlock>,
    val rawText: String,
    val confidence: Float
)

data class TextBlock(
    val text: String,
    val lines: List<TextLine>,
    val boundingBox: BoundingBox,
    val confidence: Float
)

data class TextLine(
    val text: String,
    val words: List<TextWord>,
    val boundingBox: BoundingBox
)

data class TextWord(
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float
)

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
```

### Step 2: Android Implementation (ML Kit)

```kotlin
// shared/src/androidMain/kotlin/com/ledgerlens/receipt/OcrService.kt
package com.ledgerlens.receipt

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class OcrService {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    actual suspend fun extractText(imageData: ByteArray): OcrResult {
        val startTime = System.currentTimeMillis()

        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val processingTime = System.currentTimeMillis() - startTime
                    continuation.resume(
                        OcrResult(
                            rawText = visionText.text,
                            confidence = calculateConfidence(visionText),
                            processingTimeMs = processingTime
                        )
                    )
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    actual suspend fun extractStructuredText(imageData: ByteArray): StructuredOcrResult {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            lines = block.lines.map { line ->
                                TextLine(
                                    text = line.text,
                                    words = line.elements.map { element ->
                                        TextWord(
                                            text = element.text,
                                            boundingBox = element.boundingBox?.toBoundingBox()
                                                ?: BoundingBox(0, 0, 0, 0),
                                            confidence = element.confidence ?: 0f
                                        )
                                    },
                                    boundingBox = line.boundingBox?.toBoundingBox()
                                        ?: BoundingBox(0, 0, 0, 0)
                                )
                            },
                            boundingBox = block.boundingBox?.toBoundingBox()
                                ?: BoundingBox(0, 0, 0, 0),
                            confidence = block.lines.mapNotNull {
                                it.elements.mapNotNull { e -> e.confidence }.average().toFloat()
                            }.average().toFloat()
                        )
                    }

                    continuation.resume(
                        StructuredOcrResult(
                            blocks = blocks,
                            rawText = visionText.text,
                            confidence = calculateConfidence(visionText)
                        )
                    )
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    private fun calculateConfidence(visionText: com.google.mlkit.vision.text.Text): Float {
        val confidences = visionText.textBlocks.flatMap { block ->
            block.lines.flatMap { line ->
                line.elements.mapNotNull { it.confidence }
            }
        }
        return if (confidences.isEmpty()) 0f else confidences.average().toFloat()
    }

    private fun android.graphics.Rect.toBoundingBox() = BoundingBox(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}
```

### Step 3: Desktop Implementation (Tesseract)

```kotlin
// shared/src/desktopMain/kotlin/com/ledgerlens/receipt/OcrService.kt
package com.ledgerlens.receipt

import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream

actual class OcrService {
    private val tesseract = Tesseract().apply {
        setDatapath(getTessDataPath())
        setLanguage("eng")
        setPageSegMode(1) // Automatic page segmentation with OSD
    }

    actual suspend fun extractText(imageData: ByteArray): OcrResult {
        val startTime = System.currentTimeMillis()

        val image = ImageIO.read(ByteArrayInputStream(imageData))
        val text = tesseract.doOCR(image)

        return OcrResult(
            rawText = text,
            confidence = 0.8f, // Tesseract confidence requires additional API calls
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    actual suspend fun extractStructuredText(imageData: ByteArray): StructuredOcrResult {
        val image = ImageIO.read(ByteArrayInputStream(imageData))
        val rawText = tesseract.doOCR(image)

        // Parse into blocks/lines from raw text
        val blocks = parseIntoBlocks(rawText)

        return StructuredOcrResult(
            blocks = blocks,
            rawText = rawText,
            confidence = 0.8f
        )
    }

    private fun parseIntoBlocks(text: String): List<TextBlock> {
        // Simple line-based parsing for desktop
        val lines = text.lines().filter { it.isNotBlank() }

        return listOf(
            TextBlock(
                text = text,
                lines = lines.mapIndexed { index, line ->
                    TextLine(
                        text = line,
                        words = line.split("\\s+".toRegex()).map { word ->
                            TextWord(
                                text = word,
                                boundingBox = BoundingBox(0, 0, 0, 0),
                                confidence = 0.8f
                            )
                        },
                        boundingBox = BoundingBox(0, index * 20, 0, (index + 1) * 20)
                    )
                },
                boundingBox = BoundingBox(0, 0, 0, 0),
                confidence = 0.8f
            )
        )
    }

    private fun getTessDataPath(): String {
        // Platform-specific tessdata location
        return System.getenv("TESSDATA_PREFIX")
            ?: "${System.getProperty("user.home")}/.ledgerlens/tessdata"
    }
}
```

### Step 4: Receipt Capture Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/receipt/ReceiptCaptureService.kt
package com.ledgerlens.receipt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ReceiptCaptureService(
    private val ocrService: OcrService,
    private val imageProcessor: ImageProcessor
) {

    /**
     * Process a receipt image and extract text.
     */
    suspend fun processReceipt(imageData: ByteArray): ReceiptScanResult {
        // 1. Pre-process image for better OCR
        val processedImage = imageProcessor.preprocess(imageData)

        // 2. Extract structured text
        val ocrResult = ocrService.extractStructuredText(processedImage)

        // 3. Validate result quality
        val quality = assessQuality(ocrResult)

        return ReceiptScanResult(
            ocrResult = ocrResult,
            quality = quality,
            needsRetry = quality < ScanQuality.ACCEPTABLE
        )
    }

    /**
     * Process with retry hints.
     */
    fun processWithGuidance(imageData: ByteArray): Flow<ScanProgress> = flow {
        emit(ScanProgress.Processing)

        val result = processReceipt(imageData)

        if (result.needsRetry) {
            emit(ScanProgress.NeedsRetry(
                reason = determineRetryReason(result),
                hint = getRetryHint(result)
            ))
        } else {
            emit(ScanProgress.Complete(result))
        }
    }

    private fun assessQuality(ocrResult: StructuredOcrResult): ScanQuality {
        return when {
            ocrResult.confidence >= 0.9f -> ScanQuality.EXCELLENT
            ocrResult.confidence >= 0.7f -> ScanQuality.GOOD
            ocrResult.confidence >= 0.5f -> ScanQuality.ACCEPTABLE
            else -> ScanQuality.POOR
        }
    }

    private fun determineRetryReason(result: ReceiptScanResult): RetryReason {
        return when {
            result.ocrResult.rawText.isBlank() -> RetryReason.NO_TEXT_DETECTED
            result.ocrResult.confidence < 0.3f -> RetryReason.BLURRY_IMAGE
            else -> RetryReason.LOW_QUALITY
        }
    }

    private fun getRetryHint(result: ReceiptScanResult): String {
        return when (determineRetryReason(result)) {
            RetryReason.NO_TEXT_DETECTED -> "No text detected. Ensure the receipt is visible and well-lit."
            RetryReason.BLURRY_IMAGE -> "Image is blurry. Hold the camera steady and ensure good lighting."
            RetryReason.LOW_QUALITY -> "Text quality is low. Try moving closer to the receipt."
        }
    }
}

data class ReceiptScanResult(
    val ocrResult: StructuredOcrResult,
    val quality: ScanQuality,
    val needsRetry: Boolean
)

enum class ScanQuality {
    EXCELLENT, GOOD, ACCEPTABLE, POOR
}

enum class RetryReason {
    NO_TEXT_DETECTED, BLURRY_IMAGE, LOW_QUALITY
}

sealed class ScanProgress {
    object Processing : ScanProgress()
    data class NeedsRetry(val reason: RetryReason, val hint: String) : ScanProgress()
    data class Complete(val result: ReceiptScanResult) : ScanProgress()
}
```

### Step 5: Image Preprocessor

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/receipt/ImageProcessor.kt
package com.ledgerlens.receipt

/**
 * Image preprocessing for better OCR results.
 */
expect class ImageProcessor {
    /**
     * Preprocess image for OCR.
     * - Resize if too large
     * - Convert to grayscale
     * - Increase contrast
     * - Deskew if needed
     */
    suspend fun preprocess(imageData: ByteArray): ByteArray

    /**
     * Auto-crop to receipt bounds.
     */
    suspend fun autoCrop(imageData: ByteArray): ByteArray
}

// Android implementation
// shared/src/androidMain/kotlin/com/ledgerlens/receipt/ImageProcessor.kt
actual class ImageProcessor {
    actual suspend fun preprocess(imageData: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)

        // Resize if too large (max 2048px on longest side)
        val resized = resizeIfNeeded(bitmap, 2048)

        // Convert to grayscale for better OCR
        val grayscale = toGrayscale(resized)

        // Increase contrast
        val enhanced = enhanceContrast(grayscale)

        return bitmapToBytes(enhanced)
    }

    actual suspend fun autoCrop(imageData: ByteArray): ByteArray {
        // Use edge detection to find receipt bounds
        // Implementation depends on OpenCV or custom algorithm
        return imageData // Fallback: return original
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        // Increase contrast by 50%
        val contrast = 1.5f
        val translate = (-.5f * contrast + .5f) * 255f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }
}
```

---

## Acceptance Criteria

- [ ] ML Kit integration works on Android
- [ ] Tesseract integration works on Desktop
- [ ] Structured text extraction preserves line/block structure
- [ ] Image preprocessing improves OCR accuracy
- [ ] Quality assessment provides useful feedback
- [ ] Retry hints guide user to better captures

---

## Testing

### Unit Tests
```kotlin
class OcrServiceTest {
    @Test
    fun `extractText returns non-empty result for valid receipt image`()

    @Test
    fun `extractStructuredText preserves line structure`()

    @Test
    fun `low quality image returns low confidence`()
}

class ImageProcessorTest {
    @Test
    fun `preprocess resizes large images`()

    @Test
    fun `preprocess converts to grayscale`()
}
```

### Test Images
- Clear receipt (high quality)
- Blurry receipt (low quality)
- Rotated receipt
- Receipt with shadows
- Handwritten receipt

---

## Estimated Complexity

**High** - Platform-specific OCR implementations with image processing.

