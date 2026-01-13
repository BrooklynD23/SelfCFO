package com.ledgerlens.security

/**
 * Platform-specific cryptographically secure random number generation.
 */
expect object SecureRandom {
    /**
     * Generate cryptographically secure random bytes.
     * @param length Number of bytes to generate
     * @return Random bytes
     */
    fun nextBytes(length: Int): ByteArray
}

/**
 * Generate a random salt for key derivation.
 */
fun generateSalt(): ByteArray = SecureRandom.nextBytes(Argon2Params.SALT_LENGTH)

/**
 * Generate a random IV for AES-GCM encryption.
 */
fun generateIv(): ByteArray = SecureRandom.nextBytes(AesGcmConstants.IV_LENGTH)

/**
 * Generate a random AES-256 key.
 */
fun generateAesKey(): ByteArray = SecureRandom.nextBytes(AesGcmConstants.KEY_LENGTH)
