package com.ledgerlens.ocr

/**
 * Factory for creating platform-specific OCR implementations.
 * - Android: ML Kit Text Recognition
 * - Desktop: Tesseract via Tess4j
 */
expect object ReceiptOcrFactory {
    fun create(config: OcrEngineConfig = OcrEngineConfig()): ReceiptOcr
}

data class OcrEngineConfig(
    val dataPath: String? = null,
    val languages: List<String> = listOf("eng"),
    val cacheModels: Boolean = true,
    val useGpu: Boolean = true
)

/**
 * Factory for creating platform-specific ImagePreprocessor implementations.
 */
expect object ImagePreprocessorFactory {
    fun create(): ImagePreprocessor
}
