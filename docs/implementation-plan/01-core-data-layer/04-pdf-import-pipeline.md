# 04: PDF Import Pipeline

## Overview

Implement PDF parsing for bank statements with text extraction and OCR fallback per [ADR-002](../../PRDs/13-architecture-decision-records.md#adr-002-ocr-library-selection).

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PDF IMPORT PIPELINE                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  PDF File                                                    │
│      │                                                       │
│      ▼                                                       │
│  ┌─────────────────┐                                        │
│  │ Text Detection  │ ─── Is text-based? ───┐               │
│  └─────────────────┘                       │               │
│      │ No                                  │ Yes           │
│      ▼                                     ▼               │
│  ┌─────────────────┐              ┌─────────────────┐      │
│  │   OCR Engine    │              │  Text Extract   │      │
│  │ (ML Kit/Tess)   │              │   (PDFBox)      │      │
│  └─────────────────┘              └─────────────────┘      │
│      │                                     │               │
│      └───────────────┬─────────────────────┘               │
│                      ▼                                      │
│              ┌─────────────────┐                           │
│              │ Statement Parser │                           │
│              │ (Template Match) │                           │
│              └─────────────────┘                           │
│                      │                                      │
│                      ▼                                      │
│              ┌─────────────────┐                           │
│              │  Transactions   │                           │
│              └─────────────────┘                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Implementation Steps

### Step 1: Define Parser Interface

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/PdfParser.kt
package com.ledgerlens.import

interface PdfParser {
    /**
     * Parse a PDF bank statement.
     * @param pdfData Raw PDF bytes
     * @param options Parsing options
     * @return Parsed statement result
     */
    suspend fun parse(
        pdfData: ByteArray,
        options: PdfParseOptions = PdfParseOptions()
    ): PdfParseResult
}

data class PdfParseOptions(
    val maxPages: Int = 500,
    val maxSizeBytes: Long = 50 * 1024 * 1024,  // 50 MB
    val timeoutMs: Long = 60_000,  // 60 seconds
    val memoryLimitBytes: Long = 512 * 1024 * 1024,  // 512 MB
    val ocrEnabled: Boolean = true,
    val ocrLanguage: String = "eng"
)

sealed class PdfParseResult {
    data class Success(
        val transactions: List<ParsedTransaction>,
        val metadata: StatementMetadata,
        val warnings: List<ParseWarning>
    ) : PdfParseResult()

    data class NeedsReview(
        val transactions: List<ParsedTransaction>,
        val issues: List<ParseIssue>,
        val metadata: StatementMetadata
    ) : PdfParseResult()

    data class Failure(
        val error: ParseError
    ) : PdfParseResult()
}

data class ParsedTransaction(
    val rowRef: String,  // e.g., "page:2,line:15"
    val postedDate: LocalDate,
    val transactionDate: LocalDate?,
    val descriptionRaw: String,
    val amount: Money,
    val balance: Money?,
    val confidence: Float  // 0.0 to 1.0
)

data class StatementMetadata(
    val accountName: String?,
    val accountNumber: String?,  // Last 4 only
    val statementPeriod: DateRange?,
    val pageCount: Int,
    val extractionMethod: ExtractionMethod
)

enum class ExtractionMethod {
    TEXT_BASED,
    OCR,
    HYBRID
}
```

### Step 2: Platform-Specific Text Extraction

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/PdfTextExtractor.kt
package com.ledgerlens.import

expect class PdfTextExtractor {
    /**
     * Extract text from PDF.
     * @return Extracted text per page with bounding boxes
     */
    suspend fun extractText(pdfData: ByteArray): TextExtractionResult
}

data class TextExtractionResult(
    val pages: List<PageText>,
    val isTextBased: Boolean,  // true if embedded text found
    val confidence: Float
)

data class PageText(
    val pageNumber: Int,
    val lines: List<TextLine>
)

data class TextLine(
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float
)
```

**Desktop Implementation (PDFBox):**
```kotlin
// shared/src/desktopMain/kotlin/com/ledgerlens/import/PdfTextExtractor.kt
package com.ledgerlens.import

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

actual class PdfTextExtractor {
    actual suspend fun extractText(pdfData: ByteArray): TextExtractionResult {
        val document = PDDocument.load(pdfData)
        return try {
            val stripper = CustomTextStripper()
            val pages = mutableListOf<PageText>()

            for (pageNum in 1..document.numberOfPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                stripper.getText(document)

                pages.add(PageText(
                    pageNumber = pageNum,
                    lines = stripper.extractedLines
                ))
            }

            val hasText = pages.any { it.lines.isNotEmpty() }
            TextExtractionResult(
                pages = pages,
                isTextBased = hasText,
                confidence = if (hasText) 0.95f else 0.0f
            )
        } finally {
            document.close()
        }
    }
}
```

### Step 3: OCR Engine Interface

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/OcrEngine.kt
package com.ledgerlens.import

expect class OcrEngine {
    /**
     * Perform OCR on an image.
     */
    suspend fun recognizeText(imageData: ByteArray): OcrResult
}

data class OcrResult(
    val text: String,
    val blocks: List<OcrTextBlock>,
    val confidence: Float
)

data class OcrTextBlock(
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float,
    val lines: List<OcrTextLine>
)
```

**Android Implementation (ML Kit):**
```kotlin
// shared/src/androidMain/kotlin/com/ledgerlens/import/OcrEngine.kt
package com.ledgerlens.import

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

actual class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    actual suspend fun recognizeText(imageData: ByteArray): OcrResult {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        val image = InputImage.fromBitmap(bitmap, 0)

        return suspendCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        OcrTextBlock(
                            text = block.text,
                            boundingBox = block.boundingBox?.toBoundingBox() ?: BoundingBox.EMPTY,
                            confidence = block.lines.map { it.confidence ?: 0f }.average().toFloat(),
                            lines = block.lines.map { line ->
                                OcrTextLine(
                                    text = line.text,
                                    confidence = line.confidence ?: 0f
                                )
                            }
                        )
                    }
                    continuation.resume(OcrResult(
                        text = visionText.text,
                        blocks = blocks,
                        confidence = blocks.map { it.confidence }.average().toFloat()
                    ))
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }
}
```

**Desktop Implementation (Tesseract):**
```kotlin
// shared/src/desktopMain/kotlin/com/ledgerlens/import/OcrEngine.kt
package com.ledgerlens.import

import net.sourceforge.tess4j.Tesseract

actual class OcrEngine {
    private val tesseract = Tesseract().apply {
        setDatapath("/usr/share/tesseract-ocr/4.00/tessdata")  // Configurable
        setLanguage("eng")
        setPageSegMode(6)  // Assume uniform block of text
    }

    actual suspend fun recognizeText(imageData: ByteArray): OcrResult {
        return withContext(Dispatchers.IO) {
            val image = ImageIO.read(ByteArrayInputStream(imageData))
            val result = tesseract.doOCR(image)

            OcrResult(
                text = result,
                blocks = parseBlocks(result),
                confidence = 0.8f  // Tesseract doesn't provide per-char confidence easily
            )
        }
    }
}
```

### Step 4: Statement Parser

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/StatementParser.kt
package com.ledgerlens.import

interface StatementParser {
    /**
     * Parse extracted text into transactions.
     */
    fun parse(
        pages: List<PageText>,
        options: StatementParseOptions
    ): List<ParsedTransaction>
}

class GenericStatementParser : StatementParser {
    // Date patterns
    private val datePatterns = listOf(
        Regex("""\b(\d{1,2}/\d{1,2}/\d{2,4})\b"""),
        Regex("""\b(\d{1,2}-\d{1,2}-\d{2,4})\b"""),
        Regex("""\b(\w{3}\s+\d{1,2},?\s+\d{4})\b"""),
    )

    // Amount patterns
    private val amountPatterns = listOf(
        Regex("""\$?([\d,]+\.\d{2})"""),
        Regex("""([\d,]+\.\d{2})\s*(CR|DR)?""", RegexOption.IGNORE_CASE),
    )

    override fun parse(
        pages: List<PageText>,
        options: StatementParseOptions
    ): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        for (page in pages) {
            val tableRegions = detectTableRegions(page.lines)

            for (region in tableRegions) {
                val rows = parseTableRows(region)
                for (row in rows) {
                    parseTransactionRow(row, page.pageNumber)?.let {
                        transactions.add(it)
                    }
                }
            }
        }

        return transactions
    }

    private fun parseTransactionRow(
        row: TableRow,
        pageNumber: Int
    ): ParsedTransaction? {
        val date = extractDate(row.text)
        val amount = extractAmount(row.text)
        val description = extractDescription(row.text, date, amount)

        if (date == null || amount == null) return null

        return ParsedTransaction(
            rowRef = "page:$pageNumber,line:${row.lineNumber}",
            postedDate = date,
            transactionDate = null,
            descriptionRaw = description,
            amount = amount,
            balance = extractBalance(row.text),
            confidence = calculateConfidence(row)
        )
    }
}
```

### Step 5: Security Sandbox

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/import/PdfSandbox.kt
package com.ledgerlens.import

class PdfSandbox(private val options: PdfParseOptions) {

    suspend fun <T> executeWithLimits(
        block: suspend () -> T
    ): T {
        // Check memory before starting
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        if (usedMemory > options.memoryLimitBytes * 0.8) {
            runtime.gc()  // Suggest GC
        }

        return withTimeout(options.timeoutMs) {
            block()
        }
    }

    fun validatePdf(pdfData: ByteArray) {
        // Size check
        if (pdfData.size > options.maxSizeBytes) {
            throw PdfSizeExceededException(
                "PDF size ${pdfData.size} exceeds limit ${options.maxSizeBytes}"
            )
        }

        // Basic header validation
        val header = pdfData.take(8).toByteArray().decodeToString()
        if (!header.startsWith("%PDF-")) {
            throw InvalidPdfException("Not a valid PDF file")
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Text-based PDFs extract correctly
- [ ] OCR fallback works for scanned PDFs
- [ ] Multi-page statements handled
- [ ] Security limits enforced (size, timeout, memory)
- [ ] Confidence scores assigned
- [ ] Low confidence routes to review
- [ ] At least 98% extraction rate on test corpus

---

## Dependencies

- PDFBox (Desktop)
- ML Kit Text Recognition (Android)
- Tesseract via tess4j (Desktop)

---

## Estimated Complexity

**High** - Complex parsing with platform-specific implementations.
