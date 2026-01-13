package com.ledgerlens.import

/**
 * Factory for creating platform-specific PDF parser instances.
 */
expect object PdfParserFactory {
    fun create(options: PdfParseOptions = PdfParseOptions()): PdfParser
}
