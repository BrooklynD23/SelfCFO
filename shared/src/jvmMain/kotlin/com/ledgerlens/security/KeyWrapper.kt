package com.ledgerlens.security

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES key wrapping utilities for JVM platforms.
 * 
 * Uses AES-GCM for authenticated encryption of keys.
 * This provides both confidentiality and integrity protection.
 */
object KeyWrapper {
    
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    
    /**
     * Wrap (encrypt) a key using another key.
     * 
     * @param keyToWrap The key material to protect
     * @param wrappingKey The key used to encrypt (must be 32 bytes for AES-256)
     * @return IV (12 bytes) + encrypted key + auth tag
     */
    fun wrap(keyToWrap: ByteArray, wrappingKey: ByteArray): ByteArray {
        require(wrappingKey.size == AesGcmConstants.KEY_LENGTH) {
            "Wrapping key must be ${AesGcmConstants.KEY_LENGTH} bytes"
        }
        
        val iv = generateIv()
        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(wrappingKey, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val wrapped = cipher.doFinal(keyToWrap)
        
        return iv + wrapped
    }
    
    /**
     * Unwrap (decrypt) a key using another key.
     * 
     * @param wrappedKey IV + encrypted key + auth tag
     * @param wrappingKey The key used to decrypt (must be 32 bytes for AES-256)
     * @return The unwrapped key material
     * @throws AuthenticationException if the wrapped key is tampered or wrong wrapping key
     */
    fun unwrap(wrappedKey: ByteArray, wrappingKey: ByteArray): ByteArray {
        require(wrappingKey.size == AesGcmConstants.KEY_LENGTH) {
            "Wrapping key must be ${AesGcmConstants.KEY_LENGTH} bytes"
        }
        require(wrappedKey.size >= AesGcmConstants.IV_LENGTH + AesGcmConstants.TAG_LENGTH) {
            "Wrapped key data is too short"
        }
        
        val iv = wrappedKey.sliceArray(0 until AesGcmConstants.IV_LENGTH)
        val ciphertext = wrappedKey.sliceArray(AesGcmConstants.IV_LENGTH until wrappedKey.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(wrappingKey, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw AuthenticationException("Key unwrapping failed - invalid wrapping key or tampered data")
        }
    }
    
    /**
     * Derive a wrapping key from a passphrase and salt.
     * Uses the platform's KeyDerivation (Argon2id or PBKDF2).
     */
    fun deriveWrappingKey(passphrase: String, salt: ByteArray): ByteArray {
        return KeyDerivation.deriveKey(passphrase, salt, AesGcmConstants.KEY_LENGTH)
    }
}
