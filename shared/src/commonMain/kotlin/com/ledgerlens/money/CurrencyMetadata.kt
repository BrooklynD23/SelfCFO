package com.ledgerlens.money

/**
 * Metadata for ISO 4217 currencies including decimal scale and symbols.
 */
object CurrencyMetadata {

    private data class CurrencyInfo(
        val scale: Int,
        val symbol: String,
        val name: String
    )

    private val currencies = mapOf(
        "USD" to CurrencyInfo(2, "$", "US Dollar"),
        "EUR" to CurrencyInfo(2, "€", "Euro"),
        "GBP" to CurrencyInfo(2, "£", "British Pound"),
        "JPY" to CurrencyInfo(0, "¥", "Japanese Yen"),
        "CAD" to CurrencyInfo(2, "CA$", "Canadian Dollar"),
        "AUD" to CurrencyInfo(2, "A$", "Australian Dollar"),
        "CHF" to CurrencyInfo(2, "CHF", "Swiss Franc"),
        "CNY" to CurrencyInfo(2, "¥", "Chinese Yuan"),
        "INR" to CurrencyInfo(2, "₹", "Indian Rupee"),
        "MXN" to CurrencyInfo(2, "MX$", "Mexican Peso"),
        "BRL" to CurrencyInfo(2, "R$", "Brazilian Real"),
        "KRW" to CurrencyInfo(0, "₩", "South Korean Won"),
        "SGD" to CurrencyInfo(2, "S$", "Singapore Dollar"),
        "HKD" to CurrencyInfo(2, "HK$", "Hong Kong Dollar"),
        "NZD" to CurrencyInfo(2, "NZ$", "New Zealand Dollar"),
        "SEK" to CurrencyInfo(2, "kr", "Swedish Krona"),
        "NOK" to CurrencyInfo(2, "kr", "Norwegian Krone"),
        "DKK" to CurrencyInfo(2, "kr", "Danish Krone"),
        "PLN" to CurrencyInfo(2, "zł", "Polish Zloty"),
        "THB" to CurrencyInfo(2, "฿", "Thai Baht"),
        "TWD" to CurrencyInfo(2, "NT$", "Taiwan Dollar"),
        "ZAR" to CurrencyInfo(2, "R", "South African Rand"),
        "RUB" to CurrencyInfo(2, "₽", "Russian Ruble"),
        "ILS" to CurrencyInfo(2, "₪", "Israeli Shekel"),
        "AED" to CurrencyInfo(2, "د.إ", "UAE Dirham"),
        "PHP" to CurrencyInfo(2, "₱", "Philippine Peso"),
        "CZK" to CurrencyInfo(2, "Kč", "Czech Koruna"),
        "IDR" to CurrencyInfo(2, "Rp", "Indonesian Rupiah"),
        "MYR" to CurrencyInfo(2, "RM", "Malaysian Ringgit"),
        "HUF" to CurrencyInfo(2, "Ft", "Hungarian Forint"),
        "CLP" to CurrencyInfo(0, "$", "Chilean Peso"),
        "SAR" to CurrencyInfo(2, "﷼", "Saudi Riyal"),
        "COP" to CurrencyInfo(2, "$", "Colombian Peso"),
        "TRY" to CurrencyInfo(2, "₺", "Turkish Lira"),
        "ARS" to CurrencyInfo(2, "$", "Argentine Peso"),
        "VND" to CurrencyInfo(0, "₫", "Vietnamese Dong"),
        "EGP" to CurrencyInfo(2, "£", "Egyptian Pound"),
        "BHD" to CurrencyInfo(3, ".د.ب", "Bahraini Dinar"),
        "KWD" to CurrencyInfo(3, "د.ك", "Kuwaiti Dinar"),
        "OMR" to CurrencyInfo(3, "﷼", "Omani Rial")
    )

    fun getScale(currencyCode: String): Int =
        currencies[currencyCode.uppercase()]?.scale ?: 2

    fun getSymbol(currencyCode: String): String =
        currencies[currencyCode.uppercase()]?.symbol ?: currencyCode

    fun getName(currencyCode: String): String =
        currencies[currencyCode.uppercase()]?.name ?: currencyCode

    fun isSupported(currencyCode: String): Boolean =
        currencyCode.uppercase() in currencies

    fun supportedCurrencies(): Set<String> = currencies.keys
}
