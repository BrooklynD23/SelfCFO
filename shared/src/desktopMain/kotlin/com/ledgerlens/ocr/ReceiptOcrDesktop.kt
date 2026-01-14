package com.ledgerlens.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class ReceiptOcrDesktop(private val config: OcrEngineConfig = OcrEngineConfig()) : ReceiptOcr {
    private var tesseract: Tesseract? = null
    private var initialized = false
    override val engineName = "Tesseract"
    override val engineVersion get() = tesseract?.version() ?: "unknown"
    override val supportedFormats = setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.TIFF, ImageFormat.BMP, ImageFormat.GIF)

    override suspend fun isReady() = initialized && tesseract != null
    override suspend fun initialize() = withContext(Dispatchers.IO) { try { tesseract = Tesseract().apply { setDatapath(config.dataPath ?: System.getenv("TESSDATA_PREFIX") ?: "tessdata"); setLanguage(config.languages.firstOrNull() ?: "eng"); setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO); setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY) }; initialized = true; true } catch (e: Exception) { initialized = false; false } }

    override suspend fun extractText(image: ByteArray, options: OcrOptions) = withContext(Dispatchers.IO) {
        try {
            val tess = tesseract ?: run { if (!initialize()) return@withContext OcrResult.Failure(OcrError("Init failed"), OcrErrorCode.ENGINE_INIT_FAILED); tesseract!! }
            if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) return@withContext OcrResult.Failure(OcrError("Image too large"), OcrErrorCode.IMAGE_TOO_LARGE)
            val img = try { ByteArrayInputStream(image).use { ImageIO.read(it) } } catch (e: Exception) { return@withContext OcrResult.Failure(OcrError("Decode failed", e), OcrErrorCode.CORRUPTED_IMAGE) } ?: return@withContext OcrResult.Failure(OcrError("Unsupported format"), OcrErrorCode.UNSUPPORTED_FORMAT)
            if (img.width < OcrLimits.MIN_IMAGE_DIMENSION || img.height < OcrLimits.MIN_IMAGE_DIMENSION) return@withContext OcrResult.Failure(OcrError("Image too small"), OcrErrorCode.IMAGE_TOO_SMALL)
            val start = System.currentTimeMillis()
            val words = try { tess.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD) } catch (e: Exception) { return@withContext OcrResult.Failure(OcrError("OCR failed", e), OcrErrorCode.UNKNOWN) }
            val regions = words.map { val r = it.boundingBox; TextRegion(it.text, BoundingBox(r.x, r.y, r.width, r.height), it.confidence / 100f) }.filter { it.confidence >= options.minConfidence }
            if (regions.isEmpty()) return@withContext OcrResult.Failure(OcrError("No text"), OcrErrorCode.NO_TEXT_DETECTED)
            val text = tess.doOCR(img); val conf = regions.map { it.confidence }.average().toFloat(); val lines = groupLines(regions)
            if (conf < OcrResult.Success.REVIEW_THRESHOLD) return@withContext OcrResult.NeedsReview(text, conf, "Low confidence", regions)
            OcrResult.Success(text.trim(), conf, regions, lines, System.currentTimeMillis() - start, img.width, img.height)
        } catch (e: Exception) { OcrResult.Failure(OcrError("Error: ${e.message}", e), OcrErrorCode.UNKNOWN) }
    }

    private fun groupLines(regions: List<TextRegion>): List<TextLine> { if (regions.isEmpty()) return emptyList(); val sorted = regions.sortedWith(compareBy({ it.boundingBox.y }, { it.boundingBox.x })); val lines = mutableListOf<MutableList<TextRegion>>(); var cur = mutableListOf<TextRegion>(); var y = sorted.first().boundingBox.y; for (r in sorted) { if (kotlin.math.abs(r.boundingBox.y - y) > 10) { if (cur.isNotEmpty()) lines.add(cur); cur = mutableListOf(); y = r.boundingBox.y }; cur.add(r) }; if (cur.isNotEmpty()) lines.add(cur); return lines.mapIndexed { i, lr -> TextLine(lr.map { it.copy(lineNumber = i + 1) }, i + 1) } }
    override fun close() { tesseract = null; initialized = false }
}

actual object ReceiptOcrFactory { actual fun create(config: OcrEngineConfig) = ReceiptOcrDesktop(config) }
