package com.ledgerlens.receipts

import com.ledgerlens.domain.Money

/**
 * Represents a single line item extracted from a receipt.
 */
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

    val effectiveUnitPrice: Money
        get() = unitPrice ?: if (quantity != 1.0) {
            Money.fromMinorUnits(
                minorUnits = (totalPrice.minorUnits / quantity).toLong(),
                currencyCode = totalPrice.currencyCode
            )
        } else {
            totalPrice
        }

    fun isConsistent(toleranceCents: Long = 1): Boolean {
        val expectedTotal = unitPrice?.let {
            Money.fromMinorUnits(
                minorUnits = (it.minorUnits * quantity).toLong(),
                currencyCode = it.currencyCode
            )
        } ?: return true
        val diff = kotlin.math.abs(expectedTotal.minorUnits - totalPrice.minorUnits)
        return diff <= toleranceCents
    }

    val signedAmount: Money
        get() = if (type.isDeduction) -totalPrice.abs() else totalPrice

    companion object {
        fun product(name: String, price: Money, quantity: Double = 1.0, unitPrice: Money? = null, category: String? = null) =
            ReceiptItem(name = name, quantity = quantity, unitPrice = unitPrice, totalPrice = price, type = ReceiptItemType.PRODUCT, category = category)

        fun tax(name: String, amount: Money) = ReceiptItem(name = name, totalPrice = amount, type = ReceiptItemType.TAX)
        fun tip(name: String, amount: Money) = ReceiptItem(name = name, totalPrice = amount, type = ReceiptItemType.TIP)
        fun discount(name: String, amount: Money) = ReceiptItem(name = name, totalPrice = amount.abs(), type = ReceiptItemType.DISCOUNT)
        fun fee(name: String, amount: Money) = ReceiptItem(name = name, totalPrice = amount, type = ReceiptItemType.FEE)
        fun subtotal(amount: Money, name: String = "Subtotal") = ReceiptItem(name = name, totalPrice = amount, type = ReceiptItemType.SUBTOTAL)
        fun total(amount: Money, name: String = "Total") = ReceiptItem(name = name, totalPrice = amount, type = ReceiptItemType.TOTAL)
    }
}
