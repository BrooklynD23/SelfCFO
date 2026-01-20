package com.ledgerlens.ui.viewmodels.settings

import com.ledgerlens.security.KeyManager
import com.ledgerlens.security.RecoveryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Theme options for the app.
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * UI state for the main Settings screen.
 */
data class SettingsState(
    val isLoading: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,
    val defaultCurrency: String = "USD",
    val useBiometrics: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val appVersion: String = "1.0.0",
    val buildNumber: String = "1",
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * UI state for the Backup & Restore screen.
 */
data class BackupState(
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportProgress: Float = 0f,
    val importProgress: Float = 0f,
    val lastBackupDate: String? = null,
    val showRecoveryKey: Boolean = false,
    val recoveryKey: RecoveryKey? = null,
    val recoveryKeyCopied: Boolean = false,
    val selectedBackupFile: String? = null,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * UI state for the Security Settings screen.
 */
data class SecurityState(
    val isLoading: Boolean = false,
    val biometricsAvailable: Boolean = true,
    val biometricsEnabled: Boolean = false,
    val showChangePassphrase: Boolean = false,
    val currentPassphrase: String = "",
    val newPassphrase: String = "",
    val confirmPassphrase: String = "",
    val currentPassphraseError: String? = null,
    val newPassphraseError: String? = null,
    val confirmPassphraseError: String? = null,
    val showCryptoEraseConfirm: Boolean = false,
    val cryptoEraseConfirmText: String = "",
    val error: String? = null,
    val successMessage: String? = null
) {
    val canChangePassphrase: Boolean
        get() = currentPassphrase.isNotBlank() &&
                newPassphrase.length >= MIN_PASSPHRASE_LENGTH &&
                confirmPassphrase == newPassphrase &&
                currentPassphraseError == null &&
                newPassphraseError == null &&
                confirmPassphraseError == null

    val canCryptoErase: Boolean
        get() = cryptoEraseConfirmText.equals(CRYPTO_ERASE_CONFIRM_TEXT, ignoreCase = true)

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
        const val CRYPTO_ERASE_CONFIRM_TEXT = "DELETE ALL DATA"
    }
}

/**
 * ViewModel for Settings, Backup/Restore, and Security screens.
 * Manages app preferences, backup operations, and security settings.
 */
class SettingsViewModel(
    private val keyManager: KeyManager
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Settings state
    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    // Backup state
    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    // Security state
    private val _securityState = MutableStateFlow(SecurityState())
    val securityState: StateFlow<SecurityState> = _securityState.asStateFlow()

    init {
        loadSettings()
    }

    // ========== Settings Functions ==========

    private fun loadSettings() {
        viewModelScope.launch {
            _settingsState.update { it.copy(isLoading = true) }
            try {
                // TODO: Load from actual settings repository
                delay(100) // Simulated delay
                _settingsState.update {
                    it.copy(
                        isLoading = false,
                        appVersion = "1.0.0",
                        buildNumber = "1"
                    )
                }
            } catch (e: Exception) {
                _settingsState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            // TODO: Persist to settings repository
            _settingsState.update { it.copy(theme = theme) }
        }
    }

    fun setDefaultCurrency(currency: String) {
        viewModelScope.launch {
            // TODO: Persist to settings repository
            _settingsState.update { it.copy(defaultCurrency = currency) }
        }
    }

    fun toggleBiometrics(enabled: Boolean) {
        viewModelScope.launch {
            // TODO: Actual biometrics setup
            _settingsState.update { it.copy(useBiometrics = enabled) }
        }
    }

    // ========== Backup Functions ==========

    fun loadBackupState() {
        viewModelScope.launch {
            _backupState.update { it.copy(isLoading = true) }
            try {
                // TODO: Load actual backup state
                delay(100)
                _backupState.update {
                    it.copy(
                        isLoading = false,
                        lastBackupDate = null // Would come from actual storage
                    )
                }
            } catch (e: Exception) {
                _backupState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun exportBackup(passphrase: String) {
        viewModelScope.launch {
            _backupState.update { it.copy(isExporting = true, exportProgress = 0f) }
            try {
                // TODO: Actual backup export
                for (i in 1..10) {
                    delay(100)
                    _backupState.update { it.copy(exportProgress = i / 10f) }
                }
                _backupState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 0f,
                        lastBackupDate = "Just now",
                        successMessage = "Backup created successfully"
                    )
                }
            } catch (e: Exception) {
                _backupState.update {
                    it.copy(isExporting = false, exportProgress = 0f, error = e.message)
                }
            }
        }
    }

    fun importBackup(exportPassphrase: String, newPassphrase: String) {
        viewModelScope.launch {
            _backupState.update { it.copy(isImporting = true, importProgress = 0f) }
            try {
                // TODO: Actual backup import
                for (i in 1..10) {
                    delay(100)
                    _backupState.update { it.copy(importProgress = i / 10f) }
                }
                _backupState.update {
                    it.copy(
                        isImporting = false,
                        importProgress = 0f,
                        successMessage = "Backup restored successfully"
                    )
                }
            } catch (e: Exception) {
                _backupState.update {
                    it.copy(isImporting = false, importProgress = 0f, error = e.message)
                }
            }
        }
    }

    fun selectBackupFile(path: String) {
        _backupState.update { it.copy(selectedBackupFile = path) }
    }

    fun showRecoveryKey() {
        viewModelScope.launch {
            // TODO: Get actual recovery key from KeyManager
            val mockRecoveryKey = RecoveryKey(
                listOf(
                    "abandon", "ability", "able", "about", "above", "absent",
                    "absorb", "abstract", "absurd", "abuse", "access", "accident",
                    "account", "accuse", "achieve", "acid", "acoustic", "acquire",
                    "across", "act", "action", "actor", "actress", "actual"
                )
            )
            _backupState.update {
                it.copy(showRecoveryKey = true, recoveryKey = mockRecoveryKey)
            }
        }
    }

    fun hideRecoveryKey() {
        _backupState.update {
            it.copy(showRecoveryKey = false, recoveryKey = null, recoveryKeyCopied = false)
        }
    }

    fun copyRecoveryKey() {
        // TODO: Copy to clipboard using platform API
        _backupState.update { it.copy(recoveryKeyCopied = true) }
    }

    // ========== Security Functions ==========

    fun loadSecuritySettings() {
        viewModelScope.launch {
            _securityState.update { it.copy(isLoading = true) }
            try {
                // TODO: Load actual security settings
                delay(100)
                _securityState.update {
                    it.copy(
                        isLoading = false,
                        biometricsAvailable = true, // Would check platform capability
                        biometricsEnabled = false
                    )
                }
            } catch (e: Exception) {
                _securityState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun toggleSecurityBiometrics(enabled: Boolean) {
        viewModelScope.launch {
            // TODO: Actual biometrics enrollment
            _securityState.update { it.copy(biometricsEnabled = enabled) }
            _settingsState.update { it.copy(useBiometrics = enabled) }
        }
    }

    fun showChangePassphrase() {
        _securityState.update {
            it.copy(
                showChangePassphrase = true,
                currentPassphrase = "",
                newPassphrase = "",
                confirmPassphrase = "",
                currentPassphraseError = null,
                newPassphraseError = null,
                confirmPassphraseError = null
            )
        }
    }

    fun hideChangePassphrase() {
        _securityState.update {
            it.copy(
                showChangePassphrase = false,
                currentPassphrase = "",
                newPassphrase = "",
                confirmPassphrase = "",
                currentPassphraseError = null,
                newPassphraseError = null,
                confirmPassphraseError = null
            )
        }
    }

    fun setCurrentPassphrase(passphrase: String) {
        _securityState.update {
            it.copy(currentPassphrase = passphrase, currentPassphraseError = null)
        }
    }

    fun setNewPassphrase(passphrase: String) {
        val error = if (passphrase.isNotEmpty() && passphrase.length < SecurityState.MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least ${SecurityState.MIN_PASSPHRASE_LENGTH} characters"
        } else {
            null
        }

        _securityState.update { state ->
            val confirmError = if (state.confirmPassphrase.isNotEmpty() && state.confirmPassphrase != passphrase) {
                "Passphrases don't match"
            } else {
                null
            }
            state.copy(
                newPassphrase = passphrase,
                newPassphraseError = error,
                confirmPassphraseError = confirmError
            )
        }
    }

    fun setConfirmPassphrase(passphrase: String) {
        _securityState.update { state ->
            val error = if (passphrase.isNotEmpty() && passphrase != state.newPassphrase) {
                "Passphrases don't match"
            } else {
                null
            }
            state.copy(confirmPassphrase = passphrase, confirmPassphraseError = error)
        }
    }

    fun changePassphrase() {
        viewModelScope.launch {
            _securityState.update { it.copy(isLoading = true) }

            val state = _securityState.value
            val result = keyManager.changePassphrase(
                currentPassphrase = state.currentPassphrase,
                newPassphrase = state.newPassphrase
            )

            result.fold(
                onSuccess = {
                    _securityState.update {
                        it.copy(
                            isLoading = false,
                            showChangePassphrase = false,
                            currentPassphrase = "",
                            newPassphrase = "",
                            confirmPassphrase = "",
                            successMessage = "Passphrase changed successfully"
                        )
                    }
                },
                onFailure = { e ->
                    _securityState.update {
                        it.copy(
                            isLoading = false,
                            currentPassphraseError = "Failed to change passphrase: ${e.message}"
                        )
                    }
                }
            )
        }
    }

    fun showCryptoEraseConfirm() {
        _securityState.update {
            it.copy(showCryptoEraseConfirm = true, cryptoEraseConfirmText = "")
        }
    }

    fun hideCryptoEraseConfirm() {
        _securityState.update {
            it.copy(showCryptoEraseConfirm = false, cryptoEraseConfirmText = "")
        }
    }

    fun setCryptoEraseConfirmText(text: String) {
        _securityState.update { it.copy(cryptoEraseConfirmText = text) }
    }

    fun executeCryptoErase() {
        viewModelScope.launch {
            _securityState.update { it.copy(isLoading = true) }

            val result = keyManager.cryptoErase()

            result.fold(
                onSuccess = {
                    _securityState.update {
                        it.copy(
                            isLoading = false,
                            showCryptoEraseConfirm = false,
                            successMessage = "All data has been erased"
                        )
                    }
                    // App should navigate to setup screen after this
                },
                onFailure = { e ->
                    _securityState.update {
                        it.copy(isLoading = false, error = "Failed to erase data: ${e.message}")
                    }
                }
            )
        }
    }

    // ========== Error/Success Clearing ==========

    fun clearError() {
        _settingsState.update { it.copy(error = null) }
        _backupState.update { it.copy(error = null) }
        _securityState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _settingsState.update { it.copy(successMessage = null) }
        _backupState.update { it.copy(successMessage = null) }
        _securityState.update { it.copy(successMessage = null) }
    }
}
