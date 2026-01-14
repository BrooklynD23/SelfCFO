package com.ledgerlens.receipts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LineParserTest {

    @Test
    fun `parseLine with name and price separated by spaces`() {
        val result = LineParser.parseLine("COFFEE GRANDE      $4.95")
        assertIs<LineParser.ParseResult.Item>(result)
        assertEquals("COFFEE GRANDE", result.name)
        assertEquals(495L, result.totalPrice.minorUnits)
        assertEquals(1.0, result.quantity)
        assertEquals(ReceiptItemType.PRODUCT, result.type)
    }

    @Test
    fun `parseLine with quantity at price format`() {
        val result = LineParser.parseLine("BAGEL 2 @ $1.99")
        assertIs<LineParser.ParseResult.Item>(result)
        assertEquals("BAGEL", result.name)
        assertEquals(2.0, result.quantity)
        assertEquals(199L, result.unitPrice?.minorUnits)
    }

    @Test
    fun `parseLine detects subtotal`() {
        val result = LineParser.parseLine("SUBTOTAL      $15.99")
        assertIs<LineParser.ParseResult.Item>(result)
        assertEquals(ReceiptItemType.SUBTOTAL, result.type)
        assertEquals(1599L, result.totalPrice.minorUnits)
    }

    @Test
    fun `parseLine detects tax`() {
        val result = LineParser.parseLine("Sales Tax      $1.28")
        assertIs<LineParser.ParseResult.Item>(result)
        assertEquals(ReceiptItemType.TAX, result.type)
        assertEquals(128L, result.totalPrice.minorUnits)
    }

    @Test
    fun `parseLine detects total`() {
        val result = LineParser.parseLine("TOTAL      $17.27")
        assertIs<LineParser.ParseResult.Item>(result)
        assertEquals(ReceiptItemType.TOTAL, result.type)
        assertEquals(1727L, result.totalPrice.minorUnits)
    }

    @Test
    fun `parseLine detects tip`() {
        val result = LineParser.parseLine("TIP      $3.00")
        assertIs<LineParser.ParseResult.Item>(result)
        assertEquals(ReceiptItemType.TIP, result.type)
        assertEquals(300L, result.totalPrice.minorUnits)
    }

    @Test
    fun `parseLine returns Empty for blank line`() {
        val result = LineParser.parseLine("")
        assertIs<LineParser.ParseResult.Empty>(result)
    }

    @Test
    fun `parseLine returns Empty for separator line`() {
        val result = LineParser.parseLine("----------------")
        assertIs<LineParser.ParseResult.Empty>(result)
    }

    @Test
    fun `parseLines processes multiple lines`() {
        val lines = listOf("COFFEE      $3.50", "MUFFIN      $2.50", "SUBTOTAL      $6.00", "TAX      $0.48", "TOTAL      $6.48")
        val items = LineParser.parseLines(lines)
        assertEquals(5, items.size)
        assertEquals("COFFEE", items[0].name)
        assertEquals("MUFFIN", items[1].name)
        assertEquals(ReceiptItemType.SUBTOTAL, items[2].type)
        assertEquals(ReceiptItemType.TAX, items[3].type)
        assertEquals(ReceiptItemType.TOTAL, items[4].type)
    }

    @Test
    fun `parseLines assigns line numbers`() {
        val lines = listOf("ITEM ONE      $5.00", "ITEM TWO      $3.00")
        val items = LineParser.parseLines(lines)
        assertEquals(1, items[0].lineNumber)
        assertEquals(2, items[1].lineNumber)
    }

    @Test
    fun `parseLines preserves raw text`() {
        val line = "COFFEE GRANDE      $4.95"
        val items = LineParser.parseLines(listOf(line))
        assertEquals(1, items.size)
        assertEquals(line, items[0].rawText)
    }
}
