package com.ledgerlens.security

/**
 * Platform-specific SHA-256 hashing.
 *
 * Used for content hashes (SourceFile) and transaction fingerprints (idempotent import).
 */
expect object Sha256 {
    fun digest(input: ByteArray): ByteArray
}

fun sha256Hex(input: ByteArray): String = Sha256.digest(input).toHexString()

fun ByteArray.toHexString(): String =
    joinToString(separator = "") { b -> b.toUByte().toString(16).padStart(2, '0') }
