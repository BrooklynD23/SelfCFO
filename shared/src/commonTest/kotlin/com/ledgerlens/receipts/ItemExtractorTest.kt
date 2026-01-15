package com.ledgerlens.receipts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ItemExtractorTest {
    private val extractor = RegexItemExtractor()

    @Test fun `extract simple receipt`() { val r = extractor.extract("COFFEE SHOP\n01/15/2024\n\nLATTE      $4.50\nCROISSANT  $3.25\n\nSUBTOTAL   $7.75\nTAX        $0.62\nTOTAL      $8.37"); assertIs<ExtractionResult.Success>(r); assertEquals("COFFEE SHOP", r.receipt.merchant); assertTrue(r.receipt.productItems.isNotEmpty()) }
    @Test fun `extract receipt with tip`() { val r = extractor.extract("RESTAURANT\n\nBURGER     $12.00\n\nSUBTOTAL   $12.00\nTAX        $0.96\nTIP        $4.00\nTOTAL      $16.96"); val rcpt = r.receiptOrNull; assertNotNull(rcpt); assertNotNull(rcpt.tipAmount); assertEquals(400L, rcpt.tipAmount?.minorUnits) }
    @Test fun `extract detects date`() { val r = extractor.extract("STORE\n12/25/2024\n\nITEM      $5.00\nTOTAL     $5.00"); val rcpt = r.receiptOrNull; assertNotNull(rcpt); assertNotNull(rcpt.date); assertTrue(rcpt.date!!.contains("2024")) }
    @Test fun `extract detects payment method`() { val r = extractor.extract("STORE\n\nITEM      $10.00\nTOTAL     $10.00\n\nVISA ****1234"); assertEquals("VISA", r.receiptOrNull?.paymentMethod) }
    @Test fun `extract returns Failure for empty`() { assertIs<ExtractionResult.Failure>(extractor.extract("")) }
    @Test fun `extractFromLines works`() { val r = extractor.extractFromLines(listOf("STORE", "ITEM      $5.00", "TOTAL     $5.00")); assertNotNull(r.receiptOrNull); assertEquals("STORE", r.receiptOrNull?.merchant) }
    @Test fun `extract calculates confidence`() { val r = extractor.extract("STORE\n\nITEM      $5.00\nTOTAL     $5.00"); val rcpt = r.receiptOrNull; assertNotNull(rcpt); assertTrue(rcpt.confidence > 0.0 && rcpt.confidence <= 1.0) }
    @Test fun `extract structure detection`() { val r = extractor.extract("SHOP\n123 Main St\n\nCOFFEE    $3.00\n\nSUBTOTAL  $3.00\nTAX       $0.24\nTOTAL     $3.24\n\nThank you!", ExtractionOptions(detectStructure = true)); assertIs<ExtractionResult.Success>(r); assertNotNull(r.structureInfo); assertTrue(r.structureInfo!!.hasHeader) }
}
