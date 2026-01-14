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

/**
 * Android implementation of ImagePreprocessor using Android Graphics APIs.
 */
class ImagePreprocessorAndroid : ImagePreprocessor {

    override suspend fun preprocess(image: ByteArray, options: PreprocessOptions): PreprocessResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
                ?: return@withContext PreprocessResult.Failure("Failed to decode image")

            val originalSize = ImageSize(bitmap.width, bitmap.height)
            val appliedOps = mutableListOf<String>()
            var currentBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            if (options.convertToGrayscale) { currentBitmap = toGrayscale(currentBitmap); appliedOps.add("grayscale") }
            if (options.enhanceContrast) { currentBitmap = enhanceContrast(currentBitmap, options.contrastLevel); appliedOps.add("contrast") }
            if (options.resize && options.targetWidth > 0 && currentBitmap.width > options.targetWidth) {
                currentBitmap = resize(currentBitmap, options.targetWidth); appliedOps.add("resize")
            }
            if (options.binarize) { currentBitmap = binarize(currentBitmap, options.binarizeThreshold); appliedOps.add("binarize") }

            val outputBytes = ByteArrayOutputStream().use { currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
            if (currentBitmap != bitmap) bitmap.recycle()

            PreprocessResult.Success(outputBytes, appliedOps, 0f, originalSize, ImageSize(currentBitmap.width, currentBitmap.height))
        } catch (e: Exception) { PreprocessResult.Failure("Preprocessing failed: ${e.message}", e) }
    }

    override suspend fun getImageInfo(image: ByteArray): ImageInfo? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image, 0, image.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
            ImageInfo(ImageSize(options.outWidth, options.outHeight), detectFormat(image) ?: ImageFormat.PNG, 24,
                options.outMimeType?.contains("png") == true, null, image.size.toLong())
        } catch (e: Exception) { null }
    }

    override suspend fun validateForOcr(image: ByteArray): ImageValidation = withContext(Dispatchers.IO) {
        val issues = mutableListOf<ValidationIssue>()
        if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) issues.add(ValidationIssue(ValidationIssueCode.FILE_TOO_LARGE, "File too large", IssueSeverity.ERROR))

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try { BitmapFactory.decodeByteArray(image, 0, image.size, options) } catch (e: Exception) {
            issues.add(ValidationIssue(ValidationIssueCode.CORRUPTED_DATA, "Decode failed", IssueSeverity.ERROR))
            return@withContext ImageValidation(false, issues)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) { issues.add(ValidationIssue(ValidationIssueCode.UNSUPPORTED_FORMAT, "Unsupported", IssueSeverity.ERROR)); return@withContext ImageValidation(false, issues) }
        if (options.outWidth < OcrLimits.MIN_IMAGE_DIMENSION || options.outHeight < OcrLimits.MIN_IMAGE_DIMENSION)
            issues.add(ValidationIssue(ValidationIssueCode.IMAGE_TOO_SMALL, "Image too small", IssueSeverity.ERROR))
        if (options.outWidth > OcrLimits.MAX_IMAGE_DIMENSION || options.outHeight > OcrLimits.MAX_IMAGE_DIMENSION)
            issues.add(ValidationIssue(ValidationIssueCode.IMAGE_TOO_LARGE, "Image too large", IssueSeverity.WARNING))
        ImageValidation(!issues.any { it.severity == IssueSeverity.ERROR }, issues)
    }

    private fun detectFormat(image: ByteArray): ImageFormat? = when {
        image.size < 4 -> null
        image[0] == 0xFF.toByte() && image[1] == 0xD8.toByte() -> ImageFormat.JPEG
        image[0] == 0x89.toByte() && image[1] == 0x50.toByte() -> ImageFormat.PNG
        image[0] == 0x47.toByte() && image[1] == 0x49.toByte() -> ImageFormat.GIF
        image[0] == 0x42.toByte() && image[1] == 0x4D.toByte() -> ImageFormat.BMP
        else -> null
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })
        return result
    }

    private fun enhanceContrast(bitmap: Bitmap, level: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val translate = 128 * (1 - level)
        val matrix = ColorMatrix(floatArrayOf(level, 0f, 0f, 0f, translate, 0f, level, 0f, 0f, translate, 0f, 0f, level, 0f, translate, 0f, 0f, 0f, 1f, 0f))
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return result
    }

    private fun resize(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val scale = targetWidth.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, targetWidth, (bitmap.height * scale).toInt(), true)
    }

    private fun binarize(bitmap: Bitmap, threshold: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val gray = ((pixel shr 16 and 0xFF) * 0.299 + (pixel shr 8 and 0xFF) * 0.587 + (pixel and 0xFF) * 0.114).toInt()
            result.setPixel(x, y, if (gray > threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        return result
    }
}

actual object ImagePreprocessorFactory {
    actual fun create(): ImagePreprocessor = ImagePreprocessorAndroid()
}
