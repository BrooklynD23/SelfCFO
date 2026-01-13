package com.ledgerlens.security

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM implementation of AES-GCM file encryption.
 * Used by both Android and Desktop platforms.
 */
class AesGcmFileEncryption : FileEncryption {
    
    override fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(key.size == AesGcmConstants.KEY_LENGTH) {
            "Key must be ${AesGcmConstants.KEY_LENGTH} bytes for AES-256"
        }
        
        val iv = generateIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(AesGcmConstants.TAG_LENGTH * 8, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(data)
        
        // Return IV + ciphertext (tag is appended by GCM)
        return iv + ciphertext
    }

    override fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray {
        require(key.size == AesGcmConstants.KEY_LENGTH) {
            "Key must be ${AesGcmConstants.KEY_LENGTH} bytes for AES-256"
        }
        require(encryptedData.size > AesGcmConstants.IV_LENGTH + AesGcmConstants.TAG_LENGTH) {
            "Encrypted data is too short"
        }
        
        val iv = encryptedData.sliceArray(0 until AesGcmConstants.IV_LENGTH)
        val ciphertext = encryptedData.sliceArray(AesGcmConstants.IV_LENGTH until encryptedData.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(AesGcmConstants.TAG_LENGTH * 8, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw AuthenticationException("Data authentication failed - data may be tampered")
        }
    }
}
