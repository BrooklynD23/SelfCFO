package com.ledgerlens.receipts

import com.ledgerlens.domain.Money

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
    val productItems get() = items.filter { it.type == ReceiptItemType.PRODUCT }
    val taxItems get() = items.filter { it.type == ReceiptItemType.TAX }
    val feeItems get() = items.filter { it.type == ReceiptItemType.FEE }
    val discountItems get() = items.filter { it.type == ReceiptItemType.DISCOUNT }

    val calculatedSubtotal: Money get() = Money.fromMinorUnits(productItems.sumOf { it.totalPrice.minorUnits } + feeItems.sumOf { it.totalPrice.minorUnits } - discountItems.sumOf { it.totalPrice.minorUnits }, currency)
    val calculatedTax: Money get() = Money.fromMinorUnits(taxItems.sumOf { it.totalPrice.minorUnits }, currency)
    val calculatedTip: Money get() = Money.fromMinorUnits(items.filter { it.type == ReceiptItemType.TIP }.sumOf { it.totalPrice.minorUnits }, currency)
    val calculatedTotal: Money get() = Money.fromMinorUnits(calculatedSubtotal.minorUnits + calculatedTax.minorUnits + calculatedTip.minorUnits, currency)

    val productCount get() = productItems.size
    val totalQuantity get() = productItems.sumOf { it.quantity }

    fun isValid(toleranceCents: Long = 5) = (subtotal?.let { kotlin.math.abs(it.minorUnits - calculatedSubtotal.minorUnits) <= toleranceCents } ?: true) && (totalAmount?.let { kotlin.math.abs(it.minorUnits - calculatedTotal.minorUnits) <= toleranceCents } ?: true)
    fun withWarnings(vararg newWarnings: String) = copy(warnings = warnings + newWarnings.toList())

    companion object {
        fun empty(currency: String = "USD") = ExtractedReceipt(emptyList(), currency = currency)
    }
}
