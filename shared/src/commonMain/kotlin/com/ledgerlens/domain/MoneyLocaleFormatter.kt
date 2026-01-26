package com.ledgerlens.domain

/**
 * Platform/UI-specific formatting (symbols, grouping, locale rules).
 */
expect object MoneyLocaleFormatter {
    fun format(money: Money, showCurrency: Boolean = true): String
}

/**
 * Convenience for app/UI code.
 */
fun Money.formatForDisplay(showCurrency: Boolean = true): String = MoneyLocaleFormatter.format(this, showCurrency)
