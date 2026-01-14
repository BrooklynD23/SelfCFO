package com.ledgerlens.receipts

import com.ledgerlens.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptValidatorTest {

    private fun money(cents: Long) = Money.fromMinorUnits(cents, "USD")

    @Test
    fun `validate returns valid for consistent receipt`() {
        val receipt = ExtractedReceipt(
            items = listOf(ReceiptItem.product("COFFEE", money(350)), ReceiptItem.product("MUFFIN", money(250)), ReceiptItem.subtotal(money(600)), ReceiptItem.tax("TAX", money(48)), ReceiptItem.total(money(648))),
            subtotal = money(600), taxAmount = money(48), totalAmount = money(648), merchant = "Coffee Shop", date = "2024-01-15"
        )
        val result = ReceiptValidator.validate(receipt)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate detects subtotal mismatch`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM A", money(500)), ReceiptItem.product("ITEM B", money(300))), subtotal = money(1000), totalAmount = money(1000))
        val result = ReceiptValidator.validate(receipt)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is ReceiptValidator.ValidationError.ItemsSumMismatch })
    }

    @Test
    fun `validate detects total mismatch`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(1000)), ReceiptItem.tax("TAX", money(80))), subtotal = money(1000), taxAmount = money(80), totalAmount = money(2000))
        val result = ReceiptValidator.validate(receipt)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is ReceiptValidator.ValidationError.TotalMismatch })
    }

    @Test
    fun `validate warns about missing subtotal`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(500))), totalAmount = money(500))
        val result = ReceiptValidator.validate(receipt)
        assertTrue(result.warnings.any { it is ReceiptValidator.ValidationWarning.MissingSubtotal })
    }

    @Test
    fun `validate warns about unusual tax rate`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(1000)), ReceiptItem.tax("TAX", money(500))), subtotal = money(1000), taxAmount = money(500), totalAmount = money(1500))
        val result = ReceiptValidator.validate(receipt)
        assertTrue(result.warnings.any { it is ReceiptValidator.ValidationWarning.UnusualTaxRate })
    }

    @Test
    fun `validate warns about missing merchant`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(500))), totalAmount = money(500))
        val result = ReceiptValidator.validate(receipt)
        assertTrue(result.warnings.any { it is ReceiptValidator.ValidationWarning.NoMerchant })
    }

    @Test
    fun `validate detects duplicate totals`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(500)), ReceiptItem.total(money(500)), ReceiptItem.total(money(500))), totalAmount = money(500))
        val result = ReceiptValidator.validate(receipt)
        assertTrue(result.errors.any { it is ReceiptValidator.ValidationError.DuplicateTotal })
    }

    @Test
    fun `validate detects negative price on non-discount`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(-500))))
        val result = ReceiptValidator.validate(receipt)
        assertTrue(result.errors.any { it is ReceiptValidator.ValidationError.NegativePrice })
    }

    @Test
    fun `validate respects tolerance setting`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(500))), subtotal = money(503))
        val strictResult = ReceiptValidator.validate(receipt, ReceiptValidator.ValidationConfig(toleranceCents = 1))
        val lenientResult = ReceiptValidator.validate(receipt, ReceiptValidator.ValidationConfig(toleranceCents = 5))
        assertFalse(strictResult.isValid)
        assertTrue(lenientResult.isValid)
    }

    @Test
    fun `isValid quick check works`() {
        val validReceipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(500))), subtotal = money(500), totalAmount = money(500))
        assertTrue(ReceiptValidator.isValid(validReceipt))
    }

    @Test
    fun `autoCorrect fills in missing subtotal`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM A", money(300)), ReceiptItem.product("ITEM B", money(200))))
        val corrected = ReceiptValidator.autoCorrect(receipt)
        assertEquals(500L, corrected.subtotal?.minorUnits)
    }

    @Test
    fun `autoCorrect fills in missing total`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(500)), ReceiptItem.tax("TAX", money(40))), subtotal = money(500), taxAmount = money(40))
        val corrected = ReceiptValidator.autoCorrect(receipt)
        assertEquals(540L, corrected.totalAmount?.minorUnits)
    }

    @Test
    fun `calculateExpectedTotal computes correctly`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("A", money(1000)), ReceiptItem.product("B", money(500)), ReceiptItem.tax("TAX", money(120)), ReceiptItem.tip("TIP", money(300))))
        val expected = ReceiptValidator.calculateExpectedTotal(receipt)
        assertEquals(1920L, expected.minorUnits)
    }

    @Test
    fun `needsReview is true for errors`() {
        val receipt = ExtractedReceipt(items = listOf(ReceiptItem.product("ITEM", money(500))), subtotal = money(1000))
        val result = ReceiptValidator.validate(receipt)
        assertTrue(result.needsReview)
    }
}
