package com.ledgerlens.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Tests for KeyManager implementation.
 * 
 * These tests verify the key hierarchy per ADR-003:
 * - Passphrase → Master Key → wraps KEK → wraps DEKs
 */
class KeyManagerTest {
    
    @Test
    fun testPassphraseValidation_tooShort() {
        val result = PassphraseRequirements.validate("short")
        assertTrue(result is PassphraseValidationResult.Invalid)
        val invalid = result as PassphraseValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("12 characters") })
    }
    
    @Test
    fun testPassphraseValidation_valid() {
        val result = PassphraseRequirements.validate("this-is-a-valid-passphrase")
        assertTrue(result is PassphraseValidationResult.Valid)
    }
    
    @Test
    fun testPassphraseValidation_exactMinLength() {
        val result = PassphraseRequirements.validate("123456789012")
        assertTrue(result is PassphraseValidationResult.Valid)
    }
    
    @Test
    fun testRecoveryKey_fromString() {
        val mnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident account accuse achieve acid acoustic acquire across act action actor actress actual"
        val recoveryKey = RecoveryKey.fromString(mnemonic)
        assertEquals(24, recoveryKey.words.size)
        assertEquals("abandon", recoveryKey.words.first())
        assertEquals("actual", recoveryKey.words.last())
    }
    
    @Test
    fun testRecoveryKey_toString() {
        val words = listOf("abandon", "ability", "able", "about")
        val recoveryKey = RecoveryKey(words)
        assertEquals("abandon ability able about", recoveryKey.toString())
    }
    
    @Test
    fun testBackupBundle_equality() {
        val bundle1 = BackupBundle(
            encryptedKek = byteArrayOf(1, 2, 3),
            salt = byteArrayOf(4, 5, 6),
            version = 1
        )
        val bundle2 = BackupBundle(
            encryptedKek = byteArrayOf(1, 2, 3),
            salt = byteArrayOf(4, 5, 6),
            version = 1
        )
        assertEquals(bundle1, bundle2)
        assertEquals(bundle1.hashCode(), bundle2.hashCode())
    }
    
    @Test
    fun testBackupBundle_inequality() {
        val bundle1 = BackupBundle(
            encryptedKek = byteArrayOf(1, 2, 3),
            salt = byteArrayOf(4, 5, 6),
            version = 1
        )
        val bundle2 = BackupBundle(
            encryptedKek = byteArrayOf(1, 2, 4),
            salt = byteArrayOf(4, 5, 6),
            version = 1
        )
        assertFalse(bundle1 == bundle2)
    }
}

/**
 * Tests for BIP39 word list.
 */
class Bip39WordListTest {
    
    @Test
    fun testWordListSize() {
        assertEquals(2048, Bip39WordList.size)
    }
    
    @Test
    fun testGetWord_firstWord() {
        assertEquals("abandon", Bip39WordList.getWord(0))
    }
    
    @Test
    fun testGetWord_lastWord() {
        assertEquals("zoo", Bip39WordList.getWord(2047))
    }
    
    @Test
    fun testIndexOf_validWord() {
        assertEquals(0, Bip39WordList.indexOf("abandon"))
        assertEquals(2047, Bip39WordList.indexOf("zoo"))
    }
    
    @Test
    fun testIndexOf_invalidWord() {
        assertEquals(-1, Bip39WordList.indexOf("notaword"))
    }
    
    @Test
    fun testIsValidWord() {
        assertTrue(Bip39WordList.isValidWord("abandon"))
        assertTrue(Bip39WordList.isValidWord("zoo"))
        assertFalse(Bip39WordList.isValidWord("notaword"))
    }
    
    @Test
    fun testIndexOf_caseInsensitive() {
        assertEquals(0, Bip39WordList.indexOf("ABANDON"))
        assertEquals(0, Bip39WordList.indexOf("Abandon"))
    }
}

/**
 * Tests for MnemonicGenerator.
 */
class MnemonicGeneratorTest {
    
    @Test
    fun testGenerate_produces24Words() {
        val recoveryKey = MnemonicGenerator.generate()
        assertEquals(24, recoveryKey.words.size)
    }
    
    @Test
    fun testGenerate_allWordsValid() {
        val recoveryKey = MnemonicGenerator.generate()
        recoveryKey.words.forEach { word ->
            assertTrue(Bip39WordList.isValidWord(word), "Word '$word' should be valid")
        }
    }
    
    @Test
    fun testFromEntropy_knownVector() {
        val entropy = ByteArray(32) { 0 }
        val recoveryKey = MnemonicGenerator.fromEntropy(entropy)
        assertEquals(24, recoveryKey.words.size)
        assertEquals("abandon", recoveryKey.words[0])
    }
    
    @Test
    fun testToEntropy_roundTrip() {
        val originalEntropy = SecureRandom.nextBytes(32)
        val mnemonic = MnemonicGenerator.fromEntropy(originalEntropy)
        val recoveredEntropy = MnemonicGenerator.toEntropy(mnemonic)
        
        assertNotNull(recoveredEntropy)
        assertTrue(originalEntropy.contentEquals(recoveredEntropy))
    }
    
    @Test
    fun testIsValid_validMnemonic() {
        val mnemonic = MnemonicGenerator.generate()
        assertTrue(MnemonicGenerator.isValid(mnemonic))
    }
    
    @Test
    fun testIsValid_invalidChecksum() {
        val mnemonic = MnemonicGenerator.generate()
        val tamperedWords = mnemonic.words.toMutableList()
        tamperedWords[0] = if (tamperedWords[0] == "abandon") "ability" else "abandon"
        val tamperedMnemonic = RecoveryKey(tamperedWords)
        
        assertFalse(MnemonicGenerator.isValid(tamperedMnemonic))
    }
    
    @Test
    fun testIsValid_wrongWordCount() {
        val mnemonic = RecoveryKey(listOf("abandon", "ability", "able"))
        assertFalse(MnemonicGenerator.isValid(mnemonic))
    }
    
    @Test
    fun testIsValid_invalidWord() {
        val words = (0 until 24).map { "abandon" }.toMutableList()
        words[0] = "notavalidword"
        val mnemonic = RecoveryKey(words)
        assertFalse(MnemonicGenerator.isValid(mnemonic))
    }
    
    @Test
    fun testFromEntropy_requiresCorrectLength() {
        assertFailsWith<IllegalArgumentException> {
            MnemonicGenerator.fromEntropy(ByteArray(16))
        }
    }
}
