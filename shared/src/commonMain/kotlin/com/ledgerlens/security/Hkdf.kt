package com.ledgerlens.security

/**
 * Platform-specific HKDF (HMAC-based Key Derivation Function) implementation.
 *
 * Used to derive DEKs (Database Encryption Keys, File Encryption Keys) from KEK.
 */
expect object Hkdf {
    /**
     * Derive a key using HKDF-like function.
     * @param keyMaterial Input key material (KEK)
     * @param info Context/application-specific information
     * @param outputLength Desired output key length
     * @return Derived key
     */
    fun derive(keyMaterial: ByteArray, info: ByteArray, outputLength: Int): ByteArray
}
