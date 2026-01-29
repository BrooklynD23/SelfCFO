package com.ledgerlens.import

import kotlinx.datetime.LocalDate

/**
 * Minimal multi-format date parser that works in commonMain (no java.time).
 *
 * Supported:
 * - yyyy-MM-dd
 * - MM/dd/yyyy, M/d/yyyy
 * - MM/dd/yy, M/d/yy (assumes 20xx for 00-68, 19xx for 69-99)
 * - dd/MM/yyyy
 * - dd.MM.yyyy
 * - dd-MMM-yyyy (English month abbreviations)
 */
internal class FlexibleDateParser {
    fun parse(dateString: String): LocalDate? {
        val s = dateString.trim()
        if (s.isEmpty()) return null

        // yyyy-MM-dd
        parseIso(s)?.let { return it }

        // MM/dd/yyyy, M/d/yyyy, MM/dd/yy, dd/MM/yyyy
        if (s.contains('/')) {
            val parts = s.split('/')
            if (parts.size == 3) {
                val a = parts[0].toIntOrNull()
                val b = parts[1].toIntOrNull()
                val cRaw = parts[2].toIntOrNull()
                if (a != null && b != null && cRaw != null) {
                    // Heuristic: if first part > 12, treat as dd/MM/yyyy; else MM/dd/...
                    val isDayFirst = a > 12
                    val day = if (isDayFirst) a else b
                    val month = if (isDayFirst) b else a
                    val year = normalizeYear(cRaw)
                    return safeLocalDate(year, month, day)
                }
            }
        }

        // dd.MM.yyyy
        if (s.contains('.')) {
            val parts = s.split('.')
            if (parts.size == 3) {
                val a = parts[0].toIntOrNull()
                val b = parts[1].toIntOrNull()
                val cRaw = parts[2].toIntOrNull()
                if (a != null && b != null && cRaw != null) {
                    // Heuristic: dot format is usually European (dd.MM.yyyy).
                    // If the second part is > 12, treat as MM.dd.yyyy and swap.
                    val isMonthFirst = b > 12
                    val day = if (isMonthFirst) b else a
                    val month = if (isMonthFirst) a else b
                    val year = normalizeYear(cRaw)
                    return safeLocalDate(year, month, day)
                }
            }
        }

        // dd-MMM-yyyy
        if (s.contains('-')) {
            val parts = s.split('-')
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull()
                val month = monthFromAbbrev(parts[1])
                val year = parts[2].toIntOrNull()
                if (day != null && month != null && year != null) {
                    return safeLocalDate(year, month, day)
                }
            }
        }

        return null
    }

    private fun parseIso(s: String): LocalDate? =
        runCatching { LocalDate.parse(s) }.getOrNull()

    private fun normalizeYear(y: Int): Int {
        return if (y in 0..99) {
            if (y <= 68) 2000 + y else 1900 + y
        } else {
            y
        }
    }

    private fun safeLocalDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate(year, month, day) }.getOrNull()

    private fun monthFromAbbrev(s: String): Int? {
        val k = s.trim().lowercase()
        return when (k.take(3)) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> null
        }
    }
}
