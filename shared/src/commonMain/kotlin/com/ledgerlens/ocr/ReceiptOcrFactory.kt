package com.ledgerlens.ocr

expect object ReceiptOcrFactory { fun create(config: OcrEngineConfig = OcrEngineConfig()): ReceiptOcr }
data class OcrEngineConfig(val dataPath: String? = null, val languages: List<String> = listOf("eng"), val cacheModels: Boolean = true, val useGpu: Boolean = true)
expect object ImagePreprocessorFactory { fun create(): ImagePreprocessor }
