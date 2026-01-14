package com.ledgerlens.receipts

import com.ledgerlens.domain.Money
import kotlin.math.abs

/**
 * Validates extracted receipt data for consistency and correctness.
 */
object ReceiptValidator {

    data class ValidationResult(val isValid: Boolean, val errors: List<ValidationError>, val warnings: List<ValidationWarning>, val corrections: List<SuggestedCorrection>) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
        val hasWarnings: Boolean get() = warnings.isNotEmpty()
        val needsReview: Boolean get() = hasErrors || warnings.any { it.severity == Severity.HIGH }
    }

    sealed class ValidationError {
        abstract val message: String
        data class ItemsSumMismatch(val expectedSubtotal: Money, val calculatedSum: Money, val difference: Money) : ValidationError() { override val message = "Items sum doesn't match subtotal" }
        data class TotalMismatch(val expectedTotal: Money, val calculatedTotal: Money, val difference: Money) : ValidationError() { override val message = "Calculated total doesn't match receipt total" }
        data class NegativePrice(val itemName: String, val price: Money) : ValidationError() { override val message = "Item '$itemName' has unexpected negative price" }
        data class InvalidQuantity(val itemName: String, val quantity: Double) : ValidationError() { override val message = "Item '$itemName' has invalid quantity: $quantity" }
        data class DuplicateTotal(val count: Int) : ValidationError() { override val message = "Multiple total lines found ($count)" }
    }

    enum class Severity { LOW, MEDIUM, HIGH }

    sealed class ValidationWarning {
        abstract val message: String
        abstract val severity: Severity
        data class MissingSubtotal(override val severity: Severity = Severity.MEDIUM) : ValidationWarning() { override val message = "No subtotal found" }
        data class MissingTax(override val severity: Severity = Severity.LOW) : ValidationWarning() { override val message = "No tax line found" }
        data class LargeDiscount(val discountAmount: Money, val percentOfSubtotal: Double, override val severity: Severity = Severity.MEDIUM) : ValidationWarning() { override val message = "Large discount detected" }
        data class UnusualTaxRate(val taxRate: Double, override val severity: Severity = Severity.MEDIUM) : ValidationWarning() { override val message = "Unusual tax rate: ${(taxRate * 100).toInt()}%" }
        data class NoMerchant(override val severity: Severity = Severity.LOW) : ValidationWarning() { override val message = "No merchant name detected" }
        data class NoDate(override val severity: Severity = Severity.LOW) : ValidationWarning() { override val message = "No date detected" }
        data class LowConfidence(val averageConfidence: Double, override val severity: Severity = Severity.MEDIUM) : ValidationWarning() { override val message = "Low extraction confidence" }
        data class FewItems(val itemCount: Int, override val severity: Severity = Severity.LOW) : ValidationWarning() { override val message = "Only $itemCount item(s) extracted" }
    }

    data class SuggestedCorrection(val field: String, val currentValue: String?, val suggestedValue: String, val reason: String)

    data class ValidationConfig(val toleranceCents: Long = 5, val minTaxRate: Double = 0.0, val maxTaxRate: Double = 0.25, val maxDiscountPercent: Double = 0.5, val minConfidenceThreshold: Double = 0.5, val requireMerchant: Boolean = false, val requireDate: Boolean = false)

    fun validate(receipt: ExtractedReceipt) = validate(receipt, ValidationConfig())

    fun validate(receipt: ExtractedReceipt, config: ValidationConfig): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        val corrections = mutableListOf<SuggestedCorrection>()

        receipt.subtotal?.let { subtotal ->
            val calculated = receipt.calculatedSubtotal
            val diff = abs(subtotal.minorUnits - calculated.minorUnits)
            if (diff > config.toleranceCents) {
                errors.add(ValidationError.ItemsSumMismatch(subtotal, calculated, Money.fromMinorUnits(diff, receipt.currency)))
                if (diff <= 100) corrections.add(SuggestedCorrection("subtotal", subtotal.toMajorString(), calculated.toMajorString(), "Calculated from item prices"))
            }
        } ?: warnings.add(ValidationWarning.MissingSubtotal())

        receipt.totalAmount?.let { total ->
            val calculated = receipt.calculatedTotal
            val diff = abs(total.minorUnits - calculated.minorUnits)
            if (diff > config.toleranceCents) errors.add(ValidationError.TotalMismatch(total, calculated, Money.fromMinorUnits(diff, receipt.currency)))
        }

        val totalCount = receipt.items.count { it.type == ReceiptItemType.TOTAL }
        if (totalCount > 1) errors.add(ValidationError.DuplicateTotal(totalCount))

        for (item in receipt.productItems) {
            if (item.totalPrice.isNegative && item.type != ReceiptItemType.DISCOUNT) errors.add(ValidationError.NegativePrice(item.name, item.totalPrice))
            if (item.quantity <= 0) errors.add(ValidationError.InvalidQuantity(item.name, item.quantity))
        }

        val subtotal = receipt.subtotal ?: receipt.calculatedSubtotal
        val tax = receipt.taxAmount ?: receipt.calculatedTax
        if (subtotal.minorUnits > 0 && tax.minorUnits > 0) {
            val taxRate = tax.minorUnits.toDouble() / subtotal.minorUnits
            if (taxRate < config.minTaxRate || taxRate > config.maxTaxRate) warnings.add(ValidationWarning.UnusualTaxRate(taxRate))
        } else if (tax.isZero && receipt.taxItems.isEmpty()) warnings.add(ValidationWarning.MissingTax())

        val totalDiscounts = receipt.discountItems.sumOf { it.totalPrice.minorUnits }
        if (totalDiscounts > 0 && subtotal.minorUnits > 0) {
            val discountPercent = totalDiscounts.toDouble() / subtotal.minorUnits
            if (discountPercent > config.maxDiscountPercent) warnings.add(ValidationWarning.LargeDiscount(Money.fromMinorUnits(totalDiscounts, receipt.currency), discountPercent))
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
        var corrected = receipt
        if (corrected.subtotal == null && corrected.productItems.isNotEmpty()) corrected = corrected.copy(subtotal = corrected.calculatedSubtotal)
        if (corrected.totalAmount == null && (corrected.subtotal != null || corrected.productItems.isNotEmpty())) corrected = corrected.copy(totalAmount = corrected.calculatedTotal)
        if (corrected.taxAmount == null && corrected.taxItems.isNotEmpty()) corrected = corrected.copy(taxAmount = corrected.calculatedTax)
        return corrected
    }
}
