package com.ledgerlens.receipts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ItemExtractorTest {

    private val extractor = RegexItemExtractor()

    @Test
    fun `extract simple receipt with items and total`() {
        val receiptText = """
            COFFEE SHOP
            123 Main St
            01/15/2024  10:30 AM
            
            LATTE          $4.50
            CROISSANT      $3.25
            
            SUBTOTAL       $7.75
            TAX            $0.62
            TOTAL          $8.37
        """.trimIndent()

        val result = extractor.extract(receiptText)
        assertIs<ExtractionResult.Success>(result)
        assertEquals("COFFEE SHOP", result.receipt.merchant)
        assertTrue(result.receipt.productItems.isNotEmpty())
        assertNotNull(result.receipt.subtotal)
        assertNotNull(result.receipt.totalAmount)
    }

    @Test
    fun `extract receipt with tip`() {
        val receiptText = """
            RESTAURANT
            
            BURGER            $12.00
            FRIES             $4.00
            
            SUBTOTAL          $16.00
            TAX               $1.28
            TIP               $4.00
            TOTAL             $21.28
        """.trimIndent()

        val result = extractor.extract(receiptText)
        val receipt = result.receiptOrNull
        assertNotNull(receipt)
        assertNotNull(receipt.tipAmount)
        assertEquals(400L, receipt.tipAmount?.minorUnits)
    }

    @Test
    fun `extract detects date`() {
        val receiptText = """
            STORE
            12/25/2024 2:30 PM
            
            ITEM      $5.00
            TOTAL     $5.00
        """.trimIndent()

        val result = extractor.extract(receiptText)
        val receipt = result.receiptOrNull
        assertNotNull(receipt)
        assertNotNull(receipt.date)
        assertTrue(receipt.date!!.contains("2024"))
    }

    @Test
    fun `extract detects payment method`() {
        val receiptText = """
            STORE
            
            ITEM          $10.00
            TOTAL         $10.00
            
            VISA ****1234
        """.trimIndent()

        val result = extractor.extract(receiptText)
        val receipt = result.receiptOrNull
        assertNotNull(receipt)
        assertEquals("VISA", receipt.paymentMethod)
    }

    @Test
    fun `extract returns Failure for empty input`() {
        val result = extractor.extract("")
        assertIs<ExtractionResult.Failure>(result)
    }

    @Test
    fun `extractFromLines works with pre-split lines`() {
        val lines = listOf("STORE NAME", "ITEM      $5.00", "TOTAL     $5.00")
        val result = extractor.extractFromLines(lines)
        assertTrue(result.receiptOrNull != null)
        assertEquals("STORE NAME", result.receiptOrNull?.merchant)
    }

    @Test
    fun `extract calculates receipt confidence`() {
        val receiptText = """
            STORE
            
            ITEM A      $5.00
            ITEM B      $10.00
            
            TOTAL       $15.00
        """.trimIndent()

        val result = extractor.extract(receiptText)
        val receipt = result.receiptOrNull
        assertNotNull(receipt)
        assertTrue(receipt.confidence > 0.0)
        assertTrue(receipt.confidence <= 1.0)
    }

    @Test
    fun `extract structure detection identifies sections`() {
        val receiptText = """
            COFFEE SHOP
            123 Main Street
            Phone: 555-1234
            
            COFFEE        $3.00
            BAGEL         $2.00
            
            SUBTOTAL      $5.00
            TAX           $0.40
            TOTAL         $5.40
            
            Thank you!
        """.trimIndent()

        val result = extractor.extract(receiptText, ExtractionOptions(detectStructure = true))
        assertIs<ExtractionResult.Success>(result)
        assertNotNull(result.structureInfo)
        assertTrue(result.structureInfo!!.hasHeader)
        assertTrue(result.structureInfo!!.hasItems)
        assertTrue(result.structureInfo!!.hasTotals)
    }
}
