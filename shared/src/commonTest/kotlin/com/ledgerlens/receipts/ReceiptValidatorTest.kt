package com.ledgerlens.receipts

import com.ledgerlens.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptValidatorTest {
    private fun money(cents: Long) = Money.fromMinorUnits(cents, "USD")

    @Test fun `validate returns valid for consistent receipt`() {
        val r =
            ExtractedReceipt(listOf(ReceiptItem.product("COFFEE", money(350)), ReceiptItem.product("MUFFIN", money(250)), ReceiptItem.subtotal(money(600)), ReceiptItem.tax("TAX", money(48)), ReceiptItem.total(money(648))), subtotal = money(600), taxAmount = money(48), totalAmount = money(648), merchant = "Shop", date = "2024-01-15")
        val res = ReceiptValidator.validate(r)
        assertTrue(res.isValid)
        assertTrue(res.errors.isEmpty())
    }

    @Test fun `validate detects subtotal mismatch`() {
        val r =
            ExtractedReceipt(listOf(ReceiptItem.product("A", money(500)), ReceiptItem.product("B", money(300))), subtotal = money(1000))
        val res = ReceiptValidator.validate(r)
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it is ReceiptValidator.ValidationError.ItemsSumMismatch })
    }

    @Test fun `validate detects total mismatch`() {
        val r =
            ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(1000)), ReceiptItem.tax("TAX", money(80))), subtotal = money(1000), taxAmount = money(80), totalAmount = money(2000))
        assertFalse(ReceiptValidator.validate(r).isValid)
    }

    @Test fun `validate warns about missing subtotal`() {
        val r = ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(500))), totalAmount = money(500))
        assertTrue(ReceiptValidator.validate(r).warnings.any { it is ReceiptValidator.ValidationWarning.MissingSubtotal })
    }

    @Test fun `validate warns about unusual tax rate`() {
        val r =
            ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(1000)), ReceiptItem.tax("TAX", money(500))), subtotal = money(1000), taxAmount = money(500), totalAmount = money(1500))
        assertTrue(ReceiptValidator.validate(r).warnings.any { it is ReceiptValidator.ValidationWarning.UnusualTaxRate })
    }

    @Test fun `validate warns about missing merchant`() {
        assertTrue(
            ReceiptValidator.validate(ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(500))))).warnings.any {
                it is ReceiptValidator.ValidationWarning.NoMerchant
            }
        )
    }

    @Test fun `validate detects duplicate totals`() {
        val r =
            ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(500)), ReceiptItem.total(money(500)), ReceiptItem.total(money(500))))
        assertTrue(ReceiptValidator.validate(r).errors.any { it is ReceiptValidator.ValidationError.DuplicateTotal })
    }

    @Test fun `validate detects negative price`() {
        assertTrue(
            ReceiptValidator.validate(ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(-500))))).errors.any {
                it is ReceiptValidator.ValidationError.NegativePrice
            }
        )
    }

    @Test fun `validate respects tolerance`() {
        val r = ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(500))), subtotal = money(503))
        assertFalse(ReceiptValidator.validate(r, ReceiptValidator.ValidationConfig(toleranceCents = 1)).isValid)
        assertTrue(ReceiptValidator.validate(r, ReceiptValidator.ValidationConfig(toleranceCents = 5)).isValid)
    }

    @Test fun `isValid quick check`() {
        assertTrue(ReceiptValidator.isValid(ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(500))), subtotal = money(500), totalAmount = money(500))))
    }

    @Test fun `autoCorrect fills subtotal`() {
        val r = ExtractedReceipt(listOf(ReceiptItem.product("A", money(300)), ReceiptItem.product("B", money(200))))
        assertEquals(500L, ReceiptValidator.autoCorrect(r).subtotal?.minorUnits)
    }

    @Test fun `autoCorrect fills total`() {
        val r =
            ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(500)), ReceiptItem.tax("TAX", money(40))), subtotal = money(500), taxAmount = money(40))
        assertEquals(540L, ReceiptValidator.autoCorrect(r).totalAmount?.minorUnits)
    }

    @Test fun `calculateExpectedTotal`() {
        val r =
            ExtractedReceipt(listOf(ReceiptItem.product("A", money(1000)), ReceiptItem.product("B", money(500)), ReceiptItem.tax("TAX", money(120)), ReceiptItem.tip("TIP", money(300))))
        assertEquals(1920L, ReceiptValidator.calculateExpectedTotal(r).minorUnits)
    }

    @Test fun `needsReview for errors`() {
        assertTrue(ReceiptValidator.validate(ExtractedReceipt(listOf(ReceiptItem.product("ITEM", money(500))), subtotal = money(1000))).needsReview)
    }
}
