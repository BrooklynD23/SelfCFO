package com.ledgerlens.security

/**
 * Platform-specific secure key storage.
 *
 * - Android: Uses Android Keystore with hardware backing when available
 * - Desktop: Uses OS keychain (Windows Credential Manager, macOS Keychain, etc.)
 */
interface PlatformKeystore {
    /**
     * Store wrapped KEK in platform secure storage.
     */
    suspend fun storeKek(wrappedKek: ByteArray): Result<Unit>

    /**
     * Retrieve wrapped KEK from platform secure storage.
     * @return The wrapped KEK, or null if not yet stored
     */
    suspend fun retrieveKek(): Result<ByteArray?>

    /**
     * Delete KEK (for crypto-erase).
     */
    suspend fun deleteKek(): Result<Unit>

    /**
     * Store the salt used for key derivation.
     */
    suspend fun storeSalt(salt: ByteArray): Result<Unit>

    /**
     * Retrieve the salt used for key derivation.
     */
    suspend fun retrieveSalt(): Result<ByteArray?>

    /**
     * Check if hardware-backed storage is available.
     */
    fun isHardwareBacked(): Boolean
}
