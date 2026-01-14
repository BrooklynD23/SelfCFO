package com.ledgerlens.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImagePreprocessorDesktop : ImagePreprocessor {
    override suspend fun preprocess(image: ByteArray, options: PreprocessOptions) = withContext(Dispatchers.IO) {
        try {
            val img = ByteArrayInputStream(image).use { ImageIO.read(it) } ?: return@withContext PreprocessResult.Failure("Decode failed")
            val originalSize = ImageSize(img.width, img.height)
            val ops = mutableListOf<String>()
            var cur = img
            if (options.convertToGrayscale && cur.type != BufferedImage.TYPE_BYTE_GRAY) { cur = toGrayscale(cur); ops.add("grayscale") }
            if (options.enhanceContrast) { cur = enhanceContrast(cur, options.contrastLevel); ops.add("contrast") }
            if (options.denoise) { cur = denoise(cur, options.denoiseStrength); ops.add("denoise") }
            if (options.resize && options.targetWidth > 0 && cur.width > options.targetWidth) { cur = resize(cur, options.targetWidth); ops.add("resize") }
            if (options.sharpen) { cur = sharpen(cur, options.sharpenStrength); ops.add("sharpen") }
            if (options.binarize) { cur = binarize(cur, options.binarizeThreshold); ops.add("binarize") }
            val out = ByteArrayOutputStream().use { ImageIO.write(cur, "png", it); it.toByteArray() }
            PreprocessResult.Success(out, ops, 0f, originalSize, ImageSize(cur.width, cur.height))
        } catch (e: Exception) { PreprocessResult.Failure("Error: ${e.message}", e) }
    }

    override suspend fun getImageInfo(image: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val img = ByteArrayInputStream(image).use { ImageIO.read(it) } ?: return@withContext null
            ImageInfo(ImageSize(img.width, img.height), detectFormat(image) ?: ImageFormat.PNG, if (img.type == BufferedImage.TYPE_BYTE_GRAY) 8 else 24, img.colorModel.hasAlpha(), null, image.size.toLong())
        } catch (e: Exception) { null }
    }

    override suspend fun validateForOcr(image: ByteArray) = withContext(Dispatchers.IO) {
        val issues = mutableListOf<ValidationIssue>()
        if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) issues.add(ValidationIssue(ValidationIssueCode.FILE_TOO_LARGE, "File too large", IssueSeverity.ERROR))
        val img = try { ByteArrayInputStream(image).use { ImageIO.read(it) } } catch (e: Exception) { issues.add(ValidationIssue(ValidationIssueCode.CORRUPTED_DATA, "Decode failed", IssueSeverity.ERROR)); return@withContext ImageValidation(false, issues) }
        if (img == null) { issues.add(ValidationIssue(ValidationIssueCode.UNSUPPORTED_FORMAT, "Unsupported", IssueSeverity.ERROR)); return@withContext ImageValidation(false, issues) }
        if (img.width < OcrLimits.MIN_IMAGE_DIMENSION || img.height < OcrLimits.MIN_IMAGE_DIMENSION) issues.add(ValidationIssue(ValidationIssueCode.IMAGE_TOO_SMALL, "Too small", IssueSeverity.ERROR))
        if (img.width > OcrLimits.MAX_IMAGE_DIMENSION || img.height > OcrLimits.MAX_IMAGE_DIMENSION) issues.add(ValidationIssue(ValidationIssueCode.IMAGE_TOO_LARGE, "Too large", IssueSeverity.WARNING))
        ImageValidation(!issues.any { it.severity == IssueSeverity.ERROR }, issues)
    }

    private fun detectFormat(image: ByteArray) = when { image.size < 4 -> null; image[0] == 0xFF.toByte() && image[1] == 0xD8.toByte() -> ImageFormat.JPEG; image[0] == 0x89.toByte() && image[1] == 0x50.toByte() -> ImageFormat.PNG; image[0] == 0x47.toByte() && image[1] == 0x49.toByte() -> ImageFormat.GIF; image[0] == 0x42.toByte() && image[1] == 0x4D.toByte() -> ImageFormat.BMP; else -> null }
    private fun toGrayscale(img: BufferedImage) = BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_GRAY).also { it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
    private fun enhanceContrast(img: BufferedImage, level: Float): BufferedImage { val r = BufferedImage(img.width, img.height, img.type); val o = 128 * (1 - level); for (y in 0 until img.height) for (x in 0 until img.width) { val rgb = img.getRGB(x, y); r.setRGB(x, y, (rgb and 0xFF000000.toInt()) or (((rgb shr 16 and 0xFF) * level + o).toInt().coerceIn(0, 255) shl 16) or (((rgb shr 8 and 0xFF) * level + o).toInt().coerceIn(0, 255) shl 8) or ((rgb and 0xFF) * level + o).toInt().coerceIn(0, 255)) }; return r }
    private fun denoise(img: BufferedImage, strength: Float): BufferedImage { val s = (3 + (strength * 2).toInt()).coerceIn(3, 5); val k = Kernel(s, s, FloatArray(s * s) { 1f / (s * s) }); val r = BufferedImage(img.width, img.height, img.type); ConvolveOp(k, ConvolveOp.EDGE_NO_OP, null).filter(img, r); return r }
    private fun resize(img: BufferedImage, w: Int): BufferedImage { val h = (img.height * w.toDouble() / img.width).toInt(); val r = BufferedImage(w, h, img.type); r.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(img, 0, 0, w, h, null); dispose() }; return r }
    private fun sharpen(img: BufferedImage, s: Float): BufferedImage { val k = Kernel(3, 3, floatArrayOf(0f, -s, 0f, -s, 1 + 4 * s, -s, 0f, -s, 0f)); val r = BufferedImage(img.width, img.height, img.type); ConvolveOp(k, ConvolveOp.EDGE_NO_OP, null).filter(img, r); return r }
    private fun binarize(img: BufferedImage, t: Int): BufferedImage { val r = BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_BINARY); for (y in 0 until img.height) for (x in 0 until img.width) { val rgb = img.getRGB(x, y); val g = ((rgb shr 16 and 0xFF) * 0.299 + (rgb shr 8 and 0xFF) * 0.587 + (rgb and 0xFF) * 0.114).toInt(); r.setRGB(x, y, if (g > t) 0xFFFFFF else 0) }; return r }
}

actual object ImagePreprocessorFactory { actual fun create(): ImagePreprocessor = ImagePreprocessorDesktop() }
