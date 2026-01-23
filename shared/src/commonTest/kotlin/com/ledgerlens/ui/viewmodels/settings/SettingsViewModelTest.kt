package com.ledgerlens.ui.viewmodels.settings

import com.ledgerlens.data.repositories.fake.FakeKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var keyManager: FakeKeyManager
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        keyManager = FakeKeyManager()
        viewModel = SettingsViewModel(keyManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    // ========== Settings State Tests ==========
    
    @Test
    fun `initial settings state has defaults`() {
        val state = viewModel.settingsState.value
        assertEquals("USD", state.defaultCurrency)
        assertEquals(AppTheme.SYSTEM, state.theme)
        assertFalse(state.useBiometrics)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
    
    @Test
    fun `setDefaultCurrency updates currency`() = runTest {
        viewModel.setDefaultCurrency("EUR")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("EUR", viewModel.settingsState.value.defaultCurrency)
    }
    
    @Test
    fun `setTheme updates theme`() = runTest {
        viewModel.setTheme(AppTheme.DARK)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppTheme.DARK, viewModel.settingsState.value.theme)
    }
    
    // ========== Backup State Tests ==========
    
    @Test
    fun `initial backup state is not exporting or importing`() {
        val state = viewModel.backupState.value
        assertFalse(state.isExporting)
        assertFalse(state.isImporting)
        assertFalse(state.showRecoveryKey)
    }
    
    @Test
    fun `showRecoveryKey sets showRecoveryKey to true`() = runTest {
        viewModel.showRecoveryKey()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.backupState.value.showRecoveryKey)
    }
    
    @Test
    fun `hideRecoveryKey clears recovery key state`() {
        viewModel.hideRecoveryKey()
        val state = viewModel.backupState.value
        assertFalse(state.showRecoveryKey)
        assertNull(state.recoveryKey)
        assertFalse(state.recoveryKeyCopied)
    }
    
    @Test
    fun `copyRecoveryKey sets copied flag`() {
        viewModel.copyRecoveryKey()
        assertTrue(viewModel.backupState.value.recoveryKeyCopied)
    }
    
    @Test
    fun `selectBackupFile updates selected file`() {
        viewModel.selectBackupFile("/path/to/backup.zip")
        assertEquals("/path/to/backup.zip", viewModel.backupState.value.selectedBackupFile)
    }
    
    // ========== Security State Tests ==========
    
    @Test
    fun `initial security state has empty passphrase fields`() {
        val state = viewModel.securityState.value
        assertEquals("", state.currentPassphrase)
        assertEquals("", state.newPassphrase)
        assertEquals("", state.confirmPassphrase)
        assertFalse(state.showChangePassphrase)
        assertFalse(state.showCryptoEraseConfirm)
    }
    
    @Test
    fun `showChangePassphrase opens dialog with cleared fields`() {
        viewModel.showChangePassphrase()
        val state = viewModel.securityState.value
        assertTrue(state.showChangePassphrase)
        assertEquals("", state.currentPassphrase)
        assertEquals("", state.newPassphrase)
        assertEquals("", state.confirmPassphrase)
    }
    
    @Test
    fun `hideChangePassphrase closes dialog and clears fields`() {
        viewModel.showChangePassphrase()
        viewModel.setCurrentPassphrase("old")
        viewModel.setNewPassphrase("newpassword123")
        viewModel.hideChangePassphrase()
        
        val state = viewModel.securityState.value
        assertFalse(state.showChangePassphrase)
        assertEquals("", state.currentPassphrase)
        assertEquals("", state.newPassphrase)
    }
    
    @Test
    fun `setCurrentPassphrase updates current passphrase`() {
        viewModel.showChangePassphrase()
        viewModel.setCurrentPassphrase("mypassword")
        assertEquals("mypassword", viewModel.securityState.value.currentPassphrase)
    }
    
    @Test
    fun `setNewPassphrase validates and updates new passphrase`() {
        viewModel.showChangePassphrase()
        viewModel.setNewPassphrase("short")
        
        val state = viewModel.securityState.value
        assertEquals("short", state.newPassphrase)
        // Should have validation error for short passphrase
        assertNotNull(state.newPassphraseError)
    }
    
    @Test
    fun `setNewPassphrase with valid passphrase has no error`() {
        viewModel.showChangePassphrase()
        viewModel.setNewPassphrase("ValidPassword123!")
        
        val state = viewModel.securityState.value
        assertEquals("ValidPassword123!", state.newPassphrase)
        assertNull(state.newPassphraseError)
    }
    
    @Test
    fun `setConfirmPassphrase shows error when mismatch`() {
        viewModel.showChangePassphrase()
        viewModel.setNewPassphrase("ValidPassword123!")
        viewModel.setConfirmPassphrase("DifferentPassword")
        
        val state = viewModel.securityState.value
        assertNotNull(state.confirmPassphraseError)
        assertEquals("Passphrases don't match", state.confirmPassphraseError)
    }
    
    @Test
    fun `setConfirmPassphrase has no error when matching`() {
        viewModel.showChangePassphrase()
        viewModel.setNewPassphrase("ValidPassword123!")
        viewModel.setConfirmPassphrase("ValidPassword123!")
        
        val state = viewModel.securityState.value
        assertNull(state.confirmPassphraseError)
    }
    
    @Test
    fun `canChangePassphrase is true when all fields valid`() {
        viewModel.showChangePassphrase()
        viewModel.setCurrentPassphrase("currentPass")
        viewModel.setNewPassphrase("ValidPassword123!")
        viewModel.setConfirmPassphrase("ValidPassword123!")
        
        assertTrue(viewModel.securityState.value.canChangePassphrase)
    }
    
    @Test
    fun `canChangePassphrase is false when fields empty`() {
        viewModel.showChangePassphrase()
        assertFalse(viewModel.securityState.value.canChangePassphrase)
    }
    
    // ========== Crypto Erase Tests ==========
    
    @Test
    fun `showCryptoEraseConfirm opens confirmation dialog`() {
        viewModel.showCryptoEraseConfirm()
        val state = viewModel.securityState.value
        assertTrue(state.showCryptoEraseConfirm)
        assertEquals("", state.cryptoEraseConfirmText)
    }
    
    @Test
    fun `hideCryptoEraseConfirm closes dialog`() {
        viewModel.showCryptoEraseConfirm()
        viewModel.hideCryptoEraseConfirm()
        assertFalse(viewModel.securityState.value.showCryptoEraseConfirm)
    }
    
    @Test
    fun `setCryptoEraseConfirmText updates text`() {
        viewModel.showCryptoEraseConfirm()
        viewModel.setCryptoEraseConfirmText("DELETE ALL DATA")
        assertEquals("DELETE ALL DATA", viewModel.securityState.value.cryptoEraseConfirmText)
    }
    
    @Test
    fun `canCryptoErase is true when correct confirmation text`() {
        viewModel.showCryptoEraseConfirm()
        viewModel.setCryptoEraseConfirmText("DELETE ALL DATA")
        assertTrue(viewModel.securityState.value.canCryptoErase)
    }
    
    @Test
    fun `canCryptoErase is true case insensitive`() {
        viewModel.showCryptoEraseConfirm()
        viewModel.setCryptoEraseConfirmText("delete all data")
        assertTrue(viewModel.securityState.value.canCryptoErase)
    }
    
    @Test
    fun `canCryptoErase is false with wrong text`() {
        viewModel.showCryptoEraseConfirm()
        viewModel.setCryptoEraseConfirmText("wrong text")
        assertFalse(viewModel.securityState.value.canCryptoErase)
    }
    
    // ========== Error Handling Tests ==========
    
    @Test
    fun `clearError clears all errors`() {
        viewModel.clearError()
        assertNull(viewModel.settingsState.value.error)
        assertNull(viewModel.backupState.value.error)
        assertNull(viewModel.securityState.value.error)
    }
    
    @Test
    fun `clearSuccessMessage clears all success messages`() {
        viewModel.clearSuccessMessage()
        assertNull(viewModel.settingsState.value.successMessage)
        assertNull(viewModel.backupState.value.successMessage)
        assertNull(viewModel.securityState.value.successMessage)
    }
}
