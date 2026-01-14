package com.ledgerlens.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ImagePreprocessorAndroid : ImagePreprocessor {
    override suspend fun preprocess(image: ByteArray, options: PreprocessOptions) = withContext(Dispatchers.IO) {
        try {
            val bmp = BitmapFactory.decodeByteArray(image, 0, image.size) ?: return@withContext PreprocessResult.Failure("Decode failed")
            val originalSize = ImageSize(bmp.width, bmp.height)
            val ops = mutableListOf<String>()
            var cur = bmp.copy(Bitmap.Config.ARGB_8888, true)
            if (options.convertToGrayscale) { cur = toGrayscale(cur); ops.add("grayscale") }
            if (options.enhanceContrast) { cur = enhanceContrast(cur, options.contrastLevel); ops.add("contrast") }
            if (options.resize && options.targetWidth > 0 && cur.width > options.targetWidth) { cur = resize(cur, options.targetWidth); ops.add("resize") }
            if (options.binarize) { cur = binarize(cur, options.binarizeThreshold); ops.add("binarize") }
            val out = ByteArrayOutputStream().use { cur.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
            if (cur != bmp) bmp.recycle()
            PreprocessResult.Success(out, ops, 0f, originalSize, ImageSize(cur.width, cur.height))
        } catch (e: Exception) { PreprocessResult.Failure("Error: ${e.message}", e) }
    }

    override suspend fun getImageInfo(image: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image, 0, image.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return@withContext null
            ImageInfo(ImageSize(opts.outWidth, opts.outHeight), detectFormat(image) ?: ImageFormat.PNG, 24, opts.outMimeType?.contains("png") == true, null, image.size.toLong())
        } catch (e: Exception) { null }
    }

    override suspend fun validateForOcr(image: ByteArray) = withContext(Dispatchers.IO) {
        val issues = mutableListOf<ValidationIssue>()
        if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) issues.add(ValidationIssue(ValidationIssueCode.FILE_TOO_LARGE, "File too large", IssueSeverity.ERROR))
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try { BitmapFactory.decodeByteArray(image, 0, image.size, opts) } catch (e: Exception) { issues.add(ValidationIssue(ValidationIssueCode.CORRUPTED_DATA, "Decode failed", IssueSeverity.ERROR)); return@withContext ImageValidation(false, issues) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) { issues.add(ValidationIssue(ValidationIssueCode.UNSUPPORTED_FORMAT, "Unsupported", IssueSeverity.ERROR)); return@withContext ImageValidation(false, issues) }
        if (opts.outWidth < OcrLimits.MIN_IMAGE_DIMENSION || opts.outHeight < OcrLimits.MIN_IMAGE_DIMENSION) issues.add(ValidationIssue(ValidationIssueCode.IMAGE_TOO_SMALL, "Too small", IssueSeverity.ERROR))
        if (opts.outWidth > OcrLimits.MAX_IMAGE_DIMENSION || opts.outHeight > OcrLimits.MAX_IMAGE_DIMENSION) issues.add(ValidationIssue(ValidationIssueCode.IMAGE_TOO_LARGE, "Too large", IssueSeverity.WARNING))
        ImageValidation(!issues.any { it.severity == IssueSeverity.ERROR }, issues)
    }

    private fun detectFormat(image: ByteArray) = when { image.size < 4 -> null; image[0] == 0xFF.toByte() && image[1] == 0xD8.toByte() -> ImageFormat.JPEG; image[0] == 0x89.toByte() && image[1] == 0x50.toByte() -> ImageFormat.PNG; image[0] == 0x47.toByte() && image[1] == 0x49.toByte() -> ImageFormat.GIF; image[0] == 0x42.toByte() && image[1] == 0x4D.toByte() -> ImageFormat.BMP; else -> null }
    private fun toGrayscale(bmp: Bitmap) = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }) }
    private fun enhanceContrast(bmp: Bitmap, level: Float): Bitmap { val t = 128 * (1 - level); val m = ColorMatrix(floatArrayOf(level, 0f, 0f, 0f, t, 0f, level, 0f, 0f, t, 0f, 0f, level, 0f, t, 0f, 0f, 0f, 1f, 0f)); return Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) }) } }
    private fun resize(bmp: Bitmap, w: Int) = Bitmap.createScaledBitmap(bmp, w, (bmp.height * w.toFloat() / bmp.width).toInt(), true)
    private fun binarize(bmp: Bitmap, t: Int): Bitmap { val r = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888); for (y in 0 until bmp.height) for (x in 0 until bmp.width) { val p = bmp.getPixel(x, y); val g = ((p shr 16 and 0xFF) * 0.299 + (p shr 8 and 0xFF) * 0.587 + (p and 0xFF) * 0.114).toInt(); r.setPixel(x, y, if (g > t) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()) }; return r }
}

actual object ImagePreprocessorFactory { actual fun create(): ImagePreprocessor = ImagePreprocessorAndroid() }
