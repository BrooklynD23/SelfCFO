package com.ledgerlens.security

/**
 * BIP39 mnemonic generator for recovery keys.
 *
 * Generates 24-word recovery phrases from 256 bits of entropy.
 * Uses the standard BIP39 English word list.
 */
object MnemonicGenerator {

    private const val WORDS_IN_MNEMONIC = 24
    private const val ENTROPY_BITS = 256
    private const val ENTROPY_BYTES = ENTROPY_BITS / 8
    private const val CHECKSUM_BITS = ENTROPY_BITS / 32
    private const val BITS_PER_WORD = 11

    /**
     * Generate a new recovery key with 256 bits of entropy.
     * @return RecoveryKey with 24 words
     */
    fun generate(): RecoveryKey {
        val entropy = SecureRandom.nextBytes(ENTROPY_BYTES)
        return fromEntropy(entropy)
    }

    /**
     * Convert entropy bytes to a mnemonic recovery key.
     * @param entropy 32 bytes (256 bits) of random data
     * @return RecoveryKey with 24 words
     */
    fun fromEntropy(entropy: ByteArray): RecoveryKey {
        require(entropy.size == ENTROPY_BYTES) {
            "Entropy must be $ENTROPY_BYTES bytes for 24-word mnemonic"
        }

        val checksum = Sha256.digest(entropy)
        val entropyBits = bytesToBits(entropy)
        val checksumBits = bytesToBits(checksum).take(CHECKSUM_BITS)
        val allBits = entropyBits + checksumBits

        val words = (0 until WORDS_IN_MNEMONIC).map { i ->
            val startBit = i * BITS_PER_WORD
            val index = bitsToInt(allBits.subList(startBit, startBit + BITS_PER_WORD))
            Bip39WordList.getWord(index)
        }

        return RecoveryKey(words)
    }

    /**
     * Convert a mnemonic back to entropy bytes.
     * @param mnemonic RecoveryKey to convert
     * @return 32 bytes of entropy, or null if invalid
     */
    fun toEntropy(mnemonic: RecoveryKey): ByteArray? {
        if (mnemonic.words.size != WORDS_IN_MNEMONIC) return null

        val indices = mnemonic.words.map { word ->
            Bip39WordList.indexOf(word.lowercase()).takeIf { it >= 0 } ?: return null
        }

        val allBits = indices.flatMap { intToBits(it, BITS_PER_WORD) }
        val entropyBits = allBits.take(ENTROPY_BITS)
        val checksumBits = allBits.drop(ENTROPY_BITS)

        val entropy = bitsToBytes(entropyBits)
        val expectedChecksum = bytesToBits(Sha256.digest(entropy)).take(CHECKSUM_BITS)

        return if (checksumBits == expectedChecksum) entropy else null
    }

    /**
     * Validate a mnemonic phrase.
     * @return true if valid BIP39 mnemonic with correct checksum
     */
    fun isValid(mnemonic: RecoveryKey): Boolean {
        return toEntropy(mnemonic) != null
    }

    /**
     * Derive a master key from a mnemonic using key derivation.
     */
    fun deriveKeyFromMnemonic(mnemonic: RecoveryKey, salt: ByteArray): ByteArray {
        val mnemonicString = mnemonic.words.joinToString(" ")
        return KeyDerivation.deriveKey(mnemonicString, salt, Argon2Params.KEY_LENGTH)
    }

    private fun bytesToBits(bytes: ByteArray): List<Boolean> {
        return bytes.flatMap { byte ->
            (7 downTo 0).map { bit -> (byte.toInt() shr bit) and 1 == 1 }
        }
    }

    private fun bitsToBytes(bits: List<Boolean>): ByteArray {
        return bits.chunked(8).map { chunk ->
            chunk.foldIndexed(0) { index, acc, bit ->
                if (bit) acc or (1 shl (7 - index)) else acc
            }.toByte()
        }.toByteArray()
    }

    private fun bitsToInt(bits: List<Boolean>): Int {
        return bits.fold(0) { acc, bit -> (acc shl 1) or (if (bit) 1 else 0) }
    }

    private fun intToBits(value: Int, numBits: Int): List<Boolean> {
        return (numBits - 1 downTo 0).map { bit -> (value shr bit) and 1 == 1 }
    }
}
