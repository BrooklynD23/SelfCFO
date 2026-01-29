package com.ledgerlens.import

import com.ledgerlens.domain.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatementTemplateTest {

    private val parser = TemplateBasedParser(defaultCurrencyCode = "USD")

    @Test
    fun `generic template matches any statement`() {
        val template = StatementTemplateRegistry.findTemplate("Some random bank statement text")
        assertNotNull(template)
        assertEquals("generic", template.id)
    }

    @Test
    fun `chase template matches chase statements`() {
        val template = StatementTemplateRegistry.findTemplate("CHASE BANK Statement")
        assertNotNull(template)
        assertEquals("chase", template.id)
    }

    @Test
    fun `chase template matches jpmorgan statements`() {
        val template = StatementTemplateRegistry.findTemplate("JPMorgan Chase Bank N.A.")
        assertNotNull(template)
        assertEquals("chase", template.id)
    }

    @Test
    fun `bofa template matches bank of america statements`() {
        val template = StatementTemplateRegistry.findTemplate("Bank of America Statement")
        assertNotNull(template)
        assertEquals("bofa", template.id)
    }

    @Test
    fun `wells fargo template matches wells fargo statements`() {
        val template = StatementTemplateRegistry.findTemplate("Wells Fargo Bank")
        assertNotNull(template)
        assertEquals("wellsfargo", template.id)
    }

    @Test
    fun `extractDate parses MM-DD format`() {
        val template = StatementTemplateRegistry.findTemplate("CHASE")!!
        val result = parser.extractDate("01/15 Purchase at Store 123.45", template)

        assertNotNull(result)
        assertEquals(1, result.date.monthNumber)
        assertEquals(15, result.date.dayOfMonth)
    }

    @Test
    fun `extractDate parses MM-DD-YYYY format`() {
        val genericTemplate = StatementTemplate(
            id = "test",
            bankName = "Test",
            patterns = TemplatePatterns(
                bankIdentifier = Regex("test"),
                transactionRow = Regex(".*"),
                datePattern = Regex("""(\d{2})/(\d{2})/(\d{4})"""),
                amountPattern = Regex("""([\d,]+\.\d{2})""")
            ),
            dateFormats = listOf(DateFormat.MM_DD_YYYY)
        )

        val result = parser.extractDate("01/15/2024 Purchase 123.45", genericTemplate)

        assertNotNull(result)
        assertEquals(2024, result.date.year)
        assertEquals(1, result.date.monthNumber)
        assertEquals(15, result.date.dayOfMonth)
    }

    @Test
    fun `extractAmount parses standard amount`() {
        val template = StatementTemplateRegistry.findTemplate("generic")!!
        val result = parser.extractAmount("01/15 Purchase 123.45", template)

        assertNotNull(result)
        assertEquals(12345L, result.value.minorUnits)
        assertEquals("USD", result.value.currencyCode)
    }

    @Test
    fun `extractAmount parses amount with comma separators`() {
        val template = StatementTemplateRegistry.findTemplate("generic")!!
        val result = parser.extractAmount("01/15 Large Purchase 1,234.56", template)

        assertNotNull(result)
        assertEquals(123456L, result.value.minorUnits)
    }

    @Test
    fun `extractAmount parses negative amount for signed format`() {
        val template = StatementTemplateRegistry.findTemplate("CHASE")!!
        val result = parser.extractAmount("01/15 Withdrawal -50.00", template)

        assertNotNull(result)
        assertEquals(-5000L, result.value.minorUnits)
        assertTrue(result.isDebit)
    }

    @Test
    fun `parseWithTemplate extracts transactions from chase-like text`() {
        val pages = listOf(
            PageText(
                pageNumber = 1,
                lines = listOf(
                    TextLine("CHASE Statement", 1),
                    TextLine("Account Summary", 2),
                    TextLine("01/15 GROCERY STORE PURCHASE -45.67", 3),
                    TextLine("01/16 DIRECT DEPOSIT 1,234.56", 4),
                    TextLine("01/17 ATM WITHDRAWAL -100.00", 5)
                )
            )
        )

        val result = parser.parseWithTemplate(pages)

        assertNotNull(result.template)
        assertEquals("chase", result.template?.id)
        assertTrue(result.transactions.isNotEmpty())
    }

    @Test
    fun `parseWithTemplate skips header rows`() {
        val pages = listOf(
            PageText(
                pageNumber = 1,
                lines = listOf(
                    TextLine("CHASE Bank Statement", 1),
                    TextLine("Beginning Balance 1,000.00", 2), // Should be skipped
                    TextLine("01/15 PURCHASE -45.67", 3),
                    TextLine("Ending Balance 954.33", 4) // Should be skipped
                )
            )
        )

        val result = parser.parseWithTemplate(pages)

        // Should not include balance lines as transactions
        val descriptions = result.transactions.map { it.descriptionRaw.lowercase() }
        assertTrue(descriptions.none { it.contains("beginning balance") })
        assertTrue(descriptions.none { it.contains("ending balance") })
    }

    @Test
    fun `ParsedTransaction properties work correctly`() {
        val highConfidence = ParsedTransaction(
            rowRef = "page:1,line:1",
            postedDate = LocalDate(2024, 1, 15),
            transactionDate = null,
            descriptionRaw = "Test Transaction",
            amount = Money.fromMinorUnits(-5000, "USD"),
            balance = null,
            confidence = 0.95f
        )

        assertTrue(highConfidence.isHighConfidence)
        assertTrue(!highConfidence.needsReview)

        val lowConfidence = highConfidence.copy(confidence = 0.5f)
        assertTrue(!lowConfidence.isHighConfidence)
        assertTrue(lowConfidence.needsReview)
    }

    @Test
    fun `TransactionType determined from amount`() {
        val debit = ParsedTransaction(
            rowRef = "page:1,line:1",
            postedDate = LocalDate(2024, 1, 15),
            transactionDate = null,
            descriptionRaw = "Withdrawal",
            amount = Money.fromMinorUnits(-5000, "USD"),
            balance = null,
            confidence = 0.9f,
            transactionType = TransactionType.DEBIT
        )

        assertEquals(TransactionType.DEBIT, debit.transactionType)
        assertTrue(debit.amount.isNegative)
    }

    @Test
    fun `BoundingBox EMPTY is zero`() {
        val empty = BoundingBox.EMPTY
        assertEquals(0f, empty.x)
        assertEquals(0f, empty.y)
        assertEquals(0f, empty.width)
        assertEquals(0f, empty.height)
    }

    @Test
    fun `DateRange holds start and end dates`() {
        val range = DateRange(
            start = LocalDate(2024, 1, 1),
            end = LocalDate(2024, 1, 31)
        )

        assertEquals(2024, range.start.year)
        assertEquals(1, range.start.monthNumber)
        assertEquals(31, range.end.dayOfMonth)
    }

    @Test
    fun `StatementMetadata contains extraction info`() {
        val metadata = StatementMetadata(
            accountName = "Checking",
            accountNumber = "1234",
            statementPeriod = DateRange(
                LocalDate(2024, 1, 1),
                LocalDate(2024, 1, 31)
            ),
            pageCount = 3,
            extractionMethod = ExtractionMethod.TEXT_BASED,
            bankName = "Chase",
            templateId = "chase"
        )

        assertEquals("Checking", metadata.accountName)
        assertEquals("1234", metadata.accountNumber)
        assertEquals(3, metadata.pageCount)
        assertEquals(ExtractionMethod.TEXT_BASED, metadata.extractionMethod)
    }

    @Test
    fun `PdfParseOptions has sensible defaults`() {
        val options = PdfParseOptions()

        assertEquals(500, options.maxPages)
        assertEquals(50 * 1024 * 1024L, options.maxSizeBytes)
        assertEquals(60_000L, options.timeoutMs)
        assertTrue(options.ocrEnabled)
        assertEquals("eng", options.ocrLanguage)
        assertEquals("USD", options.defaultCurrencyCode)
    }

    @Test
    fun `register custom template overrides existing`() {
        val customChase = StatementTemplate(
            id = "chase",
            bankName = "Custom Chase",
            patterns = TemplatePatterns(
                bankIdentifier = Regex("custom chase"),
                transactionRow = Regex(".*"),
                datePattern = Regex("""(\d{2})/(\d{2})"""),
                amountPattern = Regex("""([\d.]+)""")
            )
        )

        StatementTemplateRegistry.register(customChase)

        val found = StatementTemplateRegistry.findTemplate("custom chase text")
        assertNotNull(found)
        assertEquals("Custom Chase", found.bankName)

        // Re-register built-in to restore state
        StatementTemplateRegistry.register(
            StatementTemplate(
                id = "chase",
                bankName = "Chase",
                patterns = TemplatePatterns(
                    bankIdentifier = Regex("""(?i)chase|jpmorgan"""),
                    transactionRow = Regex("""^\s*(\d{2}/\d{2})\s+(.+?)\s+(-?[\d,]+\.\d{2})\s*$"""),
                    datePattern = Regex("""(\d{2})/(\d{2})"""),
                    amountPattern = Regex("""(-?[\d,]+\.\d{2})""")
                ),
                dateFormats = listOf(DateFormat.MM_DD_YY),
                amountFormat = AmountFormat.SIGNED
            )
        )
    }
}

class ParseErrorTest {

    @Test
    fun `InvalidPdf error contains reason`() {
        val error = ParseError.InvalidPdf("Missing header")
        assertEquals("Missing header", error.reason)
    }

    @Test
    fun `SizeExceeded error contains sizes`() {
        val error = ParseError.SizeExceeded(100_000_000L, 50_000_000L)
        assertEquals(100_000_000L, error.actualSize)
        assertEquals(50_000_000L, error.maxSize)
    }

    @Test
    fun `Timeout error contains timing info`() {
        val error = ParseError.Timeout(65000L, 60000L)
        assertEquals(65000L, error.elapsedMs)
        assertEquals(60000L, error.timeoutMs)
    }
}
