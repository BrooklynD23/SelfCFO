package com.ledgerlens.security

import java.security.MessageDigest

/**
 * JVM implementation of SHA-256 hashing.
 * Used by both Android and Desktop platforms.
 */
actual object Sha256 {
    actual fun hash(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}
