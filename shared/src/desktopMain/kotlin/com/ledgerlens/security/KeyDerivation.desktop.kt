package com.ledgerlens.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Desktop (JVM) implementation of key derivation.
 *
 * Note: For production, consider using a native Argon2 library.
 * This implementation uses PBKDF2 as a fallback which is less secure but widely available.
 *
 * TODO: Replace with native Argon2id library for proper support
 */
actual object KeyDerivation {
    actual fun deriveKey(passphrase: String, salt: ByteArray, outputLength: Int): ByteArray {
        // Using PBKDF2-HMAC-SHA256 as fallback
        // In production, use Argon2id via native library
        val iterations = 600_000 // High iteration count for PBKDF2

        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            iterations,
            outputLength * 8
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
