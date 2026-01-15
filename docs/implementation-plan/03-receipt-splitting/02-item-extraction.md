# 02: Item Extraction

## Overview

Parse line items with prices from OCR output using pattern matching and heuristics.

---

## Implementation Steps

### Step 1: Item Extractor

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/receipt/ItemExtractor.kt
package com.ledgerlens.receipt

import com.ledgerlens.domain.Money

class ItemExtractor {

    companion object {
        // Common price patterns
        private val PRICE_PATTERNS = listOf(
            // $12.99 or $12.99-
            Regex("""[$€£]\s*(\d{1,4})[.,](\d{2})[-]?"""),
            // 12.99 USD
            Regex("""(\d{1,4})[.,](\d{2})\s*(?:USD|EUR|GBP)?"""),
            // 12,99 (European format)
            Regex("""(\d{1,4}),(\d{2})(?:\s|$)""")
        )

        // Quantity patterns (2x, 2 @, qty 2)
        private val QUANTITY_PATTERNS = listOf(
            Regex("""(\d+)\s*[xX@]\s*"""),
            Regex("""[Qq]ty[:\s]*(\d+)"""),
            Regex("""^(\d+)\s+""")
        )

        // Lines to skip (headers, totals, etc.)
        private val SKIP_PATTERNS = listOf(
            Regex("""(?i)^(subtotal|total|tax|tip|change|cash|credit|visa|mastercard)"""),
            Regex("""(?i)thank\s*you"""),
            Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}"""), // Dates
            Regex("""^\d{10,}"""), // Long numbers (barcodes, etc.)
            Regex("""^[*\-=]+$""") // Separator lines
        )
    }

    /**
     * Extract line items from structured OCR result.
     */
    fun extractItems(ocrResult: StructuredOcrResult): ExtractionResult {
        val items = mutableListOf<ExtractedItem>()
        val unrecognized = mutableListOf<String>()

        for (block in ocrResult.blocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()

                if (shouldSkipLine(lineText)) {
                    continue
                }

                val extractedItem = tryExtractItem(lineText, line.boundingBox)
                if (extractedItem != null) {
                    items.add(extractedItem)
                } else if (lineText.length > 3) {
                    unrecognized.add(lineText)
                }
            }
        }

        // Try to match unrecognized lines with nearby prices
        val recoveredItems = tryRecoverItems(unrecognized, items)
        items.addAll(recoveredItems)

        return ExtractionResult(
            items = items,
            unrecognizedLines = unrecognized - recoveredItems.map { it.rawText }.toSet(),
            confidence = calculateConfidence(items, unrecognized)
        )
    }

    /**
     * Extract items from raw text (simpler approach).
     */
    fun extractItemsFromText(text: String): ExtractionResult {
        val lines = text.lines()
        val items = mutableListOf<ExtractedItem>()
        val unrecognized = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()

            if (shouldSkipLine(trimmed) || trimmed.isBlank()) {
                continue
            }

            val extractedItem = tryExtractItem(trimmed, null)
            if (extractedItem != null) {
                items.add(extractedItem)
            } else if (trimmed.length > 3) {
                unrecognized.add(trimmed)
            }
        }

        return ExtractionResult(
            items = items,
            unrecognizedLines = unrecognized,
            confidence = calculateConfidence(items, unrecognized)
        )
    }

    private fun shouldSkipLine(line: String): Boolean {
        return SKIP_PATTERNS.any { it.containsMatchIn(line) }
    }

    private fun tryExtractItem(
        lineText: String,
        boundingBox: BoundingBox?
    ): ExtractedItem? {
        // Try each price pattern
        for (pricePattern in PRICE_PATTERNS) {
            val priceMatch = pricePattern.find(lineText) ?: continue

            val dollars = priceMatch.groupValues[1].toIntOrNull() ?: continue
            val cents = priceMatch.groupValues[2].toIntOrNull() ?: continue
            val priceInCents = dollars * 100 + cents

            // Extract description (text before the price)
            val description = lineText.substring(0, priceMatch.range.first).trim()
            if (description.length < 2) continue

            // Extract quantity if present
            val quantity = extractQuantity(description)
            val cleanDescription = removeQuantityPrefix(description)

            return ExtractedItem(
                rawText = lineText,
                description = cleanDescription,
                unitPrice = Money(priceInCents.toLong(), "USD"),
                quantity = quantity,
                totalPrice = Money((priceInCents * quantity).toLong(), "USD"),
                confidence = 0.8f,
                boundingBox = boundingBox
            )
        }

        return null
    }

    private fun extractQuantity(description: String): Int {
        for (pattern in QUANTITY_PATTERNS) {
            val match = pattern.find(description)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 1
            }
        }
        return 1
    }

    private fun removeQuantityPrefix(description: String): String {
        var result = description
        for (pattern in QUANTITY_PATTERNS) {
            result = pattern.replace(result, "").trim()
        }
        return result
    }

    private fun tryRecoverItems(
        unrecognized: List<String>,
        existingItems: List<ExtractedItem>
    ): List<ExtractedItem> {
        // Try to pair orphan descriptions with orphan prices
        // This handles cases where price is on separate line
        val recovered = mutableListOf<ExtractedItem>()

        // Look for lines that are just prices
        val orphanPrices = unrecognized.filter { line ->
            PRICE_PATTERNS.any { it.matches(line.trim()) }
        }

        // Look for description-only lines
        val orphanDescriptions = unrecognized.filter { line ->
            !orphanPrices.contains(line) &&
            line.length > 3 &&
            !line.all { it.isDigit() || it in ".,-$" }
        }

        // Try to match them (simple heuristic: sequential pairing)
        val pairs = orphanDescriptions.zip(orphanPrices)
        for ((desc, price) in pairs) {
            val combined = "$desc $price"
            val item = tryExtractItem(combined, null)
            if (item != null) {
                recovered.add(item)
            }
        }

        return recovered
    }

    private fun calculateConfidence(
        items: List<ExtractedItem>,
        unrecognized: List<String>
    ): Float {
        if (items.isEmpty()) return 0f

        val totalLines = items.size + unrecognized.size
        val recognizedRatio = items.size.toFloat() / totalLines

        val avgItemConfidence = items.map { it.confidence }.average().toFloat()

        return (recognizedRatio * 0.6f + avgItemConfidence * 0.4f)
    }
}

data class ExtractedItem(
    val rawText: String,
    val description: String,
    val unitPrice: Money,
    val quantity: Int = 1,
    val totalPrice: Money,
    val confidence: Float,
    val boundingBox: BoundingBox? = null
)

data class ExtractionResult(
    val items: List<ExtractedItem>,
    val unrecognizedLines: List<String>,
    val confidence: Float
)
```

### Step 2: Receipt Parser (Full Pipeline)

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/receipt/ReceiptParser.kt
package com.ledgerlens.receipt

import com.ledgerlens.domain.Money

class ReceiptParser(
    private val itemExtractor: ItemExtractor
) {

    /**
     * Parse a full receipt from OCR result.
     */
    fun parse(ocrResult: StructuredOcrResult): ParsedReceipt {
        // 1. Extract line items
        val extraction = itemExtractor.extractItems(ocrResult)

        // 2. Detect header info (merchant name, date)
        val header = extractHeader(ocrResult)

        // 3. Detect totals section
        val totals = extractTotals(ocrResult)

        // 4. Validate items against totals
        val validation = validateAgainstTotals(extraction.items, totals)

        return ParsedReceipt(
            merchantName = header.merchantName,
            date = header.date,
            items = extraction.items,
            subtotal = totals.subtotal,
            tax = totals.tax,
            tip = totals.tip,
            total = totals.total,
            validation = validation,
            unrecognizedLines = extraction.unrecognizedLines,
            confidence = calculateOverallConfidence(extraction, totals, validation)
        )
    }

    private fun extractHeader(ocrResult: StructuredOcrResult): ReceiptHeader {
        val firstLines = ocrResult.blocks.firstOrNull()?.lines?.take(5) ?: emptyList()

        // Merchant name is usually the first significant line
        val merchantName = firstLines
            .map { it.text.trim() }
            .filter { it.length > 3 && !it.all { c -> c.isDigit() } }
            .firstOrNull()

        // Date pattern
        val datePattern = Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})""")
        val dateText = firstLines
            .map { it.text }
            .firstNotNullOfOrNull { datePattern.find(it)?.value }

        return ReceiptHeader(
            merchantName = merchantName,
            date = dateText
        )
    }

    private fun extractTotals(ocrResult: StructuredOcrResult): ReceiptTotals {
        val allLines = ocrResult.blocks.flatMap { it.lines }.map { it.text }

        var subtotal: Money? = null
        var tax: Money? = null
        var tip: Money? = null
        var total: Money? = null

        val pricePattern = Regex("""[$€£]?\s*(\d{1,4})[.,](\d{2})""")

        for (line in allLines) {
            val lineUpper = line.uppercase()
            val priceMatch = pricePattern.find(line)

            if (priceMatch != null) {
                val dollars = priceMatch.groupValues[1].toIntOrNull() ?: continue
                val cents = priceMatch.groupValues[2].toIntOrNull() ?: continue
                val amount = Money((dollars * 100 + cents).toLong(), "USD")

                when {
                    lineUpper.contains("SUBTOTAL") -> subtotal = amount
                    lineUpper.contains("TAX") && !lineUpper.contains("BEFORE") -> tax = amount
                    lineUpper.contains("TIP") || lineUpper.contains("GRATUITY") -> tip = amount
                    lineUpper.contains("TOTAL") && !lineUpper.contains("SUB") -> total = amount
                }
            }
        }

        return ReceiptTotals(
            subtotal = subtotal,
            tax = tax,
            tip = tip,
            total = total
        )
    }

    private fun validateAgainstTotals(
        items: List<ExtractedItem>,
        totals: ReceiptTotals
    ): ReceiptValidation {
        val itemsSum = items.sumOf { it.totalPrice.minorUnits }
        val itemsSumMoney = Money(itemsSum, "USD")

        val issues = mutableListOf<ValidationIssue>()

        // Check if items sum matches subtotal
        if (totals.subtotal != null) {
            val diff = kotlin.math.abs(itemsSum - totals.subtotal.minorUnits)
            if (diff > 100) { // More than $1 difference
                issues.add(
                    ValidationIssue(
                        type = IssueType.SUBTOTAL_MISMATCH,
                        message = "Items sum (${itemsSumMoney.formatForDisplay()}) doesn't match subtotal (${totals.subtotal.formatForDisplay()})"
                    )
                )
            }
        }

        // Check if subtotal + tax + tip = total
        if (totals.total != null && totals.subtotal != null) {
            val expectedTotal = totals.subtotal.minorUnits +
                    (totals.tax?.minorUnits ?: 0) +
                    (totals.tip?.minorUnits ?: 0)

            if (kotlin.math.abs(expectedTotal - totals.total.minorUnits) > 10) {
                issues.add(
                    ValidationIssue(
                        type = IssueType.TOTAL_MISMATCH,
                        message = "Calculated total doesn't match receipt total"
                    )
                )
            }
        }

        return ReceiptValidation(
            isValid = issues.isEmpty(),
            issues = issues,
            itemsSum = itemsSumMoney
        )
    }

    private fun calculateOverallConfidence(
        extraction: ExtractionResult,
        totals: ReceiptTotals,
        validation: ReceiptValidation
    ): Float {
        var confidence = extraction.confidence

        // Boost confidence if totals detected
        if (totals.total != null) confidence += 0.1f

        // Reduce confidence if validation issues
        if (!validation.isValid) confidence -= 0.2f

        return confidence.coerceIn(0f, 1f)
    }
}

data class ParsedReceipt(
    val merchantName: String?,
    val date: String?,
    val items: List<ExtractedItem>,
    val subtotal: Money?,
    val tax: Money?,
    val tip: Money?,
    val total: Money?,
    val validation: ReceiptValidation,
    val unrecognizedLines: List<String>,
    val confidence: Float
)

data class ReceiptHeader(
    val merchantName: String?,
    val date: String?
)

data class ReceiptTotals(
    val subtotal: Money?,
    val tax: Money?,
    val tip: Money?,
    val total: Money?
)

data class ReceiptValidation(
    val isValid: Boolean,
    val issues: List<ValidationIssue>,
    val itemsSum: Money
)

data class ValidationIssue(
    val type: IssueType,
    val message: String
)

enum class IssueType {
    SUBTOTAL_MISMATCH,
    TOTAL_MISMATCH,
    MISSING_ITEMS,
    DUPLICATE_ITEM
}
```

### Step 3: Manual Item Editor

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/receipt/ManualItemEditor.kt
package com.ledgerlens.receipt

import com.ledgerlens.domain.Money

/**
 * Service for manual item editing when OCR fails.
 */
class ManualItemEditor {

    /**
     * Create item from manual input.
     */
    fun createItem(
        description: String,
        price: String,
        quantity: Int = 1
    ): ExtractedItem? {
        val parsedPrice = parsePrice(price) ?: return null

        return ExtractedItem(
            rawText = "$description $price",
            description = description.trim(),
            unitPrice = parsedPrice,
            quantity = quantity,
            totalPrice = Money(parsedPrice.minorUnits * quantity, parsedPrice.currencyCode),
            confidence = 1.0f // Manual entry is certain
        )
    }

    /**
     * Split an item into multiple items.
     */
    fun splitItem(
        item: ExtractedItem,
        newDescriptions: List<Pair<String, Long>> // description to price in cents
    ): List<ExtractedItem> {
        return newDescriptions.map { (desc, priceInCents) ->
            ExtractedItem(
                rawText = desc,
                description = desc,
                unitPrice = Money(priceInCents, item.unitPrice.currencyCode),
                quantity = 1,
                totalPrice = Money(priceInCents, item.unitPrice.currencyCode),
                confidence = 1.0f
            )
        }
    }

    /**
     * Merge multiple items into one.
     */
    fun mergeItems(
        items: List<ExtractedItem>,
        newDescription: String
    ): ExtractedItem {
        val totalPrice = items.sumOf { it.totalPrice.minorUnits }
        val currency = items.first().unitPrice.currencyCode

        return ExtractedItem(
            rawText = newDescription,
            description = newDescription,
            unitPrice = Money(totalPrice, currency),
            quantity = 1,
            totalPrice = Money(totalPrice, currency),
            confidence = 1.0f
        )
    }

    private fun parsePrice(price: String): Money? {
        val cleaned = price.replace(Regex("""[$€£\s]"""), "")

        // Try different formats
        val patterns = listOf(
            Regex("""(\d+)[.,](\d{2})"""), // 12.99 or 12,99
            Regex("""(\d+)""") // Just dollars
        )

        for (pattern in patterns) {
            val match = pattern.find(cleaned)
            if (match != null) {
                val dollars = match.groupValues[1].toLongOrNull() ?: continue
                val cents = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0
                return Money(dollars * 100 + cents, "USD")
            }
        }

        return null
    }
}
```

---

## Acceptance Criteria

- [ ] Common receipt formats parsed correctly
- [ ] Prices extracted with proper decimal handling
- [ ] Quantity detection works (2x, qty 2, etc.)
- [ ] Header info (merchant, date) extracted
- [ ] Totals (subtotal, tax, tip, total) detected
- [ ] Validation catches mismatches
- [ ] Manual editing fallback works
- [ ] European price formats supported (12,99)

---

## Testing

### Unit Tests
```kotlin
class ItemExtractorTest {
    @Test
    fun `extracts simple item with price`() {
        val result = extractor.extractItemsFromText("Coffee $4.50")
        assertEquals(1, result.items.size)
        assertEquals("Coffee", result.items[0].description)
        assertEquals(450L, result.items[0].unitPrice.minorUnits)
    }

    @Test
    fun `extracts item with quantity`() {
        val result = extractor.extractItemsFromText("2x Bagel $1.99")
        assertEquals(2, result.items[0].quantity)
        assertEquals(398L, result.items[0].totalPrice.minorUnits)
    }

    @Test
    fun `skips total lines`() {
        val result = extractor.extractItemsFromText("TOTAL $15.00")
        assertEquals(0, result.items.size)
    }

    @Test
    fun `handles European format`() {
        val result = extractor.extractItemsFromText("Kaffee 4,50")
        assertEquals(450L, result.items[0].unitPrice.minorUnits)
    }
}
```

### Test Receipts
- Restaurant receipt (with tip)
- Grocery receipt (many items)
- Retail receipt (with tax)
- European receipt (comma decimals)
- Poorly formatted receipt

---

## Estimated Complexity

**High** - Complex pattern matching with many edge cases.
