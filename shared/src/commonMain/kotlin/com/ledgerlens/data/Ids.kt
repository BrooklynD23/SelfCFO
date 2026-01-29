package com.ledgerlens.data

import com.ledgerlens.security.SecureRandom

/**
 * ID generation utilities.
 *
 * We use UUIDv4-formatted strings for stable, cross-platform identifiers.
 */
object Ids {
    fun newId(prefix: String? = null): String {
        val uuid = uuidV4()
        return if (prefix.isNullOrBlank()) uuid else "$prefix-$uuid"
    }

    private fun uuidV4(): String {
        val b = SecureRandom.nextBytes(16)
        // RFC 4122: version 4
        b[6] = ((b[6].toInt() and 0x0F) or 0x40).toByte()
        // RFC 4122: variant 10xx
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()

        fun hex(i: Int): String = b[i].toUByte().toString(16).padStart(2, '0')

        return buildString(36) {
            append(hex(0))
            append(hex(1))
            append(hex(2))
            append(hex(3))
            append('-')
            append(hex(4))
            append(hex(5))
            append('-')
            append(hex(6))
            append(hex(7))
            append('-')
            append(hex(8))
            append(hex(9))
            append('-')
            append(hex(10))
            append(hex(11))
            append(hex(12))
            append(hex(13))
            append(hex(14))
            append(hex(15))
        }
    }
}
