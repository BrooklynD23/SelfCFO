package com.ledgerlens.receipts

import com.ledgerlens.domain.Money

data class ReceiptItem(
    val name: String,
    val quantity: Double = 1.0,
    val unitPrice: Money? = null,
    val totalPrice: Money,
    val type: ReceiptItemType = ReceiptItemType.PRODUCT,
    val category: String? = null,
    val confidence: Double = 1.0,
    val rawText: String? = null,
    val lineNumber: Int? = null
) {
    init {
        require(quantity > 0) { "Quantity must be positive" }
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
    }

    val effectiveUnitPrice: Money get() = unitPrice ?: if (quantity != 1.0) Money.fromMinorUnits((totalPrice.minorUnits / quantity).toLong(), totalPrice.currencyCode) else totalPrice
    fun isConsistent(toleranceCents: Long = 1): Boolean = unitPrice?.let { kotlin.math.abs((it.minorUnits * quantity).toLong() - totalPrice.minorUnits) <= toleranceCents } ?: true
    val signedAmount: Money get() = if (type.isDeduction) -totalPrice.abs() else totalPrice

    companion object {
        fun product(name: String, price: Money, quantity: Double = 1.0, unitPrice: Money? = null, category: String? = null) = ReceiptItem(name, quantity, unitPrice, price, ReceiptItemType.PRODUCT, category)
        fun tax(name: String, amount: Money) = ReceiptItem(name, totalPrice = amount, type = ReceiptItemType.TAX)
        fun tip(name: String, amount: Money) = ReceiptItem(name, totalPrice = amount, type = ReceiptItemType.TIP)
        fun discount(name: String, amount: Money) = ReceiptItem(name, totalPrice = amount.abs(), type = ReceiptItemType.DISCOUNT)
        fun fee(name: String, amount: Money) = ReceiptItem(name, totalPrice = amount, type = ReceiptItemType.FEE)
        fun subtotal(amount: Money, name: String = "Subtotal") = ReceiptItem(name, totalPrice = amount, type = ReceiptItemType.SUBTOTAL)
        fun total(amount: Money, name: String = "Total") = ReceiptItem(name, totalPrice = amount, type = ReceiptItemType.TOTAL)
    }
}
