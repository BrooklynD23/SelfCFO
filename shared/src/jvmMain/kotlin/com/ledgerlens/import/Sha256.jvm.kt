package com.ledgerlens.import

import java.security.MessageDigest

/**
 * JVM implementation of SHA-256 using java.security.MessageDigest.
 */
actual object Sha256 {
    actual fun digest(input: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input)
    }
}
