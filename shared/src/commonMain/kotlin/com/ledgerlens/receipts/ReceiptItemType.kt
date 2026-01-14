package com.ledgerlens.receipts

enum class ReceiptItemType {
    PRODUCT, TAX, TIP, DISCOUNT, SUBTOTAL, TOTAL, FEE, UNKNOWN;

    val isAggregate: Boolean get() = this == SUBTOTAL || this == TOTAL
    val affectsTotal: Boolean get() = this in listOf(PRODUCT, TAX, TIP, FEE, DISCOUNT)
    val isDeduction: Boolean get() = this == DISCOUNT

    companion object {
        private val SUBTOTAL_KW = setOf("subtotal", "sub-total", "sub total", "merchandise total", "items total", "before tax")
        private val TOTAL_KW = setOf("total", "grand total", "amount due", "total due", "balance due", "order total")
        private val TAX_KW = setOf("tax", "sales tax", "vat", "gst", "hst", "pst", "state tax", "local tax")
        private val TIP_KW = setOf("tip", "gratuity", "service charge")
        private val DISCOUNT_KW = setOf("discount", "coupon", "promo", "savings", "markdown", "reward", "credit")
        private val FEE_KW = setOf("fee", "delivery", "service fee", "convenience fee", "processing fee")

        fun fromDescription(description: String): ReceiptItemType {
            val lower = description.lowercase().trim()
            return when {
                TOTAL_KW.any { lower.contains(it) } && !SUBTOTAL_KW.any { lower.contains(it) } -> TOTAL
                SUBTOTAL_KW.any { lower.contains(it) } -> SUBTOTAL
                TAX_KW.any { lower.contains(it) } -> TAX
                TIP_KW.any { lower.contains(it) } -> TIP
                DISCOUNT_KW.any { lower.contains(it) } -> DISCOUNT
                FEE_KW.any { lower.contains(it) } -> FEE
                else -> PRODUCT
            }
        }
    }
}
