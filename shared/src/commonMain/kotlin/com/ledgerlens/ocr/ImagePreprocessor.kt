package com.ledgerlens.ocr

interface ImagePreprocessor {
    suspend fun preprocess(image: ByteArray, options: PreprocessOptions = PreprocessOptions()): PreprocessResult
    suspend fun getImageInfo(image: ByteArray): ImageInfo?
    suspend fun validateForOcr(image: ByteArray): ImageValidation
}

data class PreprocessOptions(
    val convertToGrayscale: Boolean = true, val enhanceContrast: Boolean = true, val contrastLevel: Float = 1.2f,
    val deskew: Boolean = true, val denoise: Boolean = true, val denoiseStrength: Float = 0.5f,
    val resize: Boolean = true, val targetWidth: Int = OcrLimits.OPTIMAL_WIDTH,
    val sharpen: Boolean = false, val sharpenStrength: Float = 0.3f, val binarize: Boolean = false, val binarizeThreshold: Int = 128
) {
    companion object {
        val MINIMAL = PreprocessOptions(false, false, 1f, false, false, 0f, false, 0, false, 0f, false, 128)
        val RECEIPT = PreprocessOptions(contrastLevel = 1.3f)
        val AGGRESSIVE = PreprocessOptions(contrastLevel = 1.5f, denoiseStrength = 0.7f, sharpen = true, sharpenStrength = 0.4f)
        val DOCUMENT_SCAN = PreprocessOptions(binarize = true, binarizeThreshold = 140)
    }
}

sealed class PreprocessResult {
    data class Success(val image: ByteArray, val appliedOperations: List<String>, val skewAngle: Float = 0f, val originalSize: ImageSize, val newSize: ImageSize) : PreprocessResult() {
        override fun equals(other: Any?) = this === other || (other is Success && image.contentEquals(other.image) && appliedOperations == other.appliedOperations)
        override fun hashCode() = 31 * image.contentHashCode() + appliedOperations.hashCode()
    }
    data class Failure(val error: String, val cause: Throwable? = null) : PreprocessResult()
}

data class ImageSize(val width: Int, val height: Int) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 0f
    val pixels: Long get() = width.toLong() * height
    fun scale(factor: Float) = ImageSize((width * factor).toInt(), (height * factor).toInt())
    fun fitWidth(targetWidth: Int) = if (width <= 0) this else scale(targetWidth.toFloat() / width)
    companion object { val EMPTY = ImageSize(0, 0) }
}

data class ImageInfo(val size: ImageSize, val format: ImageFormat, val colorDepth: Int, val hasAlpha: Boolean, val dpi: Int?, val fileSizeBytes: Long) {
    val isGrayscale: Boolean get() = colorDepth <= 8
    val isColor: Boolean get() = colorDepth > 8
}

data class ImageValidation(val isValid: Boolean, val issues: List<ValidationIssue>) {
    val hasErrors: Boolean get() = issues.any { it.severity == IssueSeverity.ERROR }
    val hasWarnings: Boolean get() = issues.any { it.severity == IssueSeverity.WARNING }
    companion object {
        val VALID = ImageValidation(true, emptyList())
        fun invalid(vararg issues: ValidationIssue) = ImageValidation(false, issues.toList())
    }
}

data class ValidationIssue(val code: ValidationIssueCode, val message: String, val severity: IssueSeverity)
enum class IssueSeverity { INFO, WARNING, ERROR }
enum class ValidationIssueCode { IMAGE_TOO_SMALL, IMAGE_TOO_LARGE, FILE_TOO_LARGE, UNSUPPORTED_FORMAT, LOW_RESOLUTION, LOW_CONTRAST, EXCESSIVE_SKEW, CORRUPTED_DATA, POSSIBLE_BLUR }
