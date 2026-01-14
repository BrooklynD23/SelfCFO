package com.ledgerlens.receipts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LineParserTest {
    @Test fun `parseLine with name and price`() { val r = LineParser.parseLine("COFFEE GRANDE      $4.95"); assertIs<LineParser.ParseResult.Item>(r); assertEquals("COFFEE GRANDE", r.name); assertEquals(495L, r.totalPrice.minorUnits) }
    @Test fun `parseLine with quantity at price`() { val r = LineParser.parseLine("BAGEL 2 @ $1.99"); assertIs<LineParser.ParseResult.Item>(r); assertEquals("BAGEL", r.name); assertEquals(2.0, r.quantity) }
    @Test fun `parseLine detects subtotal`() { val r = LineParser.parseLine("SUBTOTAL      $15.99"); assertIs<LineParser.ParseResult.Item>(r); assertEquals(ReceiptItemType.SUBTOTAL, r.type) }
    @Test fun `parseLine detects tax`() { val r = LineParser.parseLine("Sales Tax      $1.28"); assertIs<LineParser.ParseResult.Item>(r); assertEquals(ReceiptItemType.TAX, r.type) }
    @Test fun `parseLine detects total`() { val r = LineParser.parseLine("TOTAL      $17.27"); assertIs<LineParser.ParseResult.Item>(r); assertEquals(ReceiptItemType.TOTAL, r.type) }
    @Test fun `parseLine detects tip`() { val r = LineParser.parseLine("TIP      $3.00"); assertIs<LineParser.ParseResult.Item>(r); assertEquals(ReceiptItemType.TIP, r.type) }
    @Test fun `parseLine returns Empty for blank`() { assertIs<LineParser.ParseResult.Empty>(LineParser.parseLine("")) }
    @Test fun `parseLine returns Empty for separator`() { assertIs<LineParser.ParseResult.Empty>(LineParser.parseLine("----------------")) }
    @Test fun `parseLines processes multiple lines`() { val items = LineParser.parseLines(listOf("COFFEE      $3.50", "MUFFIN      $2.50", "TOTAL      $6.00")); assertEquals(3, items.size); assertEquals("COFFEE", items[0].name) }
    @Test fun `parseLines assigns line numbers`() { val items = LineParser.parseLines(listOf("ITEM ONE      $5.00", "ITEM TWO      $3.00")); assertEquals(1, items[0].lineNumber); assertEquals(2, items[1].lineNumber) }
    @Test fun `parseLines preserves raw text`() { val line = "COFFEE      $4.95"; val items = LineParser.parseLines(listOf(line)); assertEquals(line, items[0].rawText) }
}
