package com.ledgerlens.security

import java.security.MessageDigest

actual object Sha256 {
    actual fun digest(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }
}

