package com.ledgerlens.import

/**
 * Android PDF parser implementation.
 *
 * TODO: Implement using ML Kit for OCR on scanned PDFs.
 * For now, this is a stub that throws NotImplementedError.
 *
 * Implementation plan:
 * 1. Use PdfRenderer to convert PDF pages to bitmaps
 * 2. Use ML Kit Text Recognition to extract text from bitmaps
 * 3. Parse extracted text using TemplateBasedParser
 *
 * Dependencies needed (add to androidMain):
 * - com.google.mlkit:text-recognition:16.0.0
 */
class PdfParserAndroid(
    private val templateParser: TemplateBasedParser = TemplateBasedParser()
) : PdfParser {

    override suspend fun canParse(pdfData: ByteArray): Boolean {
        return try {
            validatePdfHeader(pdfData)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun parse(
        pdfData: ByteArray,
        options: PdfParseOptions
    ): PdfParseResult {
        // Validate size
        if (pdfData.size > options.maxSizeBytes) {
            return PdfParseResult.Failure(
                ParseError.SizeExceeded(pdfData.size.toLong(), options.maxSizeBytes)
            )
        }

        // Validate PDF header
        try {
            validatePdfHeader(pdfData)
        } catch (e: Exception) {
            return PdfParseResult.Failure(
                ParseError.InvalidPdf(e.message ?: "Invalid PDF header")
            )
        }

        // TODO: Implement ML Kit-based text extraction
        // For now, return a clear error indicating this needs implementation
        return PdfParseResult.Failure(
            ParseError.UnsupportedFormat(
                "Android PDF parsing with ML Kit OCR is not yet implemented. " +
                "Use desktop version for PDF import, or implement ML Kit integration."
            )
        )
    }

    private fun validatePdfHeader(pdfData: ByteArray) {
        require(pdfData.size >= 8) { "PDF data too small" }
        val header = pdfData.take(8).toByteArray().decodeToString()
        require(header.startsWith("%PDF-")) { "Not a valid PDF file: missing %PDF- header" }
    }

    /**
     * TODO: Implement these methods for ML Kit integration
     */
    @Suppress("unused")
    private suspend fun extractTextWithMlKit(pdfData: ByteArray): TextExtractionResult {
        // 1. Open PDF with PdfRenderer
        // 2. Render each page to Bitmap
        // 3. Process bitmap with ML Kit TextRecognition
        // 4. Convert ML Kit results to TextExtractionResult

        throw NotImplementedError(
            "ML Kit text extraction not implemented. " +
            "See implementation guide in 04-pdf-import-pipeline.md"
        )
    }
}

/**
 * Factory for creating platform-specific PDF parser on Android.
 */
actual object PdfParserFactory {
    actual fun create(options: PdfParseOptions): PdfParser {
        return PdfParserAndroid(
            templateParser = TemplateBasedParser(options.defaultCurrencyCode)
        )
    }
}
