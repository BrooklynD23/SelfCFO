# 07: Receipt Export

## Overview

Implement export and sharing functionality for split results via image, PDF, and text formats.

---

## Implementation Steps

### Step 1: Export Models

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/export/SplitExport.kt
package com.ledgerlens.export

import com.ledgerlens.domain.Money
import com.ledgerlens.splitting.Settlement
import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat {
    TEXT,       // Plain text summary
    MARKDOWN,   // Markdown formatted
    IMAGE,      // PNG image
    PDF,        // PDF document
    JSON        // Machine-readable
}

@Serializable
data class ExportOptions(
    val format: ExportFormat,
    val includeItemDetails: Boolean = true,
    val includeFeeBreakdown: Boolean = true,
    val includePaymentLinks: Boolean = false,
    val participantPaymentMethods: Map<String, String> = emptyMap()
)

@Serializable
data class ExportResult(
    val format: ExportFormat,
    val content: ByteArray? = null,   // For image/PDF
    val textContent: String? = null,  // For text/markdown
    val mimeType: String,
    val suggestedFilename: String
)
```

### Step 2: Text Exporter

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/export/TextExporter.kt
package com.ledgerlens.export

import com.ledgerlens.domain.Money
import com.ledgerlens.splitting.*

class TextExporter {

    /**
     * Generate plain text summary.
     */
    fun exportText(
        split: ReceiptSplit,
        settlement: Settlement,
        options: ExportOptions
    ): String {
        return buildString {
            appendLine("=" .repeat(40))
            appendLine("SPLIT SUMMARY")
            appendLine("=" .repeat(40))
            appendLine()

            // Header
            split.merchantName?.let { appendLine("📍 $it") }
            split.date?.let { appendLine("📅 $it") }
            appendLine()

            // Items
            if (options.includeItemDetails) {
                appendLine("-".repeat(40))
                appendLine("ITEMS")
                appendLine("-".repeat(40))

                for (item in split.items) {
                    val allocated = item.allocations
                        .groupBy { it.participantId }
                        .map { (pid, allocs) ->
                            val name = settlement.participantBalances
                                .find { it.participantId == pid }?.participantName ?: pid
                            name
                        }
                        .joinToString(", ")

                    appendLine("${item.description}")
                    appendLine("  ${item.totalPrice.formatUsd()} → $allocated")
                }
                appendLine()
            }

            // Fee breakdown
            if (options.includeFeeBreakdown) {
                appendLine("-".repeat(40))
                appendLine("FEES")
                appendLine("-".repeat(40))

                val subtotal = split.items.sumOf { it.totalPrice.minorUnits }
                appendLine("Subtotal: ${Money(subtotal, split.currency).formatUsd()}")

                split.fees?.tax?.let { appendLine("Tax: ${it.formatUsd()}") }
                split.fees?.tip?.let { appendLine("Tip: ${it.formatUsd()}") }
                split.fees?.serviceFee?.let { appendLine("Service Fee: ${it.formatUsd()}") }

                appendLine("TOTAL: ${settlement.totalAmount.formatUsd()}")
                appendLine()
            }

            // Per-person breakdown
            appendLine("-".repeat(40))
            appendLine("WHO OWES WHAT")
            appendLine("-".repeat(40))

            for (balance in settlement.participantBalances) {
                appendLine("${balance.participantName}:")
                appendLine("  Items: ${balance.itemsSubtotal.formatUsd()}")
                if (balance.taxShare.minorUnits > 0) {
                    appendLine("  Tax: ${balance.taxShare.formatUsd()}")
                }
                if (balance.tipShare.minorUnits > 0) {
                    appendLine("  Tip: ${balance.tipShare.formatUsd()}")
                }
                appendLine("  Total: ${balance.totalOwed.formatUsd()}")
                appendLine()
            }

            // Settlement transactions
            appendLine("-".repeat(40))
            appendLine("SETTLEMENTS")
            appendLine("-".repeat(40))

            if (settlement.transactions.isEmpty()) {
                appendLine("No payments needed!")
            } else {
                for (tx in settlement.transactions) {
                    val status = if (tx.status == TransactionStatus.COMPLETED) "✓" else "○"
                    appendLine("$status ${tx.fromParticipantName} → ${tx.toParticipantName}: ${tx.amount.formatUsd()}")

                    if (options.includePaymentLinks) {
                        val paymentInfo = options.participantPaymentMethods[tx.toParticipantId]
                        if (paymentInfo != null) {
                            appendLine("   Pay via: $paymentInfo")
                        }
                    }
                }
            }

            appendLine()
            appendLine("=" .repeat(40))
            appendLine("Generated by LedgerLens")
        }
    }

    /**
     * Generate markdown formatted summary.
     */
    fun exportMarkdown(
        split: ReceiptSplit,
        settlement: Settlement,
        options: ExportOptions
    ): String {
        return buildString {
            appendLine("# Split Summary")
            appendLine()

            split.merchantName?.let { appendLine("**$it**") }
            split.date?.let { appendLine("*$it*") }
            appendLine()

            // Items table
            if (options.includeItemDetails && split.items.isNotEmpty()) {
                appendLine("## Items")
                appendLine()
                appendLine("| Item | Price | Split Among |")
                appendLine("|------|-------|-------------|")

                for (item in split.items) {
                    val participants = item.allocations
                        .map { alloc ->
                            settlement.participantBalances
                                .find { it.participantId == alloc.participantId }
                                ?.participantName ?: "?"
                        }
                        .joinToString(", ")

                    appendLine("| ${item.description} | ${item.totalPrice.formatUsd()} | $participants |")
                }
                appendLine()
            }

            // Totals
            appendLine("## Totals")
            appendLine()

            val subtotal = split.items.sumOf { it.totalPrice.minorUnits }
            appendLine("- **Subtotal:** ${Money(subtotal, split.currency).formatUsd()}")
            split.fees?.tax?.let { appendLine("- **Tax:** ${it.formatUsd()}") }
            split.fees?.tip?.let { appendLine("- **Tip:** ${it.formatUsd()}") }
            appendLine("- **Total:** ${settlement.totalAmount.formatUsd()}")
            appendLine()

            // Per-person
            appendLine("## Per Person")
            appendLine()
            appendLine("| Person | Items | Tax | Tip | Total |")
            appendLine("|--------|-------|-----|-----|-------|")

            for (balance in settlement.participantBalances) {
                appendLine("| ${balance.participantName} | ${balance.itemsSubtotal.formatUsd()} | ${balance.taxShare.formatUsd()} | ${balance.tipShare.formatUsd()} | **${balance.totalOwed.formatUsd()}** |")
            }
            appendLine()

            // Settlements
            appendLine("## Settlements")
            appendLine()

            if (settlement.transactions.isEmpty()) {
                appendLine("*No payments needed!*")
            } else {
                for (tx in settlement.transactions) {
                    val checkbox = if (tx.status == TransactionStatus.COMPLETED) "[x]" else "[ ]"
                    appendLine("- $checkbox **${tx.fromParticipantName}** pays **${tx.toParticipantName}**: ${tx.amount.formatUsd()}")
                }
            }

            appendLine()
            appendLine("---")
            appendLine("*Generated by LedgerLens*")
        }
    }

    /**
     * Generate short text for messaging.
     */
    fun exportShortText(settlement: Settlement): String {
        return buildString {
            appendLine("Split: ${settlement.totalAmount.formatUsd()}")

            for (tx in settlement.transactions.filter { it.status == TransactionStatus.PENDING }) {
                appendLine("• ${tx.fromParticipantName} → ${tx.toParticipantName}: ${tx.amount.formatUsd()}")
            }
        }
    }
}
```

### Step 3: Image Exporter

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/export/ImageExporter.kt
package com.ledgerlens.export

/**
 * Platform-specific image generation.
 */
expect class ImageExporter {
    /**
     * Generate PNG image of split summary.
     */
    suspend fun exportImage(
        split: ReceiptSplit,
        settlement: Settlement,
        options: ExportOptions
    ): ByteArray
}

// Android implementation
// shared/src/androidMain/kotlin/com/ledgerlens/export/ImageExporter.kt
actual class ImageExporter {
    actual suspend fun exportImage(
        split: ReceiptSplit,
        settlement: Settlement,
        options: ExportOptions
    ): ByteArray {
        val width = 800
        val height = calculateHeight(split, settlement, options)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.WHITE)

        // Draw content
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 32f
        }

        var y = 60f

        // Title
        paint.textSize = 48f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Split Summary", 40f, y, paint)
        y += 80f

        // Merchant/Date
        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT
        split.merchantName?.let {
            canvas.drawText(it, 40f, y, paint)
            y += 50f
        }

        // Divider
        y += 20f
        canvas.drawLine(40f, y, width - 40f, y, paint)
        y += 40f

        // Per-person breakdown
        paint.textSize = 36f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Who Owes What", 40f, y, paint)
        y += 50f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 28f
        for (balance in settlement.participantBalances) {
            canvas.drawText(
                "${balance.participantName}: ${balance.totalOwed.formatUsd()}",
                60f, y, paint
            )
            y += 45f
        }

        // Settlements
        y += 30f
        canvas.drawLine(40f, y, width - 40f, y, paint)
        y += 40f

        paint.textSize = 36f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Settlements", 40f, y, paint)
        y += 50f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 28f
        for (tx in settlement.transactions) {
            canvas.drawText(
                "${tx.fromParticipantName} → ${tx.toParticipantName}: ${tx.amount.formatUsd()}",
                60f, y, paint
            )
            y += 45f
        }

        // Footer
        y += 40f
        paint.textSize = 20f
        paint.color = Color.GRAY
        canvas.drawText("Generated by LedgerLens", 40f, y, paint)

        // Convert to PNG bytes
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun calculateHeight(
        split: ReceiptSplit,
        settlement: Settlement,
        options: ExportOptions
    ): Int {
        var height = 200 // Header
        height += settlement.participantBalances.size * 50
        height += settlement.transactions.size * 50
        height += 150 // Footer and padding
        return height
    }
}
```

### Step 4: Split Exporter Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/export/SplitExporter.kt
package com.ledgerlens.export

class SplitExporter(
    private val textExporter: TextExporter,
    private val imageExporter: ImageExporter
) {

    /**
     * Export split in specified format.
     */
    suspend fun export(
        split: ReceiptSplit,
        settlement: Settlement,
        options: ExportOptions
    ): ExportResult {
        return when (options.format) {
            ExportFormat.TEXT -> {
                val text = textExporter.exportText(split, settlement, options)
                ExportResult(
                    format = ExportFormat.TEXT,
                    textContent = text,
                    mimeType = "text/plain",
                    suggestedFilename = generateFilename(split, "txt")
                )
            }

            ExportFormat.MARKDOWN -> {
                val markdown = textExporter.exportMarkdown(split, settlement, options)
                ExportResult(
                    format = ExportFormat.MARKDOWN,
                    textContent = markdown,
                    mimeType = "text/markdown",
                    suggestedFilename = generateFilename(split, "md")
                )
            }

            ExportFormat.IMAGE -> {
                val imageBytes = imageExporter.exportImage(split, settlement, options)
                ExportResult(
                    format = ExportFormat.IMAGE,
                    content = imageBytes,
                    mimeType = "image/png",
                    suggestedFilename = generateFilename(split, "png")
                )
            }

            ExportFormat.PDF -> {
                // PDF generation would use platform PDF libraries
                throw UnsupportedOperationException("PDF export not yet implemented")
            }

            ExportFormat.JSON -> {
                val json = Json.encodeToString(
                    SplitExportData.serializer(),
                    SplitExportData(split, settlement)
                )
                ExportResult(
                    format = ExportFormat.JSON,
                    textContent = json,
                    mimeType = "application/json",
                    suggestedFilename = generateFilename(split, "json")
                )
            }
        }
    }

    /**
     * Quick share via platform share sheet.
     */
    suspend fun quickShare(
        split: ReceiptSplit,
        settlement: Settlement
    ): String {
        return textExporter.exportShortText(settlement)
    }

    private fun generateFilename(split: ReceiptSplit, extension: String): String {
        val sanitizedMerchant = split.merchantName
            ?.replace(Regex("[^a-zA-Z0-9]"), "_")
            ?.take(20)
            ?: "split"

        val date = split.date?.replace("/", "-") ?: "undated"

        return "ledgerlens_${sanitizedMerchant}_$date.$extension"
    }
}

@Serializable
data class SplitExportData(
    val split: ReceiptSplit,
    val settlement: Settlement
)
```

### Step 5: Share Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/export/ShareService.kt
package com.ledgerlens.export

/**
 * Platform-specific sharing.
 */
expect class ShareService {
    /**
     * Share text via platform share sheet.
     */
    suspend fun shareText(text: String, subject: String?)

    /**
     * Share file via platform share sheet.
     */
    suspend fun shareFile(
        data: ByteArray,
        mimeType: String,
        filename: String
    )

    /**
     * Copy text to clipboard.
     */
    suspend fun copyToClipboard(text: String)
}

// Android implementation
// shared/src/androidMain/kotlin/com/ledgerlens/export/ShareService.kt
actual class ShareService(
    private val context: Context
) {
    actual suspend fun shareText(text: String, subject: String?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

        val chooser = Intent.createChooser(intent, "Share via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    actual suspend fun shareFile(
        data: ByteArray,
        mimeType: String,
        filename: String
    ) {
        // Write to cache and share via FileProvider
        val cacheDir = context.cacheDir
        val file = File(cacheDir, filename)
        file.writeBytes(data)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    actual suspend fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LedgerLens Split", text)
        clipboard.setPrimaryClip(clip)
    }
}
```

---

## Acceptance Criteria

- [ ] Export to plain text works
- [ ] Export to Markdown works
- [ ] Export to PNG image works
- [ ] Quick share generates short text
- [ ] Copy to clipboard works
- [ ] Platform share sheet integration works
- [ ] Filenames sanitized and descriptive
- [ ] Image includes all summary info
- [ ] Payment links included when requested

---

## Testing

### Unit Tests
```kotlin
class TextExporterTest {
    @Test
    fun `exportText includes all participants`() {
        val text = exporter.exportText(split, settlement, options)

        assertTrue(text.contains("Alice"))
        assertTrue(text.contains("Bob"))
    }

    @Test
    fun `exportShortText is concise`() {
        val text = exporter.exportShortText(settlement)

        assertTrue(text.length < 500)
        assertTrue(text.contains("→"))
    }

    @Test
    fun `markdown format is valid`() {
        val markdown = exporter.exportMarkdown(split, settlement, options)

        assertTrue(markdown.contains("# "))
        assertTrue(markdown.contains("| "))
    }
}
```

---

## Estimated Complexity

**Low** - Text formatting with platform-specific sharing.

