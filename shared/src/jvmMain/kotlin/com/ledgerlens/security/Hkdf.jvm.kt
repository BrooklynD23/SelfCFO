package com.ledgerlens.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * JVM implementation of HKDF using HMAC-SHA256.
 * Used by both Android and Desktop platforms.
 */
actual object Hkdf {
    actual fun derive(keyMaterial: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        require(outputLength > 0) { "Output length must be positive" }
        require(outputLength <= 255 * 32) { "Output length too large for HKDF" }

        val hmac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(keyMaterial, "HmacSHA256")

        // HKDF-Extract (simplified - using keyMaterial directly as PRK)
        val prk = keyMaterial

        // HKDF-Expand
        val hashLen = 32 // SHA-256 output length
        val n = (outputLength + hashLen - 1) / hashLen // Number of hash iterations needed

        val result = ByteArray(outputLength)
        var offset = 0

        for (i in 1..n) {
            hmac.init(SecretKeySpec(prk, "HmacSHA256"))
            if (i > 1) {
                // Include previous hash output
                val prevStart = maxOf(0, offset - hashLen)
                hmac.update(result, prevStart, offset - prevStart)
            }
            hmac.update(info)
            hmac.update(i.toByte())
            val hash = hmac.doFinal()

            val toCopy = minOf(hash.size, outputLength - offset)
            hash.copyInto(result, offset, 0, toCopy)
            offset += toCopy
        }

        return result
    }
}
