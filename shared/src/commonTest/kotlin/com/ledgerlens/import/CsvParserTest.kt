package com.ledgerlens.import

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class CsvAutoDetectorTest {
    private val detector = CsvAutoDetector()

    @Test
    fun `detect comma delimiter`() {
        val lines = listOf("a,b,c", "1,2,3", "4,5,6")
        assertEquals(',', detector.detectDelimiter(lines))
    }

    @Test
    fun `detect tab delimiter`() {
        val lines = listOf("a\tb\tc", "1\t2\t3", "4\t5\t6")
        assertEquals('\t', detector.detectDelimiter(lines))
    }

    @Test
    fun `detect semicolon delimiter`() {
        val lines = listOf("a;b;c", "1;2;3", "4;5;6")
        assertEquals(';', detector.detectDelimiter(lines))
    }

    @Test
    fun `detect pipe delimiter`() {
        val lines = listOf("a|b|c", "1|2|3", "4|5|6")
        assertEquals('|', detector.detectDelimiter(lines))
    }

    @Test
    fun `detect header row with text headers`() {
        val rows = listOf(
            listOf("Date", "Description", "Amount"),
            listOf("01/15/2024", "Coffee Shop", "12.50"),
            listOf("01/16/2024", "Grocery Store", "45.00")
        )
        assertTrue(detector.detectHeader(rows))
    }

    @Test
    fun `detect no header when first row has numbers`() {
        val rows = listOf(
            listOf("01/15/2024", "Coffee Shop", "12.50"),
            listOf("01/16/2024", "Grocery Store", "45.00")
        )
        assertFalse(detector.detectHeader(rows))
    }

    @Test
    fun `detect column mapping from headers`() {
        val headers = listOf("Date", "Description", "Debit", "Credit", "Balance")
        val mapping = detector.detectColumnMapping(headers)
        assertEquals(0, mapping.dateColumn)
        assertEquals(1, mapping.descriptionColumn)
        assertEquals(2, mapping.debitColumn)
        assertEquals(3, mapping.creditColumn)
        assertEquals(4, mapping.balanceColumn)
        assertNull(mapping.amountColumn)
    }
}

class FlexibleDateParserTest {
    private val parser = FlexibleDateParser()

    @Test
    fun `parse ISO format`() {
        val result = parser.parse("2024-01-15")
        assertEquals(LocalDate(2024, 1, 15), result)
    }

    @Test
    fun `parse US slash format MM-dd-yyyy`() {
        val result = parser.parse("01/15/2024")
        assertEquals(LocalDate(2024, 1, 15), result)
    }

    @Test
    fun `parse two digit year after 2000`() {
        val result = parser.parse("01/15/24")
        assertEquals(LocalDate(2024, 1, 15), result)
    }

    @Test
    fun `parse two digit year before 2000`() {
        val result = parser.parse("01/15/95")
        assertEquals(LocalDate(1995, 1, 15), result)
    }

    @Test
    fun `parse month name format`() {
        val result = parser.parse("15-Jan-2024")
        assertEquals(LocalDate(2024, 1, 15), result)
    }

    @Test
    fun `parse European dot format dd-MM-yyyy`() {
        val result = parser.parse("15.01.2024")
        assertEquals(LocalDate(2024, 1, 15), result)
    }

    @Test
    fun `handle ambiguous date with day greater than 12`() {
        val result = parser.parse("15/01/2024")
        assertEquals(LocalDate(2024, 1, 15), result)
    }

    @Test
    fun `return null for invalid date`() {
        assertNull(parser.parse("not a date"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("99/99/2024"))
    }
}

class CsvParserImplTest {
    private val parser = CsvParserImpl()

    @Test
    fun `parse simple CSV with header`() = runTest {
        val csv = """
            Date,Description,Amount
            01/15/2024,Coffee Shop,12.50
            01/16/2024,Grocery Store,45.00
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(2, success.transactions.size)

        val first = success.transactions[0]
        assertEquals(LocalDate(2024, 1, 15), first.postedDate)
        assertEquals("Coffee Shop", first.descriptionRaw)
        assertEquals(1250L, first.amount.minorUnits)

        val second = success.transactions[1]
        assertEquals(LocalDate(2024, 1, 16), second.postedDate)
        assertEquals("Grocery Store", second.descriptionRaw)
        assertEquals(4500L, second.amount.minorUnits)
    }

    @Test
    fun `parse CSV with separate debit credit columns`() = runTest {
        val csv = """
            Date,Description,Debit,Credit
            01/15/2024,Coffee Shop,12.50,
            01/16/2024,Paycheck,,1000.00
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(2, success.transactions.size)

        assertEquals(-1250L, success.transactions[0].amount.minorUnits)
        assertEquals(100000L, success.transactions[1].amount.minorUnits)
    }

    @Test
    fun `parse CSV with negative amounts`() = runTest {
        val csv = """
            Date,Description,Amount
            01/15/2024,Coffee Shop,-12.50
            01/16/2024,Refund,25.00
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(-1250L, success.transactions[0].amount.minorUnits)
        assertEquals(2500L, success.transactions[1].amount.minorUnits)
    }

    @Test
    fun `parse CSV with accounting negative format`() = runTest {
        val csv = """
            Date,Description,Amount
            01/15/2024,Coffee Shop,(12.50)
            01/16/2024,Refund,25.00
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(-1250L, success.transactions[0].amount.minorUnits)
    }

    @Test
    fun `parse CSV with balance column`() = runTest {
        val csv = """
            Date,Description,Amount,Balance
            01/15/2024,Coffee Shop,-12.50,987.50
            01/16/2024,Grocery,-45.00,942.50
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(98750L, success.transactions[0].balance?.minorUnits)
        assertEquals(94250L, success.transactions[1].balance?.minorUnits)
    }

    @Test
    fun `parse CSV with quoted fields`() = runTest {
        val csv = """
            Date,Description,Amount
            01/15/2024,"Coffee, Tea & More",12.50
            01/16/2024,"Joe's ""Best"" Store",45.00
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals("Coffee, Tea & More", success.transactions[0].descriptionRaw)
        assertEquals("Joe's \"Best\" Store", success.transactions[1].descriptionRaw)
    }

    @Test
    fun `parse CSV with semicolon delimiter`() = runTest {
        val csv = """
            Date;Description;Amount
            01/15/2024;Coffee Shop;12.50
            01/16/2024;Grocery Store;45.00
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(';', success.detectedOptions.delimiter)
        assertEquals(2, success.transactions.size)
    }

    @Test
    fun `parse empty file returns failure`() = runTest {
        val result = parser.parse(byteArrayOf())
        assertTrue(result is CsvParseResult.Failure)
        assertEquals(CsvParseError.EmptyFile, (result as CsvParseResult.Failure).error)
    }

    @Test
    fun `parse CSV without date column returns NeedsMapping`() = runTest {
        val csv = """
            Name,Value,Category
            Item1,100,Cat1
            Item2,200,Cat2
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.NeedsMapping)
    }

    @Test
    fun `parse CSV with different currency`() = runTest {
        val csv = """
            Date,Description,Amount
            01/15/2024,Coffee,1234
        """.trimIndent()

        val options = CsvParseOptions(currencyCode = "JPY")
        val result = parser.parse(csv.encodeToByteArray(), options)

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(1234L, success.transactions[0].amount.minorUnits)
        assertEquals("JPY", success.transactions[0].amount.currencyCode)
        assertEquals(0, success.transactions[0].amount.scale)
    }

    @Test
    fun `parse CSV with thousands separator`() = runTest {
        val csv = """
            Date,Description,Amount
            01/15/2024,Big Purchase,"1,234.56"
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(123456L, success.transactions[0].amount.minorUnits)
    }

    @Test
    fun `detected options are populated`() = runTest {
        val csv = """
            Date,Description,Amount
            01/15/2024,Coffee,12.50
        """.trimIndent()

        val result = parser.parse(csv.encodeToByteArray())

        assertTrue(result is CsvParseResult.Success)
        val success = result as CsvParseResult.Success
        assertEquals(',', success.detectedOptions.delimiter)
        assertEquals(true, success.detectedOptions.hasHeader)
        assertEquals("USD", success.detectedOptions.currencyCode)
    }
}
