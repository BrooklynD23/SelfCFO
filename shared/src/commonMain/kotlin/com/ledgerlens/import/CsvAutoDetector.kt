package com.ledgerlens.import

class CsvAutoDetector {

    fun detectEncoding(data: ByteArray): String {
        if (data.size >= 3) {
            if (data[0] == 0xEF.toByte() &&
                data[1] == 0xBB.toByte() &&
                data[2] == 0xBF.toByte()) {
                return "UTF-8-BOM"
            }
        }
        if (data.size >= 2) {
            if (data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) {
                return "UTF-16LE"
            }
            if (data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) {
                return "UTF-16BE"
            }
        }
        return "UTF-8"
    }

    fun stripBom(data: ByteArray, encoding: String): ByteArray {
        return when (encoding) {
            "UTF-8-BOM" -> if (data.size >= 3) data.copyOfRange(3, data.size) else data
            "UTF-16LE", "UTF-16BE" -> if (data.size >= 2) data.copyOfRange(2, data.size) else data
            else -> data
        }
    }

    fun detectDelimiter(lines: List<String>): Char {
        val candidates = listOf(',', '\t', ';', '|')
        val scores = candidates.associateWith { delimiter ->
            scoreDelimiter(lines, delimiter)
        }
        return scores.maxByOrNull { it.value }?.key ?: ','
    }

    private fun scoreDelimiter(lines: List<String>, delimiter: Char): Int {
        if (lines.isEmpty()) return 0

        val counts = lines.take(10).map { line ->
            countDelimiterOutsideQuotes(line, delimiter)
        }

        if (counts.all { it == 0 }) return 0

        val variance = counts.distinct().size
        val avgCount = counts.average()

        return if (variance <= 2 && avgCount > 0) {
            (avgCount * 10 / variance).toInt()
        } else {
            0
        }
    }

    private fun countDelimiterOutsideQuotes(line: String, delimiter: Char): Int {
        var count = 0
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> count++
            }
        }
        return count
    }

    fun detectHasHeader(rows: List<List<String>>): Boolean {
        if (rows.size < 2) return false

        val firstRow = rows.first()
        val dataRows = rows.drop(1).take(5)

        val firstRowNumericCount = firstRow.count { isNumericValue(it) }
        val avgDataNumericCount = dataRows.map { row ->
            row.count { isNumericValue(it) }
        }.average()

        return firstRowNumericCount < avgDataNumericCount * 0.5
    }

    private fun isNumericValue(value: String): Boolean {
        val cleaned = value.replace(Regex("[,$()\\s]"), "")
        return cleaned.matches(Regex("-?\\d+\\.?\\d*")) && cleaned.isNotEmpty()
    }
}
