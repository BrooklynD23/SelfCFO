package com.ledgerlens.import

import kotlinx.datetime.LocalDate

class FlexibleDateParser {

    fun parse(dateString: String): LocalDate? {
        val cleaned = dateString.trim()

        return tryParseIso(cleaned)
            ?: tryParseSlashFormat(cleaned)
            ?: tryParseDashFormat(cleaned)
            ?: tryParseDotFormat(cleaned)
    }

    private fun tryParseIso(text: String): LocalDate? {
        val match = Regex("""(\d{4})-(\d{2})-(\d{2})""").matchEntire(text) ?: return null
        return try {
            LocalDate(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun tryParseSlashFormat(text: String): LocalDate? {
        val match = Regex("""(\d{1,2})/(\d{1,2})/(\d{2,4})""").matchEntire(text) ?: return null
        return try {
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val yearRaw = match.groupValues[3].toInt()
            val year = normalizeYear(yearRaw)

            resolveMonthDayAmbiguity(first, second, year)
        } catch (e: Exception) {
            null
        }
    }

    private fun tryParseDashFormat(text: String): LocalDate? {
        val numericMatch = Regex("""(\d{1,2})-(\d{1,2})-(\d{2,4})""").matchEntire(text)
        if (numericMatch != null) {
            return try {
                val first = numericMatch.groupValues[1].toInt()
                val second = numericMatch.groupValues[2].toInt()
                val year = normalizeYear(numericMatch.groupValues[3].toInt())
                resolveMonthDayAmbiguity(first, second, year)
            } catch (e: Exception) {
                null
            }
        }

        val monthNameMatch = Regex("""(\d{1,2})-(\w{3})-(\d{2,4})""", RegexOption.IGNORE_CASE).matchEntire(text)
        if (monthNameMatch != null) {
            return try {
                val day = monthNameMatch.groupValues[1].toInt()
                val month = parseMonthName(monthNameMatch.groupValues[2]) ?: return null
                val year = normalizeYear(monthNameMatch.groupValues[3].toInt())
                LocalDate(year, month, day)
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    private fun tryParseDotFormat(text: String): LocalDate? {
        val match = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""").matchEntire(text) ?: return null
        return try {
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = normalizeYear(match.groupValues[3].toInt())
            LocalDate(year, month, day)
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeYear(year: Int): Int {
        return when {
            year >= 100 -> year
            year >= 50 -> 1900 + year
            else -> 2000 + year
        }
    }

    private fun resolveMonthDayAmbiguity(first: Int, second: Int, year: Int): LocalDate? {
        return when {
            first > 12 && second <= 12 -> LocalDate(year, second, first)
            second > 12 && first <= 12 -> LocalDate(year, first, second)
            first <= 12 && second <= 12 -> LocalDate(year, first, second)
            else -> null
        }
    }

    private fun parseMonthName(name: String): Int? {
        val months = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
            "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
            "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )
        return months[name.lowercase().take(3)]
    }
}
