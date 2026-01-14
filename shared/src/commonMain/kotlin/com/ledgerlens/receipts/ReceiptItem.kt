package com.ledgerlens.receipts

import com.ledgerlens.money.Money

/**
 * Represents a single line item extracted from a receipt.
 *
 * @property name The item description/name
 * @property quantity Number of units (default 1)
 * @property unitPrice Price per unit (null if not separately listed)
 * @property totalPrice Total price for this line item
 * @property type Classification of the item type
 * @property category Optional spending category for the item
 * @property confidence Extraction confidence score (0.0 to 1.0)
 * @property rawText Original text from which this item was extracted
 * @property lineNumber Line number in the original receipt (1-indexed)
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

    /**
     * Computed unit price if quantity > 1 and unit price not provided.
     */
    val effectiveUnitPrice: Money
        get() = unitPrice ?: if (quantity != 1.0) {
            Money(
                minorUnits = (totalPrice.minorUnits / quantity).toLong(),
                currencyCode = totalPrice.currencyCode
            )
        } else {
            totalPrice
        }

    /**
     * Verify that quantity × unitPrice = totalPrice (within tolerance).
     */
    fun isConsistent(toleranceCents: Long = 1): Boolean {
        val expectedTotal = unitPrice?.let {
            Money(
                minorUnits = (it.minorUnits * quantity).toLong(),
                currencyCode = it.currencyCode
            )
        } ?: return true

        val diff = kotlin.math.abs(expectedTotal.minorUnits - totalPrice.minorUnits)
        return diff <= toleranceCents
    }

    /**
     * Returns the signed amount for calculation purposes.
     * Discounts return negative values.
     */
    val signedAmount: Money
        get() = if (type.isDeduction) -totalPrice.abs() else totalPrice

    companion object {
        /**
         * Create a product item.
         */
        fun product(
            name: String,
            price: Money,
            quantity: Double = 1.0,
            unitPrice: Money? = null,
            category: String? = null
        ): ReceiptItem = ReceiptItem(
            name = name,
            quantity = quantity,
            unitPrice = unitPrice,
            totalPrice = price,
            type = ReceiptItemType.PRODUCT,
            category = category
        )

        /**
         * Create a tax item.
         */
        fun tax(name: String, amount: Money): ReceiptItem = ReceiptItem(
            name = name,
            totalPrice = amount,
            type = ReceiptItemType.TAX
        )

        /**
         * Create a tip item.
         */
        fun tip(name: String, amount: Money): ReceiptItem = ReceiptItem(
            name = name,
            totalPrice = amount,
            type = ReceiptItemType.TIP
        )

        /**
         * Create a discount item.
         */
        fun discount(name: String, amount: Money): ReceiptItem = ReceiptItem(
            name = name,
            totalPrice = amount.abs(),
            type = ReceiptItemType.DISCOUNT
        )

        /**
         * Create a fee item.
         */
        fun fee(name: String, amount: Money): ReceiptItem = ReceiptItem(
            name = name,
            totalPrice = amount,
            type = ReceiptItemType.FEE
        )

        /**
         * Create a subtotal item.
         */
        fun subtotal(amount: Money, name: String = "Subtotal"): ReceiptItem = ReceiptItem(
            name = name,
            totalPrice = amount,
            type = ReceiptItemType.SUBTOTAL
        )

        /**
         * Create a total item.
         */
        fun total(amount: Money, name: String = "Total"): ReceiptItem = ReceiptItem(
            name = name,
            totalPrice = amount,
            type = ReceiptItemType.TOTAL
        )
    }
}
