package com.ledgerlens.security

/**
 * File encryption interface using AES-256-GCM.
 * 
 * Provides authenticated encryption for attachments (receipts, statements).
 */
interface FileEncryption {
    /**
     * Encrypt file data.
     * @param data Plaintext data
     * @param key AES-256 key (32 bytes)
     * @return IV (12 bytes) + ciphertext + auth tag (16 bytes)
     */
    fun encrypt(data: ByteArray, key: ByteArray): ByteArray

    /**
     * Decrypt file data.
     * @param encryptedData IV + ciphertext + auth tag
     * @param key AES-256 key (32 bytes)
     * @return Plaintext data
     * @throws SecurityException if authentication fails
     */
    fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray
}

/**
 * AES-GCM encryption constants.
 */
object AesGcmConstants {
    /** Initialization vector length in bytes */
    const val IV_LENGTH = 12
    
    /** Authentication tag length in bytes */
    const val TAG_LENGTH = 16
    
    /** Key length in bytes (AES-256) */
    const val KEY_LENGTH = 32
}

/**
 * Security exception for encryption/decryption failures.
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Security exception for authentication failures (tampered data).
 */
class AuthenticationException(message: String) : SecurityException(message)
