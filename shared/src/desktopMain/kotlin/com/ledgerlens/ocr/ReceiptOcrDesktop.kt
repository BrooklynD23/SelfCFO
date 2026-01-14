package com.ledgerlens.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Desktop implementation of ReceiptOcr using Tesseract via Tess4j.
 */
class ReceiptOcrDesktop(private val config: OcrEngineConfig = OcrEngineConfig()) : ReceiptOcr {
    private var tesseract: Tesseract? = null
    private var initialized = false

    override val engineName: String = "Tesseract"
    override val engineVersion: String get() = tesseract?.version() ?: "unknown"
    override val supportedFormats: Set<ImageFormat> = setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.TIFF, ImageFormat.BMP, ImageFormat.GIF)

    override suspend fun isReady(): Boolean = initialized && tesseract != null

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            tesseract = Tesseract().apply {
                setDatapath(config.dataPath ?: System.getenv("TESSDATA_PREFIX") ?: "tessdata")
                setLanguage(config.languages.firstOrNull() ?: "eng")
                setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO)
                setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY)
            }
            initialized = true
            true
        } catch (e: Exception) { initialized = false; false }
    }

    override suspend fun extractText(image: ByteArray, options: OcrOptions): OcrResult = withContext(Dispatchers.IO) {
        try {
            val tess = tesseract ?: run {
                if (!initialize()) return@withContext OcrResult.Failure(OcrError("Failed to initialize Tesseract"), OcrErrorCode.ENGINE_INIT_FAILED)
                tesseract!!
            }

            if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) return@withContext OcrResult.Failure(OcrError("Image exceeds maximum size"), OcrErrorCode.IMAGE_TOO_LARGE)

            val bufferedImage = try { ByteArrayInputStream(image).use { ImageIO.read(it) } }
            catch (e: Exception) { return@withContext OcrResult.Failure(OcrError("Failed to decode: ${e.message}", e), OcrErrorCode.CORRUPTED_IMAGE) }
                ?: return@withContext OcrResult.Failure(OcrError("Unsupported format"), OcrErrorCode.UNSUPPORTED_FORMAT)

            if (bufferedImage.width < OcrLimits.MIN_IMAGE_DIMENSION || bufferedImage.height < OcrLimits.MIN_IMAGE_DIMENSION)
                return@withContext OcrResult.Failure(OcrError("Image too small"), OcrErrorCode.IMAGE_TOO_SMALL)

            configureTesseract(tess, options)
            val startTime = System.currentTimeMillis()
            val words = try { tess.getWords(bufferedImage, ITessAPI.TessPageIteratorLevel.RIL_WORD) }
            catch (e: Exception) { return@withContext OcrResult.Failure(OcrError("OCR failed: ${e.message}", e), OcrErrorCode.UNKNOWN) }

            val regions = words.mapIndexed { _, word ->
                val rect = word.boundingBox
                TextRegion(word.text, BoundingBox(rect.x, rect.y, rect.width, rect.height), word.confidence / 100f)
            }.filter { it.confidence >= options.minConfidence }

            if (regions.isEmpty()) return@withContext OcrResult.Failure(OcrError("No text detected"), OcrErrorCode.NO_TEXT_DETECTED)

            val fullText = tess.doOCR(bufferedImage)
            val avgConfidence = regions.map { it.confidence }.average().toFloat()
            val lines = groupIntoLines(regions)
            val processingTime = System.currentTimeMillis() - startTime

            if (avgConfidence < OcrResult.Success.REVIEW_THRESHOLD)
                return@withContext OcrResult.NeedsReview(fullText, avgConfidence, "Low confidence (${(avgConfidence * 100).toInt()}%)", regions)

            OcrResult.Success(fullText.trim(), avgConfidence, regions, lines, processingTimeMs = processingTime, imageWidth = bufferedImage.width, imageHeight = bufferedImage.height)
        } catch (e: Exception) { OcrResult.Failure(OcrError("Error: ${e.message}", e), OcrErrorCode.UNKNOWN) }
    }

    private fun configureTesseract(tess: Tesseract, options: OcrOptions) {
        tess.setPageSegMode(when (options.segmentationMode) {
            SegmentationMode.AUTO -> ITessAPI.TessPageSegMode.PSM_AUTO
            SegmentationMode.SINGLE_BLOCK -> ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK
            SegmentationMode.SINGLE_LINE -> ITessAPI.TessPageSegMode.PSM_SINGLE_LINE
            SegmentationMode.SINGLE_WORD -> ITessAPI.TessPageSegMode.PSM_SINGLE_WORD
            SegmentationMode.SPARSE_TEXT -> ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT
            SegmentationMode.SPARSE_TEXT_OSD -> ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT_OSD
        })
        tess.setOcrEngineMode(when (options.recognitionMode) {
            RecognitionMode.FAST -> ITessAPI.TessOcrEngineMode.OEM_TESSERACT_ONLY
            RecognitionMode.BALANCED -> ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY
            RecognitionMode.ACCURATE -> ITessAPI.TessOcrEngineMode.OEM_TESSERACT_LSTM_COMBINED
        })
    }

    private fun groupIntoLines(regions: List<TextRegion>): List<TextLine> {
        if (regions.isEmpty()) return emptyList()
        val sorted = regions.sortedWith(compareBy({ it.boundingBox.y }, { it.boundingBox.x }))
        val lines = mutableListOf<MutableList<TextRegion>>()
        var currentLine = mutableListOf<TextRegion>()
        var currentY = sorted.first().boundingBox.y

        for (region in sorted) {
            if (kotlin.math.abs(region.boundingBox.y - currentY) > 10) {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = mutableListOf()
                currentY = region.boundingBox.y
            }
            currentLine.add(region)
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines.mapIndexed { idx, lr -> TextLine(lr.map { it.copy(lineNumber = idx + 1) }, idx + 1) }
    }

    override fun close() { tesseract = null; initialized = false }
}

actual object ReceiptOcrFactory {
    actual fun create(config: OcrEngineConfig): ReceiptOcr = ReceiptOcrDesktop(config)
}
