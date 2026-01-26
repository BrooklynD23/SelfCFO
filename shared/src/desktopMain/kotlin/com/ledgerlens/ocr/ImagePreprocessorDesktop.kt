package com.ledgerlens.ocr

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop implementation of ImagePreprocessor using Java AWT.
 */
class ImagePreprocessorDesktop : ImagePreprocessor {

    override suspend fun preprocess(image: ByteArray, options: PreprocessOptions): PreprocessResult =
        withContext(Dispatchers.IO) {
            try {
                val bufferedImage = ByteArrayInputStream(image).use { ImageIO.read(it) }
                    ?: return@withContext PreprocessResult.Failure("Failed to decode image")

                val originalSize = ImageSize(bufferedImage.width, bufferedImage.height)
                val appliedOps = mutableListOf<String>()
                var currentImage = bufferedImage

                if (options.convertToGrayscale && currentImage.type != BufferedImage.TYPE_BYTE_GRAY) {
                    currentImage = toGrayscale(currentImage)
                    appliedOps.add("grayscale")
                }
                if (options.enhanceContrast) {
                    currentImage = enhanceContrast(currentImage, options.contrastLevel)
                    appliedOps.add("contrast")
                }
                if (options.denoise) {
                    currentImage = denoise(currentImage, options.denoiseStrength)
                    appliedOps.add("denoise")
                }
                if (options.resize && options.targetWidth > 0 && currentImage.width > options.targetWidth) {
                    currentImage = resize(currentImage, options.targetWidth)
                    appliedOps.add("resize")
                }
                if (options.sharpen) {
                    currentImage = sharpen(currentImage, options.sharpenStrength)
                    appliedOps.add("sharpen")
                }
                if (options.binarize) {
                    currentImage = binarize(currentImage, options.binarizeThreshold)
                    appliedOps.add("binarize")
                }

                val outputBytes = ByteArrayOutputStream().use {
                    ImageIO.write(currentImage, "png", it)
                    it.toByteArray()
                }
                PreprocessResult.Success(
                    outputBytes,
                    appliedOps,
                    0f,
                    originalSize,
                    ImageSize(currentImage.width, currentImage.height)
                )
            } catch (e: Exception) {
                PreprocessResult.Failure("Preprocessing failed: ${e.message}", e)
            }
        }

    override suspend fun getImageInfo(image: ByteArray): ImageInfo? =
        withContext(Dispatchers.IO) {
            try {
                val img = ByteArrayInputStream(image).use { ImageIO.read(it) }
                    ?: return@withContext null
                val bitDepth = if (img.type == BufferedImage.TYPE_BYTE_GRAY) 8 else 24
                ImageInfo(
                    ImageSize(img.width, img.height),
                    detectFormat(image) ?: ImageFormat.PNG,
                    bitDepth,
                    img.colorModel.hasAlpha(),
                    null,
                    image.size.toLong()
                )
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun validateForOcr(image: ByteArray): ImageValidation =
        withContext(Dispatchers.IO) {
            val issues = mutableListOf<ValidationIssue>()
            if (image.size > OcrLimits.MAX_IMAGE_SIZE_BYTES) {
                issues.add(
                    ValidationIssue(
                        ValidationIssueCode.FILE_TOO_LARGE,
                        "File too large",
                        IssueSeverity.ERROR
                    )
                )
            }

            val img = try {
                ByteArrayInputStream(image).use { ImageIO.read(it) }
            } catch (e: Exception) {
                issues.add(
                    ValidationIssue(
                        ValidationIssueCode.CORRUPTED_DATA,
                        "Decode failed",
                        IssueSeverity.ERROR
                    )
                )
                return@withContext ImageValidation(false, issues)
            }
            if (img == null) {
                issues.add(
                    ValidationIssue(
                        ValidationIssueCode.UNSUPPORTED_FORMAT,
                        "Unsupported",
                        IssueSeverity.ERROR
                    )
                )
                return@withContext ImageValidation(false, issues)
            }
            if (img.width < OcrLimits.MIN_IMAGE_DIMENSION ||
                img.height < OcrLimits.MIN_IMAGE_DIMENSION
            ) {
                issues.add(
                    ValidationIssue(
                        ValidationIssueCode.IMAGE_TOO_SMALL,
                        "Image too small",
                        IssueSeverity.ERROR
                    )
                )
            }
            if (img.width > OcrLimits.MAX_IMAGE_DIMENSION ||
                img.height > OcrLimits.MAX_IMAGE_DIMENSION
            ) {
                issues.add(
                    ValidationIssue(
                        ValidationIssueCode.IMAGE_TOO_LARGE,
                        "Image too large",
                        IssueSeverity.WARNING
                    )
                )
            }
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

    private fun toGrayscale(image: BufferedImage): BufferedImage {
        val gray = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        gray.createGraphics().apply {
            drawImage(image, 0, 0, null)
            dispose()
        }
        return gray
    }

    private fun enhanceContrast(image: BufferedImage, level: Float): BufferedImage {
        val result = BufferedImage(image.width, image.height, image.type)
        val offset = 128 * (1 - level)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            val r = ((rgb shr 16 and 0xFF) * level + offset).toInt().coerceIn(0, 255)
            val g = ((rgb shr 8 and 0xFF) * level + offset).toInt().coerceIn(0, 255)
            val b = ((rgb and 0xFF) * level + offset).toInt().coerceIn(0, 255)
            result.setRGB(x, y, (rgb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b)
        }
        return result
    }

    private fun denoise(image: BufferedImage, strength: Float): BufferedImage {
        val size = (3 + (strength * 2).toInt()).coerceIn(3, 5)
        val kernel = Kernel(size, size, FloatArray(size * size) { 1f / (size * size) })
        val result = BufferedImage(image.width, image.height, image.type)
        ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(image, result)
        return result
    }

    private fun resize(image: BufferedImage, targetWidth: Int): BufferedImage {
        val scale = targetWidth.toDouble() / image.width
        val newHeight = (image.height * scale).toInt()
        val result = BufferedImage(targetWidth, newHeight, image.type)
        result.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image, 0, 0, targetWidth, newHeight, null)
            dispose()
        }
        return result
    }

    private fun sharpen(image: BufferedImage, strength: Float): BufferedImage {
        val kernel =
            Kernel(3, 3, floatArrayOf(0f, -strength, 0f, -strength, 1 + 4 * strength, -strength, 0f, -strength, 0f))
        val result = BufferedImage(image.width, image.height, image.type)
        ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(image, result)
        return result
    }

    private fun binarize(image: BufferedImage, threshold: Int): BufferedImage {
        val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_BINARY)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            val gray = ((rgb shr 16 and 0xFF) * 0.299 + (rgb shr 8 and 0xFF) * 0.587 + (rgb and 0xFF) * 0.114).toInt()
            result.setRGB(x, y, if (gray > threshold) 0xFFFFFF else 0x000000)
        }
        return result
    }
}

actual object ImagePreprocessorFactory {
    actual fun create(): ImagePreprocessor = ImagePreprocessorDesktop()
}
