package com.ledgerlens.import

import com.ledgerlens.domain.Money
import com.ledgerlens.domain.MoneyParser
import com.ledgerlens.domain.RoundingMode
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Template for parsing bank statements from specific institutions.
 *
 * Each bank has its own statement format. Templates define patterns
 * for extracting transactions from those formats.
 */
data class StatementTemplate(
    val id: String,
    val bankName: String,
    val patterns: TemplatePatterns,
    val columnLayout: ColumnLayout? = null,
    val dateFormats: List<DateFormat> = listOf(DateFormat.MM_DD_YYYY),
    val amountFormat: AmountFormat = AmountFormat.STANDARD
)

/**
 * Regex patterns for identifying and parsing statement content.
 */
data class TemplatePatterns(
    val bankIdentifier: Regex,           // Pattern to identify this bank's statements
    val transactionRow: Regex,           // Pattern matching a transaction row
    val datePattern: Regex,              // Pattern for extracting dates
    val amountPattern: Regex,            // Pattern for extracting amounts
    val descriptionPattern: Regex? = null,
    val balancePattern: Regex? = null,
    val headerPattern: Regex? = null,    // Pattern to identify table headers
    val skipPatterns: List<Regex> = emptyList()  // Patterns for rows to skip
)

/**
 * Column layout for tabular statements.
 */
data class ColumnLayout(
    val dateColumn: Int,
    val descriptionColumn: Int,
    val amountColumn: Int,
    val balanceColumn: Int? = null,
    val debitColumn: Int? = null,   // Some banks have separate debit/credit columns
    val creditColumn: Int? = null,
    val checkNumberColumn: Int? = null
)

/**
 * Amount formatting style.
 */
enum class AmountFormat {
    STANDARD,           // $1,234.56 or 1,234.56
    SIGNED,             // -1,234.56 for debits
    PARENTHESES,        // (1,234.56) for debits
    SEPARATE_COLUMNS,   // Debit and credit in separate columns
    CR_DR_SUFFIX        // 1,234.56 CR or 1,234.56 DR
}

/**
 * Template registry and matcher.
 */
object StatementTemplateRegistry {
    private val templates = mutableListOf<StatementTemplate>()

    init {
        registerBuiltInTemplates()
    }

    /**
     * Find matching template for a statement.
     */
    fun findTemplate(text: String): StatementTemplate? {
        return templates.firstOrNull { template ->
            template.patterns.bankIdentifier.containsMatchIn(text)
        }
    }

    /**
     * Register a custom template.
     */
    fun register(template: StatementTemplate) {
        templates.removeAll { it.id == template.id }
        if (template.id == "generic") {
            templates.add(template)
        } else {
            templates.add(0, template)
        }
    }

    /**
     * Get all registered templates.
     */
    fun getAll(): List<StatementTemplate> = templates.toList()

    private fun registerBuiltInTemplates() {
        // Chase Bank
        register(createChaseTemplate())

        // Bank of America
        register(createBofATemplate())

        // Wells Fargo
        register(createWellsFargoTemplate())

        // Generic template as fallback (must be lowest priority)
        register(createGenericTemplate())
    }

    private fun createGenericTemplate() = StatementTemplate(
        id = "generic",
        bankName = "Generic",
        patterns = TemplatePatterns(
            bankIdentifier = Regex(".*"),  // Matches anything as fallback
            transactionRow = Regex("""^\s*(\d{1,2}[/\-]\d{1,2}[/\-]?\d{0,4})\s+(.+?)\s+([\d,]+\.\d{2})\s*$"""),
            datePattern = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-]?(\d{2,4})?"""),
            amountPattern = Regex("""\$?([\d,]+\.\d{2})""")
        ),
        dateFormats = listOf(DateFormat.MM_DD_YYYY, DateFormat.MM_DD_YY)
    )

    private fun createChaseTemplate() = StatementTemplate(
        id = "chase",
        bankName = "Chase",
        patterns = TemplatePatterns(
            bankIdentifier = Regex("""(?i)chase|jpmorgan"""),
            transactionRow = Regex("""^\s*(\d{2}/\d{2})\s+(.+?)\s+(-?[\d,]+\.\d{2})\s*$"""),
            datePattern = Regex("""(\d{2})/(\d{2})"""),
            amountPattern = Regex("""(-?[\d,]+\.\d{2})"""),
            skipPatterns = listOf(
                Regex("""(?i)beginning balance"""),
                Regex("""(?i)ending balance"""),
                Regex("""(?i)account summary""")
            )
        ),
        dateFormats = listOf(DateFormat.MM_DD_YY),
        amountFormat = AmountFormat.SIGNED
    )

    private fun createBofATemplate() = StatementTemplate(
        id = "bofa",
        bankName = "Bank of America",
        patterns = TemplatePatterns(
            bankIdentifier = Regex("""(?i)bank of america|bofa"""),
            transactionRow = Regex("""^\s*(\d{2}/\d{2}/\d{2})\s+(.+?)\s+([\d,]+\.\d{2})\s*$"""),
            datePattern = Regex("""(\d{2})/(\d{2})/(\d{2})"""),
            amountPattern = Regex("""([\d,]+\.\d{2})"""),
            skipPatterns = listOf(
                Regex("""(?i)continued on"""),
                Regex("""(?i)page \d+""")
            )
        ),
        dateFormats = listOf(DateFormat.MM_DD_YY)
    )

    private fun createWellsFargoTemplate() = StatementTemplate(
        id = "wellsfargo",
        bankName = "Wells Fargo",
        patterns = TemplatePatterns(
            bankIdentifier = Regex("""(?i)wells\s*fargo"""),
            transactionRow = Regex("""^\s*(\d{1,2}/\d{1,2})\s+(.+?)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})?\s*$"""),
            datePattern = Regex("""(\d{1,2})/(\d{1,2})"""),
            amountPattern = Regex("""([\d,]+\.\d{2})"""),
            balancePattern = Regex("""([\d,]+\.\d{2})\s*$""")
        ),
        dateFormats = listOf(DateFormat.MM_DD_YY),
        columnLayout = ColumnLayout(
            dateColumn = 0,
            descriptionColumn = 1,
            amountColumn = 2,
            balanceColumn = 3
        )
    )
}

/**
 * Parser that uses templates to extract transactions.
 */
class TemplateBasedParser(
    private val defaultCurrencyCode: String = "USD"
) {
    /**
     * Parse text using the best matching template.
     */
    fun parseWithTemplate(
        pages: List<PageText>,
        template: StatementTemplate? = null
    ): TemplateParseResult {
        val allText = pages.joinToString("\n") { page ->
            page.lines.joinToString("\n") { it.text }
        }

        val selectedTemplate = template
            ?: StatementTemplateRegistry.findTemplate(allText)
            ?: return TemplateParseResult(
                transactions = emptyList(),
                template = null,
                warnings = listOf(ParseWarning(
                    WarningCode.UNSUPPORTED_FORMAT,
                    "No matching template found"
                ))
            )

        val transactions = mutableListOf<ParsedTransaction>()
        val warnings = mutableListOf<ParseWarning>()

        for (page in pages) {
            for (line in page.lines) {
                if (shouldSkipLine(line.text, selectedTemplate)) continue

                val parsed = parseTransactionLine(
                    line = line,
                    pageNumber = page.pageNumber,
                    template = selectedTemplate
                )

                when (parsed) {
                    is LineParseResult.Success -> transactions.add(parsed.transaction)
                    is LineParseResult.Warning -> warnings.add(parsed.warning)
                    is LineParseResult.Skip -> { /* ignore */ }
                }
            }
        }

        return TemplateParseResult(
            transactions = transactions,
            template = selectedTemplate,
            warnings = warnings
        )
    }

    private fun shouldSkipLine(text: String, template: StatementTemplate): Boolean {
        if (text.isBlank()) return true
        return template.patterns.skipPatterns.any { it.containsMatchIn(text) }
    }

    private fun parseTransactionLine(
        line: TextLine,
        pageNumber: Int,
        template: StatementTemplate
    ): LineParseResult {
        val text = line.text

        // Try to match transaction row pattern
        val rowMatch = template.patterns.transactionRow.find(text)
            ?: return LineParseResult.Skip

        // Extract date
        val date = extractDate(text, template)
            ?: return LineParseResult.Warning(ParseWarning(
                WarningCode.AMBIGUOUS_DATE,
                "Could not parse date from: ${text.take(50)}",
                "page:$pageNumber,line:${line.lineNumber}"
            ))

        // Extract amount
        val amount = extractAmount(text, template)
            ?: return LineParseResult.Warning(ParseWarning(
                WarningCode.AMBIGUOUS_AMOUNT,
                "Could not parse amount from: ${text.take(50)}",
                "page:$pageNumber,line:${line.lineNumber}"
            ))

        // Extract description
        val description = extractDescription(text, template, date, amount)

        // Extract balance if pattern exists
        val balance = template.patterns.balancePattern?.let { pattern ->
            extractBalance(text, pattern)
        }

        val transaction = ParsedTransaction(
            rowRef = "page:$pageNumber,line:${line.lineNumber}",
            postedDate = date.date,
            transactionDate = null,
            descriptionRaw = description,
            amount = amount.value,
            balance = balance,
            confidence = calculateConfidence(date, amount, line),
            transactionType = determineTransactionType(amount)
        )

        return LineParseResult.Success(transaction)
    }

    internal fun extractDate(text: String, template: StatementTemplate): ExtractedDate? {
        val match = template.patterns.datePattern.find(text) ?: return null

        return try {
            val groups = match.groupValues.drop(1).filter { it.isNotEmpty() }
            if (groups.size < 2) return null

            val (part1, part2) = groups[0].toInt() to groups[1].toInt()
            val yearPart = groups.getOrNull(2)?.toInt()

            // Determine year
            val year = when {
                yearPart == null -> kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).year
                yearPart < 100 -> 2000 + yearPart
                else -> yearPart
            }

            // Parse based on expected format
            val format = template.dateFormats.firstOrNull() ?: DateFormat.MM_DD_YYYY
            val (month, day) = when (format) {
                DateFormat.DD_MM_YYYY, DateFormat.DD_MMM_YYYY -> part2 to part1
                else -> part1 to part2
            }

            ExtractedDate(
                date = LocalDate(year, month, day),
                confidence = if (yearPart != null) 0.95f else 0.8f,
                rawText = match.value,
                format = format
            )
        } catch (e: Exception) {
            null
        }
    }

    internal fun extractAmount(text: String, template: StatementTemplate): ExtractedAmount? {
        val match = template.patterns.amountPattern.find(text) ?: return null

        return try {
            val rawAmount = match.groupValues[1]
            val cleanAmount = rawAmount.replace(",", "")

            val money = Money.parseMajor(cleanAmount, defaultCurrencyCode, RoundingMode.HALF_UP)

            // Determine if debit based on format
            val isDebit = when (template.amountFormat) {
                AmountFormat.SIGNED -> {
                    // Prefer sign information from the parsed value and the matched substring.
                    money.isNegative ||
                        match.value.trim().startsWith("-") ||
                        (match.range.first > 0 && text[match.range.first - 1] == '-')
                }
                AmountFormat.PARENTHESES -> text.contains("($rawAmount)") || text.contains("( $rawAmount )")
                AmountFormat.CR_DR_SUFFIX -> text.contains(Regex("""$rawAmount\s*DR""", RegexOption.IGNORE_CASE))
                else -> false  // Cannot determine from format alone
            }

            ExtractedAmount(
                value = if (isDebit) -money.abs() else money.abs(),
                isDebit = isDebit,
                confidence = 0.9f,
                rawText = match.value
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractDescription(
        text: String,
        template: StatementTemplate,
        date: ExtractedDate,
        amount: ExtractedAmount
    ): String {
        // Remove date and amount from text to get description
        var description = text
            .replace(date.rawText, "")
            .replace(amount.rawText, "")
            .replace(Regex("""\$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Use custom pattern if available
        template.patterns.descriptionPattern?.let { pattern ->
            pattern.find(text)?.let { match ->
                description = match.groupValues.getOrElse(1) { match.value }
            }
        }

        return description
    }

    private fun extractBalance(text: String, pattern: Regex): Money? {
        val match = pattern.find(text) ?: return null
        return try {
            val cleanAmount = match.groupValues[1].replace(",", "")
            Money.parseMajor(cleanAmount, defaultCurrencyCode, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateConfidence(
        date: ExtractedDate,
        amount: ExtractedAmount,
        line: TextLine
    ): Float {
        var confidence = (date.confidence + amount.confidence + line.confidence) / 3f

        // Boost confidence if all parts parsed cleanly
        if (date.confidence > 0.9f && amount.confidence > 0.9f) {
            confidence = minOf(confidence + 0.05f, 1.0f)
        }

        return confidence
    }

    private fun determineTransactionType(amount: ExtractedAmount): TransactionType {
        return when {
            amount.isDebit -> TransactionType.DEBIT
            amount.value.isNegative -> TransactionType.DEBIT
            amount.value.isPositive -> TransactionType.CREDIT
            else -> TransactionType.UNKNOWN
        }
    }
}

/**
 * Result of parsing with a template.
 */
data class TemplateParseResult(
    val transactions: List<ParsedTransaction>,
    val template: StatementTemplate?,
    val warnings: List<ParseWarning>
)

/**
 * Result of parsing a single line.
 */
sealed class LineParseResult {
    data class Success(val transaction: ParsedTransaction) : LineParseResult()
    data class Warning(val warning: ParseWarning) : LineParseResult()
    data object Skip : LineParseResult()
}
