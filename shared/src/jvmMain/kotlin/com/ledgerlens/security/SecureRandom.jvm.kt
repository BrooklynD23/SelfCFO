package com.ledgerlens.security

/**
 * JVM implementation of SecureRandom using java.security.SecureRandom.
 * Used by both Android and Desktop platforms.
 */
actual object SecureRandom {
    private val random = java.security.SecureRandom()

    actual fun nextBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes
    }
}
