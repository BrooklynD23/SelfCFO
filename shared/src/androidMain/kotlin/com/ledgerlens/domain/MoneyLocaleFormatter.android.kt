package com.ledgerlens.domain

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Android implementation of MoneyLocaleFormatter.
 * Uses java.text.NumberFormat for locale-aware formatting.
 */
actual object MoneyLocaleFormatter {
    actual fun format(money: Money, showCurrency: Boolean): String {
        val locale = Locale.getDefault()
        val formatter = if (showCurrency) {
            NumberFormat.getCurrencyInstance(locale).apply {
                try {
                    currency = Currency.getInstance(money.currencyCode)
                } catch (e: IllegalArgumentException) {
                    // Unknown currency code, fall back to number format
                    return formatWithSymbol(money)
                }
            }
        } else {
            NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = money.scale
                maximumFractionDigits = money.scale
            }
        }

        val divisor = (1..money.scale).fold(1.0) { acc, _ -> acc * 10.0 }
        val majorUnits = money.minorUnits.toDouble() / divisor

        return formatter.format(majorUnits)
    }

    private fun formatWithSymbol(money: Money): String {
        val symbol = CurrencyMetadata.getSymbol(money.currencyCode)
        val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = money.scale
            maximumFractionDigits = money.scale
        }

        val divisor = (1..money.scale).fold(1.0) { acc, _ -> acc * 10.0 }
        val majorUnits = money.minorUnits.toDouble() / divisor

        return "$symbol${formatter.format(majorUnits)}"
    }
}
