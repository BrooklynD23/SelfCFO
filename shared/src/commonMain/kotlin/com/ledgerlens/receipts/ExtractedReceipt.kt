package com.ledgerlens.receipts

import com.ledgerlens.domain.Money

/**
 * Represents a fully extracted receipt with all line items and metadata.
 */
data class ExtractedReceipt(
    val items: List<ReceiptItem>,
    val merchant: String? = null,
    val date: String? = null,
    val time: String? = null,
    val subtotal: Money? = null,
    val taxAmount: Money? = null,
    val tipAmount: Money? = null,
    val totalAmount: Money? = null,
    val paymentMethod: String? = null,
    val currency: String = "USD",
    val confidence: Double = 1.0,
    val warnings: List<String> = emptyList(),
    val rawText: String? = null
) {
    val productItems: List<ReceiptItem> get() = items.filter { it.type == ReceiptItemType.PRODUCT }
    val taxItems: List<ReceiptItem> get() = items.filter { it.type == ReceiptItemType.TAX }
    val feeItems: List<ReceiptItem> get() = items.filter { it.type == ReceiptItemType.FEE }
    val discountItems: List<ReceiptItem> get() = items.filter { it.type == ReceiptItemType.DISCOUNT }

    val calculatedSubtotal: Money
        get() {
            val products = productItems.sumOf { it.totalPrice.minorUnits }
            val fees = feeItems.sumOf { it.totalPrice.minorUnits }
            val discounts = discountItems.sumOf { it.totalPrice.minorUnits }
            return Money.fromMinorUnits(products + fees - discounts, currency)
        }

    val calculatedTax: Money get() = Money.fromMinorUnits(taxItems.sumOf { it.totalPrice.minorUnits }, currency)
    val calculatedTip: Money get() = Money.fromMinorUnits(
        items.filter {
            it.type == ReceiptItemType.TIP
        }.sumOf { it.totalPrice.minorUnits },
        currency
    )
    val calculatedTotal: Money get() = Money.fromMinorUnits(calculatedSubtotal.minorUnits + calculatedTax.minorUnits + calculatedTip.minorUnits, currency)

    val productCount: Int get() = productItems.size
    val totalQuantity: Double get() = productItems.sumOf { it.quantity }

    fun isValid(toleranceCents: Long = 5): Boolean {
        val subtotalValid = subtotal?.let {
            kotlin.math.abs(it.minorUnits - calculatedSubtotal.minorUnits) <= toleranceCents
        } ?: true
        val totalValid = totalAmount?.let {
            kotlin.math.abs(it.minorUnits - calculatedTotal.minorUnits) <= toleranceCents
        } ?: true
        return subtotalValid && totalValid
    }

    fun withWarnings(vararg newWarnings: String) = copy(warnings = warnings + newWarnings.toList())

    companion object {
        fun empty(currency: String = "USD") = ExtractedReceipt(items = emptyList(), currency = currency)
    }
}
