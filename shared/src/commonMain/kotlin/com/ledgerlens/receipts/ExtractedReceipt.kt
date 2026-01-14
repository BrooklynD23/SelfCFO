package com.ledgerlens.receipts

import com.ledgerlens.domain.Money

/**
 * Represents a fully extracted receipt with all line items and metadata.
 *
 * @property items All extracted line items
 * @property merchant Merchant/store name
 * @property date Transaction date (ISO format YYYY-MM-DD)
 * @property time Transaction time (HH:MM format, optional)
 * @property subtotal Extracted subtotal amount
 * @property taxAmount Total tax amount
 * @property tipAmount Tip amount (if applicable)
 * @property totalAmount Final total
 * @property paymentMethod Detected payment method
 * @property currency Currency code
 * @property confidence Overall extraction confidence
 * @property warnings List of validation warnings
 * @property rawText Original receipt text
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
    /**
     * Product items only (excludes tax, tip, fees, subtotal, total).
     */
    val productItems: List<ReceiptItem>
        get() = items.filter { it.type == ReceiptItemType.PRODUCT }

    /**
     * Tax items.
     */
    val taxItems: List<ReceiptItem>
        get() = items.filter { it.type == ReceiptItemType.TAX }

    /**
     * Fee items.
     */
    val feeItems: List<ReceiptItem>
        get() = items.filter { it.type == ReceiptItemType.FEE }

    /**
     * Discount items.
     */
    val discountItems: List<ReceiptItem>
        get() = items.filter { it.type == ReceiptItemType.DISCOUNT }

    /**
     * Calculate sum of product items.
     */
    val calculatedSubtotal: Money
        get() {
            val products = productItems.sumOf { it.totalPrice.minorUnits }
            val fees = feeItems.sumOf { it.totalPrice.minorUnits }
            val discounts = discountItems.sumOf { it.totalPrice.minorUnits }
            return Money.fromMinorUnits(products + fees - discounts, currency)
        }

    /**
     * Calculate sum of tax items.
     */
    val calculatedTax: Money
        get() = Money.fromMinorUnits(taxItems.sumOf { it.totalPrice.minorUnits }, currency)

    /**
     * Calculate sum of tip items.
     */
    val calculatedTip: Money
        get() = Money.fromMinorUnits(
            items.filter { it.type == ReceiptItemType.TIP }.sumOf { it.totalPrice.minorUnits },
            currency
        )

    /**
     * Calculate expected total from items.
     */
    val calculatedTotal: Money
        get() = Money.fromMinorUnits(
            calculatedSubtotal.minorUnits + calculatedTax.minorUnits + calculatedTip.minorUnits,
            currency
        )

    /**
     * Number of distinct products.
     */
    val productCount: Int
        get() = productItems.size

    /**
     * Total quantity of items.
     */
    val totalQuantity: Double
        get() = productItems.sumOf { it.quantity }

    /**
     * Check if totals are consistent.
     */
    fun isValid(toleranceCents: Long = 5): Boolean {
        val subtotalValid = subtotal?.let {
            kotlin.math.abs(it.minorUnits - calculatedSubtotal.minorUnits) <= toleranceCents
        } ?: true

        val totalValid = totalAmount?.let {
            kotlin.math.abs(it.minorUnits - calculatedTotal.minorUnits) <= toleranceCents
        } ?: true

        return subtotalValid && totalValid
    }

    /**
     * Create a copy with additional warnings.
     */
    fun withWarnings(vararg newWarnings: String): ExtractedReceipt =
        copy(warnings = warnings + newWarnings.toList())

    companion object {
        /**
         * Create an empty receipt.
         */
        fun empty(currency: String = "USD"): ExtractedReceipt = ExtractedReceipt(
            items = emptyList(),
            currency = currency
        )

        /**
         * Builder for constructing receipts.
         */
        class Builder(private val currency: String = "USD") {
            private val items = mutableListOf<ReceiptItem>()
            private var merchant: String? = null
            private var date: String? = null
            private var time: String? = null
            private var subtotal: Money? = null
            private var taxAmount: Money? = null
            private var tipAmount: Money? = null
            private var totalAmount: Money? = null
            private var paymentMethod: String? = null
            private var confidence: Double = 1.0
            private val warnings = mutableListOf<String>()
            private var rawText: String? = null

            fun addItem(item: ReceiptItem) = apply { items.add(item) }
            fun addItems(newItems: List<ReceiptItem>) = apply { items.addAll(newItems) }
            fun merchant(value: String?) = apply { merchant = value }
            fun date(value: String?) = apply { date = value }
            fun time(value: String?) = apply { time = value }
            fun subtotal(value: Money?) = apply { subtotal = value }
            fun taxAmount(value: Money?) = apply { taxAmount = value }
            fun tipAmount(value: Money?) = apply { tipAmount = value }
            fun totalAmount(value: Money?) = apply { totalAmount = value }
            fun paymentMethod(value: String?) = apply { paymentMethod = value }
            fun confidence(value: Double) = apply { confidence = value }
            fun addWarning(warning: String) = apply { warnings.add(warning) }
            fun rawText(value: String?) = apply { rawText = value }

            fun build(): ExtractedReceipt = ExtractedReceipt(
                items = items.toList(),
                merchant = merchant,
                date = date,
                time = time,
                subtotal = subtotal,
                taxAmount = taxAmount,
                tipAmount = tipAmount,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                currency = currency,
                confidence = confidence,
                warnings = warnings.toList(),
                rawText = rawText
            )
        }
    }
}
