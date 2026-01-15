# 02: ML Categorization

## Overview

Integrate TensorFlow Lite for on-device transaction categorization with signed model bundles per [ADR-005](../../PRDs/13-architecture-decision-records.md#adr-005-model-update-mechanism).

---

## Implementation Steps

### Step 1: Define Model Interface

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ml/CategorizationModel.kt
package com.ledgerlens.ml

interface CategorizationModel {
    /**
     * Predict category for a transaction.
     * @param features Extracted features
     * @return Top predictions with confidence scores
     */
    suspend fun predict(features: TransactionFeatures): List<CategoryPrediction>

    /**
     * Get model version info.
     */
    fun getModelInfo(): ModelInfo
}

data class TransactionFeatures(
    val merchantTokens: List<String>,
    val descriptionTokens: List<String>,
    val amountBucket: AmountBucket,
    val dayOfWeek: Int,
    val month: Int
)

enum class AmountBucket {
    TINY,       // < $5
    SMALL,      // $5 - $20
    MEDIUM,     // $20 - $50
    LARGE,      // $50 - $200
    VERY_LARGE, // $200 - $1000
    HUGE        // > $1000
}

data class CategoryPrediction(
    val categoryId: String,
    val confidence: Float
)

data class ModelInfo(
    val version: String,
    val bundleId: String,
    val minAppVersion: String
)
```

### Step 2: Feature Extractor

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ml/FeatureExtractor.kt
package com.ledgerlens.ml

class FeatureExtractor {

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
        "pos", "debit", "credit", "purchase", "checkcard", "ach", "online"
    )

    fun extract(
        merchantNormalized: String,
        descriptionRaw: String,
        amount: Money,
        date: LocalDate
    ): TransactionFeatures {
        return TransactionFeatures(
            merchantTokens = tokenize(merchantNormalized),
            descriptionTokens = tokenize(descriptionRaw),
            amountBucket = getAmountBucket(amount),
            dayOfWeek = date.dayOfWeek.value,
            month = date.monthNumber
        )
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .take(20)  // Limit tokens
    }

    private fun getAmountBucket(amount: Money): AmountBucket {
        // Avoid floating point: compare in minor units using currency scale.
        // For MVP (bank transactions), currency scales are typically 0-3.
        val absMinorUnits = amount.abs().minorUnits
        val scale = amount.scale
        val unit = (1..scale).fold(1L) { acc, _ -> acc * 10L } // 1 major unit in minor units

        val tiny = 5L * unit
        val small = 20L * unit
        val medium = 50L * unit
        val large = 200L * unit
        val veryLarge = 1000L * unit

        return when {
            absMinorUnits < tiny -> AmountBucket.TINY
            absMinorUnits < small -> AmountBucket.SMALL
            absMinorUnits < medium -> AmountBucket.MEDIUM
            absMinorUnits < large -> AmountBucket.LARGE
            absMinorUnits < veryLarge -> AmountBucket.VERY_LARGE
            else -> AmountBucket.HUGE
        }
    }
}
```

### Step 3: TFLite Model Wrapper

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ml/TFLiteModel.kt
package com.ledgerlens.ml

expect class TFLiteModel(modelPath: String) : CategorizationModel {
    override suspend fun predict(features: TransactionFeatures): List<CategoryPrediction>
    override fun getModelInfo(): ModelInfo
    fun close()
}
```

**Android Implementation:**
```kotlin
// shared/src/androidMain/kotlin/com/ledgerlens/ml/TFLiteModel.kt
package com.ledgerlens.ml

import org.tensorflow.lite.Interpreter

actual class TFLiteModel actual constructor(modelPath: String) : CategorizationModel {
    private val interpreter: Interpreter
    private val modelInfo: ModelInfo

    init {
        val modelFile = File(modelPath)
        interpreter = Interpreter(modelFile)
        modelInfo = loadModelInfo(modelPath)
    }

    actual override suspend fun predict(
        features: TransactionFeatures
    ): List<CategoryPrediction> {
        // Convert features to input tensor
        val inputArray = prepareInput(features)

        // Output buffer
        val outputArray = Array(1) { FloatArray(NUM_CATEGORIES) }

        // Run inference
        interpreter.run(inputArray, outputArray)

        // Convert to predictions
        return outputArray[0]
            .mapIndexed { index, confidence ->
                CategoryPrediction(
                    categoryId = CATEGORY_INDEX_MAP[index] ?: "uncategorized",
                    confidence = confidence
                )
            }
            .sortedByDescending { it.confidence }
            .take(3)
    }

    private fun prepareInput(features: TransactionFeatures): Array<FloatArray> {
        // Vocabulary lookup and embedding
        val tokenIds = features.merchantTokens
            .plus(features.descriptionTokens)
            .map { vocabulary[it] ?: 0 }
            .take(MAX_SEQUENCE_LENGTH)
            .padEnd(MAX_SEQUENCE_LENGTH, 0)

        // One-hot encode categorical features
        val amountBucket = FloatArray(AmountBucket.values().size).apply {
            this[features.amountBucket.ordinal] = 1f
        }
        val dayOfWeek = FloatArray(7).apply {
            this[features.dayOfWeek - 1] = 1f
        }

        // Combine into input tensor
        return arrayOf(
            tokenIds.map { it.toFloat() }.toFloatArray()
                .plus(amountBucket)
                .plus(dayOfWeek)
        )
    }

    actual override fun getModelInfo(): ModelInfo = modelInfo

    actual fun close() {
        interpreter.close()
    }

    companion object {
        const val MAX_SEQUENCE_LENGTH = 40
        const val NUM_CATEGORIES = 30

        // Loaded from vocabulary.json in bundle
        private lateinit var vocabulary: Map<String, Int>

        // Loaded from categories.json in bundle
        private lateinit var CATEGORY_INDEX_MAP: Map<Int, String>
    }
}
```

### Step 4: Model Bundle Manager

```kotlin
// shared/src/jvmMain/kotlin/com/ledgerlens/ml/ModelBundleManager.kt
package com.ledgerlens.ml

import java.io.File
import java.security.Signature
import java.security.PublicKey

class ModelBundleManager(
    private val bundleDir: File,
    private val publicKey: PublicKey  // Embedded in app
) {
    companion object {
        // Hard limits to reduce DoS risk from malicious or corrupted bundles.
        const val MAX_BUNDLE_SIZE_BYTES: Long = 50L * 1024 * 1024
        const val MAX_SINGLE_FILE_BYTES: Long = 25L * 1024 * 1024
    }

    /**
     * Load a model bundle with verification.
     */
    suspend fun loadBundle(bundleId: String): Result<ModelBundle> = runCatching {
        val bundlePath = File(bundleDir, bundleId)
        require(bundlePath.isDirectory) { "Bundle directory not found: $bundleId" }

        // 0. Size guard (best-effort; real hosting should also enforce limits)
        val bundleSize = bundlePath.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        require(bundleSize <= MAX_BUNDLE_SIZE_BYTES) { "Bundle too large" }

        // 1. Read manifest (verify before trusting any file paths)
        val manifestFile = File(bundlePath, "manifest.json")
        val manifestBytes = manifestFile.readBytes()
        val manifest = Json.decodeFromString<BundleManifest>(manifestBytes.decodeToString())

        // 2. Verify signature (canonical JSON, not object re-serialization)
        verifySignature(manifest, manifestBytes)

        // 3. Rollback protection (ADR-005): never accept a bundle version older than installed.
        // if (manifest.bundleVersion < installedVersion) throw BundleRollbackException(...)

        // 4. Verify file paths, sizes, and hashes
        for (fileEntry in manifest.files) {
            require(isSafeRelativePath(fileEntry.path)) { "Unsafe bundle path: ${fileEntry.path}" }

            val file = File(bundlePath, fileEntry.path).canonicalFile
            require(file.path.startsWith(bundlePath.canonicalPath + File.separator)) {
                "Path traversal attempt: ${fileEntry.path}"
            }
            require(file.isFile) { "Missing file: ${fileEntry.path}" }
            require(fileEntry.sizeBytes in 0..MAX_SINGLE_FILE_BYTES) { "File too large: ${fileEntry.path}" }
            require(file.length() == fileEntry.sizeBytes) { "Size mismatch: ${fileEntry.path}" }

            val actualHash = file.readBytes().sha256Hex()
            if (actualHash != fileEntry.sha256) {
                throw BundleIntegrityException(
                    "Hash mismatch for ${fileEntry.path}"
                )
            }
        }

        // 5. Check version compatibility
        if (!isCompatible(manifest.minAppVersion)) {
            throw BundleIncompatibleException(
                "Bundle requires app version ${manifest.minAppVersion}"
            )
        }

        // 6. Quarantine/activation (ADR-005): validate in staging dir before switching "active".

        ModelBundle(
            id = manifest.bundleId,
            version = manifest.bundleVersion,
            modelPath = File(bundlePath, "model.tflite").absolutePath,
            vocabularyPath = File(bundlePath, "vocabulary.json").absolutePath,
            categoriesPath = File(bundlePath, "categories.json").absolutePath
        )
    }

    private fun verifySignature(manifest: BundleManifest, manifestBytes: ByteArray) {
        val signature = Signature.getInstance("Ed25519")
        signature.initVerify(publicKey)

        // Verify signature over a deterministic serialization of the manifest with the signature field removed.
        // Keep the JSON configuration stable (no pretty printing, stable field order via data class property order).
        val canonicalJson = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = false
        }
        val canonicalBytes = canonicalJson
            .encodeToString(BundleManifest.serializer(), manifest.copy(signature = ""))
            .toByteArray()

        signature.update(canonicalBytes)

        val signatureBytes = Base64.decode(manifest.signature)
        if (!signature.verify(signatureBytes)) {
            throw BundleSignatureException("Invalid bundle signature")
        }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/") || path.startsWith("\\\\")) return false
        if (path.contains("..")) return false
        return true
    }
}

@Serializable
data class BundleManifest(
    val bundleId: String,
    val bundleVersion: String,
    val minAppVersion: String,
    val maxAppVersion: String?,
    val createdAt: String,
    val files: List<BundleFile>,
    val signature: String
)

@Serializable
data class BundleFile(
    val path: String,
    val sha256: String,
    val sizeBytes: Long
)
```

---

## Acceptance Criteria

- [ ] TFLite model loads on Android and Desktop
- [ ] Feature extraction produces consistent vectors
- [ ] Inference returns top-3 categories
- [ ] Bundle signature verification works
- [ ] Bundle hash verification works
- [ ] Version compatibility checked
- [ ] Inference time <10ms desktop, <30ms Android

---

## Dependencies

- TensorFlow Lite
- Ed25519 for signature verification

---

## Estimated Complexity

**High** - Platform-specific TFLite integration with security.
