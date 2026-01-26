package com.ledgerlens.domain

/**
 * Parser for converting decimal strings to Money minor units.
 * Uses integer math only - no floating point.
 */
object MoneyParser {
    /**
     * Parse a decimal string into minor units using integer math.
     *
     * Supported inputs (examples):
     * - "12.34"
     * - "-12.34"
     * - "(12.34)"   // accounting negative
     * - "1,234.56"  // thousands separators
     *
     * NOTE: If more fractional digits are provided than the currency `scale`,
     * apply `roundingMode` deterministically.
     */
    fun parseToMinorUnits(amountString: String, scale: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): Long {
        require(scale >= 0) { "scale must be non-negative" }

        var text = amountString.trim()
        require(text.isNotBlank()) { "amountString is blank" }

        // Accounting negatives: "(12.34)"
        var negative = false
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.substring(1, text.length - 1).trim()
        }

        // Leading sign
        if (text.startsWith("+")) text = text.drop(1).trim()
        if (text.startsWith("-")) {
            negative = true
            text = text.drop(1).trim()
        }

        // Keep digits and separators only; other characters (currency symbols, spaces) are ignored.
        text = text.replace(Regex("""[^0-9.,]"""), "")
        require(text.isNotBlank()) { "No digits found in amountString" }

        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')

        val decimalSep: Char? = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot >= 0 -> '.'
            lastComma >= 0 -> {
                // Heuristic: treat comma as decimal only if it looks like a fractional separator.
                val digitsAfter = text.length - lastComma - 1
                if (digitsAfter in 1..maxOf(scale, 1)) ',' else null
            }
            else -> null
        }

        val groupingSep: Char? = when (decimalSep) {
            '.' -> ','
            ',' -> '.'
            else -> ','
        }

        val (wholeRaw, fracRaw) = if (decimalSep != null && text.contains(decimalSep)) {
            val parts = text.split(decimalSep, limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        } else {
            text to ""
        }

        val wholeDigits = wholeRaw.replace(groupingSep.toString(), "").ifBlank { "0" }
        val fracDigits = fracRaw.replace(groupingSep.toString(), "")

        val whole = wholeDigits.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid whole part: $wholeDigits")

        fun pow10Long(exp: Int): Long = (1..exp).fold(1L) { acc, _ -> acc * 10L }
        val factor = pow10Long(scale)

        val keptFrac = when {
            scale == 0 -> ""
            fracDigits.length <= scale -> fracDigits.padEnd(scale, '0')
            else -> fracDigits.substring(0, scale)
        }
        val baseFrac = if (keptFrac.isBlank()) 0L else keptFrac.toLong()

        var minorUnits = whole * factor + baseFrac

        // Rounding if extra fractional digits exist
        if (fracDigits.length > scale) {
            val nextDigit = fracDigits.getOrNull(scale)?.digitToIntOrNull() ?: 0
            val rest = fracDigits.drop(scale + 1)
            val restNonZero = rest.any { it != '0' }

            val roundUp = when (roundingMode) {
                RoundingMode.DOWN -> false
                RoundingMode.UP -> nextDigit != 0 || restNonZero
                RoundingMode.HALF_UP -> nextDigit >= 5
                RoundingMode.HALF_DOWN -> nextDigit > 5 || (nextDigit == 5 && restNonZero)
                RoundingMode.HALF_EVEN -> when {
                    nextDigit > 5 -> true
                    nextDigit < 5 -> false
                    restNonZero -> true
                    else -> (minorUnits % 2L) != 0L
                }
            }

            if (roundUp) minorUnits += 1L
        }

        return if (negative) -minorUnits else minorUnits
    }
}
