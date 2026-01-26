package com.ledgerlens.receipts

import com.ledgerlens.domain.Money
import kotlin.math.abs

object ReceiptValidator {
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError>,
        val warnings: List<ValidationWarning>,
        val corrections: List<SuggestedCorrection>
    ) {
        val hasErrors get() = errors.isNotEmpty()
        val hasWarnings get() = warnings.isNotEmpty()
        val needsReview get() = hasErrors || warnings.any { it.severity == Severity.HIGH }
    }
    sealed class ValidationError {
        abstract val message: String
        data class ItemsSumMismatch(
            val expectedSubtotal: Money,
            val calculatedSum: Money,
            val difference: Money
        ) : ValidationError() {
            override val message = "Items sum doesn't match subtotal"
        }
        data class TotalMismatch(
            val expectedTotal: Money,
            val calculatedTotal: Money,
            val difference: Money
        ) : ValidationError() {
            override val message = "Total doesn't match"
        }
        data class NegativePrice(
            val itemName: String,
            val price: Money
        ) : ValidationError() {
            override val message = "Negative price on '$itemName'"
        }
        data class InvalidQuantity(
            val itemName: String,
            val quantity: Double
        ) : ValidationError() {
            override val message = "Invalid quantity on '$itemName'"
        }
        data class DuplicateTotal(
            val count: Int
        ) : ValidationError() {
            override val message = "Multiple totals found ($count)"
        }
    }
    enum class Severity { LOW, MEDIUM, HIGH }
    sealed class ValidationWarning {
        abstract val message: String
        abstract val severity: Severity
        data class MissingSubtotal(
            override val severity: Severity = Severity.MEDIUM
        ) : ValidationWarning() {
            override val message = "No subtotal"
        }
        data class MissingTax(
            override val severity: Severity = Severity.LOW
        ) : ValidationWarning() {
            override val message = "No tax"
        }
        data class LargeDiscount(
            val discountAmount: Money,
            val percentOfSubtotal: Double,
            override val severity: Severity = Severity.MEDIUM
        ) : ValidationWarning() {
            override val message = "Large discount"
        }
        data class UnusualTaxRate(
            val taxRate: Double,
            override val severity: Severity = Severity.MEDIUM
        ) : ValidationWarning() {
            override val message = "Unusual tax rate: ${(taxRate * 100).toInt()}%"
        }
        data class NoMerchant(
            override val severity: Severity = Severity.LOW
        ) : ValidationWarning() {
            override val message = "No merchant"
        }
        data class NoDate(
            override val severity: Severity = Severity.LOW
        ) : ValidationWarning() {
            override val message = "No date"
        }
        data class LowConfidence(
            val averageConfidence: Double,
            override val severity: Severity = Severity.MEDIUM
        ) : ValidationWarning() {
            override val message = "Low confidence"
        }
        data class FewItems(
            val itemCount: Int,
            override val severity: Severity = Severity.LOW
        ) : ValidationWarning() {
            override val message = "Only $itemCount item(s)"
        }
    }
    data class SuggestedCorrection(
        val field: String,
        val currentValue: String?,
        val suggestedValue: String,
        val reason: String
    )
    data class ValidationConfig(
        val toleranceCents: Long = 5,
        val minTaxRate: Double = 0.0,
        val maxTaxRate: Double = 0.25,
        val maxDiscountPercent: Double = 0.5,
        val minConfidenceThreshold: Double = 0.5
    )

    fun validate(receipt: ExtractedReceipt) = validate(receipt, ValidationConfig())
    fun validate(receipt: ExtractedReceipt, config: ValidationConfig): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        val corrections = mutableListOf<SuggestedCorrection>()
        receipt.subtotal?.let {
            val calc = receipt.calculatedSubtotal
            val d = abs(it.minorUnits - calc.minorUnits)
            if (d > config.toleranceCents) {
                errors.add(ValidationError.ItemsSumMismatch(it, calc, Money.fromMinorUnits(d, receipt.currency)))
                if (d <= 100) corrections.add(SuggestedCorrection("subtotal", it.toMajorString(), calc.toMajorString(), "Calculated"))
            }
        } ?: warnings.add(ValidationWarning.MissingSubtotal())
        receipt.totalAmount?.let {
            val calc = receipt.calculatedTotal
            val d = abs(it.minorUnits - calc.minorUnits)
            if (d > config.toleranceCents) errors.add(ValidationError.TotalMismatch(it, calc, Money.fromMinorUnits(d, receipt.currency)))
        }
        val tc = receipt.items.count { it.type == ReceiptItemType.TOTAL }
        if (tc > 1) errors.add(ValidationError.DuplicateTotal(tc))
        for (item in receipt.productItems) {
            if (item.totalPrice.isNegative && item.type != ReceiptItemType.DISCOUNT) errors.add(ValidationError.NegativePrice(item.name, item.totalPrice))
            if (item.quantity <= 0) errors.add(ValidationError.InvalidQuantity(item.name, item.quantity))
        }
        val sub = receipt.subtotal ?: receipt.calculatedSubtotal
        val tax = receipt.taxAmount ?: receipt.calculatedTax
        if (sub.minorUnits > 0 && tax.minorUnits > 0) {
            val rate = tax.minorUnits.toDouble() / sub.minorUnits
            if (rate < config.minTaxRate || rate > config.maxTaxRate) warnings.add(ValidationWarning.UnusualTaxRate(rate))
        } else if (tax.isZero && receipt.taxItems.isEmpty()) {
            warnings.add(ValidationWarning.MissingTax())
        }
        val disc = receipt.discountItems.sumOf { it.totalPrice.minorUnits }
        if (disc > 0 && sub.minorUnits > 0) {
            val pct = disc.toDouble() / sub.minorUnits
            if (pct > config.maxDiscountPercent) warnings.add(ValidationWarning.LargeDiscount(Money.fromMinorUnits(disc, receipt.currency), pct))
        }
        if (receipt.merchant.isNullOrBlank()) warnings.add(ValidationWarning.NoMerchant())
        if (receipt.date.isNullOrBlank()) warnings.add(ValidationWarning.NoDate())
        if (receipt.confidence < config.minConfidenceThreshold) warnings.add(ValidationWarning.LowConfidence(receipt.confidence))
        if (receipt.productItems.size <= 1) warnings.add(ValidationWarning.FewItems(receipt.productItems.size))
        return ValidationResult(errors.isEmpty(), errors, warnings, corrections)
    }

    fun isValid(receipt: ExtractedReceipt, toleranceCents: Long = 5) = receipt.isValid(toleranceCents)
    fun calculateExpectedTotal(receipt: ExtractedReceipt) = receipt.calculatedTotal
    fun autoCorrect(receipt: ExtractedReceipt): ExtractedReceipt {
        var c = receipt
        if (c.subtotal == null && c.productItems.isNotEmpty()) c = c.copy(subtotal = c.calculatedSubtotal)
        if (c.totalAmount == null && (c.subtotal != null || c.productItems.isNotEmpty())) c = c.copy(totalAmount = c.calculatedTotal)
        if (c.taxAmount == null && c.taxItems.isNotEmpty()) c = c.copy(taxAmount = c.calculatedTax)
        return c
    }
}
