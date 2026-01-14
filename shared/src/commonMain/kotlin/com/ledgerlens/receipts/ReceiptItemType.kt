package com.ledgerlens.receipts

/**
 * Classifies the type of line item on a receipt.
 */
enum class ReceiptItemType {
    /** Regular product or service */
    PRODUCT,

    /** Sales tax or VAT */
    TAX,

    /** Gratuity or tip */
    TIP,

    /** Discount, coupon, or markdown */
    DISCOUNT,

    /** Subtotal before tax/tip */
    SUBTOTAL,

    /** Final total */
    TOTAL,

    /** Service fee, delivery fee, or other charges */
    FEE,

    /** Unclassified line item */
    UNKNOWN;

    val isAggregate: Boolean
        get() = this == SUBTOTAL || this == TOTAL

    val affectsTotal: Boolean
        get() = when (this) {
            PRODUCT, TAX, TIP, FEE -> true
            DISCOUNT -> true
            SUBTOTAL, TOTAL, UNKNOWN -> false
        }

    val isDeduction: Boolean
        get() = this == DISCOUNT

    companion object {
        private val SUBTOTAL_KEYWORDS = setOf(
            "subtotal", "sub-total", "sub total", "merchandise total",
            "items total", "food total", "before tax"
        )

        private val TOTAL_KEYWORDS = setOf(
            "total", "grand total", "amount due", "total due",
            "balance due", "you pay", "charge total", "order total"
        )

        private val TAX_KEYWORDS = setOf(
            "tax", "sales tax", "vat", "gst", "hst", "pst",
            "state tax", "local tax", "city tax"
        )

        private val TIP_KEYWORDS = setOf(
            "tip", "gratuity", "service charge"
        )

        private val DISCOUNT_KEYWORDS = setOf(
            "discount", "coupon", "promo", "savings", "member savings",
            "markdown", "reward", "credit", "off"
        )

        private val FEE_KEYWORDS = setOf(
            "fee", "delivery", "service fee", "convenience fee",
            "processing fee", "handling"
        )

        /**
         * Infer item type from description text.
         */
        fun fromDescription(description: String): ReceiptItemType {
            val lower = description.lowercase().trim()

            return when {
                TOTAL_KEYWORDS.any { lower.contains(it) } &&
                    !SUBTOTAL_KEYWORDS.any { lower.contains(it) } -> TOTAL
                SUBTOTAL_KEYWORDS.any { lower.contains(it) } -> SUBTOTAL
                TAX_KEYWORDS.any { lower.contains(it) } -> TAX
                TIP_KEYWORDS.any { lower.contains(it) } -> TIP
                DISCOUNT_KEYWORDS.any { lower.contains(it) } -> DISCOUNT
                FEE_KEYWORDS.any { lower.contains(it) } -> FEE
                else -> PRODUCT
            }
        }
    }
}
