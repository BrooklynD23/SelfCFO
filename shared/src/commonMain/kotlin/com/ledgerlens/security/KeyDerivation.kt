package com.ledgerlens.security

/**
 * Platform-specific key derivation using Argon2id.
 *
 * Implements secure password hashing per ADR-003.
 */
expect object KeyDerivation {
    /**
     * Derive key from passphrase using Argon2id.
     * @param passphrase User passphrase
     * @param salt Random salt (16 bytes)
     * @param outputLength Desired key length (32 for AES-256)
     * @return Derived key
     */
    fun deriveKey(passphrase: String, salt: ByteArray, outputLength: Int = Argon2Params.KEY_LENGTH): ByteArray
}

/**
 * Argon2id parameters per ADR-003 security requirements.
 *
 * These parameters provide strong resistance against:
 * - GPU-based attacks (memory-hard)
 * - Side-channel attacks (data-independent)
 * - Time-memory trade-off attacks
 */
object Argon2Params {
    /** Memory cost in KB (64 MB) */
    const val MEMORY_KB = 65536

    /** Number of iterations */
    const val ITERATIONS = 3

    /** Parallelism factor */
    const val PARALLELISM = 4

    /** Salt length in bytes */
    const val SALT_LENGTH = 16

    /** Output key length in bytes (AES-256) */
    const val KEY_LENGTH = 32
}
