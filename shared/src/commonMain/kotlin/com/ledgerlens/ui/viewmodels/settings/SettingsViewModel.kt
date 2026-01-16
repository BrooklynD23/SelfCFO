package com.ledgerlens.ui.viewmodels.settings

import com.ledgerlens.security.BackupBundle
import com.ledgerlens.security.PassphraseRequirements
import com.ledgerlens.security.PassphraseValidationResult
import com.ledgerlens.security.RecoveryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for settings screen.
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    
    // User preferences
    val defaultCurrency: String = "USD",
    val theme: AppTheme = AppTheme.SYSTEM,
    val useBiometrics: Boolean = false,
    val biometricsAvailable: Boolean = false,
    
    // App info
    val appVersion: String = "1.0.0",
    val buildNumber: String = "1",
    
    // Data stats
    val transactionCount: Int = 0,
    val accountCount: Int = 0,
    val receiptCount: Int = 0,
    val databaseSizeBytes: Long = 0
)

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

/**
 * UI state for backup/restore operations.
 */
data class BackupRestoreUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    
    // Export
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val lastBackupDate: String? = null,
    
    // Import
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val selectedBackupFile: String? = null,
    
    // Recovery key
    val showRecoveryKey: Boolean = false,
    val recoveryKey: RecoveryKey? = null,
    val recoveryKeyCopied: Boolean = false
)

/**
 * UI state for security settings.
 */
data class SecuritySettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    
    // Change passphrase
    val showChangePassphrase: Boolean = false,
    val currentPassphrase: String = "",
    val newPassphrase: String = "",
    val confirmPassphrase: String = "",
    val currentPassphraseError: String? = null,
    val newPassphraseError: String? = null,
    val confirmPassphraseError: String? = null,
    
    // Biometrics
    val biometricsEnabled: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val showBiometricsPrompt: Boolean = false,
    
    // Crypto erase
    val showCryptoEraseConfirm: Boolean = false,
    val cryptoEraseConfirmText: String = ""
) {
    val canChangePassphrase: Boolean
        get() = currentPassphrase.isNotBlank() &&
                newPassphrase.isNotBlank() &&
                confirmPassphrase.isNotBlank() &&
                newPassphrase == confirmPassphrase &&
                currentPassphraseError == null &&
                newPassphraseError == null &&
                confirmPassphraseError == null
    
    val canCryptoErase: Boolean
        get() = cryptoEraseConfirmText.equals("DELETE ALL DATA", ignoreCase = true)
}

/**
 * ViewModel for settings screens.
 */
class SettingsViewModel(
    // TODO: Replace with actual dependency injection
    // private val keyManager: KeyManager,
    // private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()
    
    private val _backupState = MutableStateFlow(BackupRestoreUiState())
    val backupState: StateFlow<BackupRestoreUiState> = _backupState.asStateFlow()
    
    private val _securityState = MutableStateFlow(SecuritySettingsUiState())
    val securityState: StateFlow<SecuritySettingsUiState> = _securityState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    // ========== Settings Actions ==========
    
    fun loadSettings() {
        scope.launch {
            _settingsState.update { it.copy(isLoading = true, error = null) }
            try {
                // TODO: Load from repositories
                // val prefs = preferencesRepository.getAll()
                // val stats = statsRepository.getDataStats()
                _settingsState.update { it.copy(
                    isLoading = false,
                    // TODO: Map from preferences
                    biometricsAvailable = false // TODO: Check platform capability
                )}
            } catch (e: Exception) {
                _settingsState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load settings: ${e.message}"
                )}
            }
        }
    }
    
    fun setDefaultCurrency(currency: String) {
        scope.launch {
            try {
                // TODO: Save to preferences
                _settingsState.update { it.copy(defaultCurrency = currency) }
            } catch (e: Exception) {
                _settingsState.update { it.copy(error = "Failed to update currency: ${e.message}") }
            }
        }
    }
    
    fun setTheme(theme: AppTheme) {
        scope.launch {
            try {
                // TODO: Save to preferences
                _settingsState.update { it.copy(theme = theme) }
            } catch (e: Exception) {
                _settingsState.update { it.copy(error = "Failed to update theme: ${e.message}") }
            }
        }
    }
    
    fun toggleBiometrics(enabled: Boolean) {
        if (!_settingsState.value.biometricsAvailable) return
        
        scope.launch {
            try {
                // TODO: Enable/disable biometrics
                _settingsState.update { it.copy(useBiometrics = enabled) }
            } catch (e: Exception) {
                _settingsState.update { it.copy(error = "Failed to update biometrics: ${e.message}") }
            }
        }
    }
    
    // ========== Backup/Restore Actions ==========
    
    fun loadBackupState() {
        scope.launch {
            _backupState.update { it.copy(isLoading = true) }
            try {
                // TODO: Load last backup date from preferences
                _backupState.update { it.copy(
                    isLoading = false,
                    lastBackupDate = null // TODO: Load from preferences
                )}
            } catch (e: Exception) {
                _backupState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load backup info: ${e.message}"
                )}
            }
        }
    }
    
    fun exportBackup(exportPassphrase: String) {
        scope.launch {
            _backupState.update { it.copy(isExporting = true, exportProgress = 0f, error = null) }
            
            try {
                // Validate passphrase
                val validation = PassphraseRequirements.validate(exportPassphrase)
                if (validation is PassphraseValidationResult.Invalid) {
                    _backupState.update { it.copy(
                        isExporting = false,
                        error = validation.errors.first()
                    )}
                    return@launch
                }
                
                // TODO: Export backup
                // val backup = keyManager.exportForBackup(exportPassphrase)
                // Save to file...
                
                _backupState.update { it.copy(exportProgress = 0.5f) }
                
                // Simulate progress
                _backupState.update { it.copy(exportProgress = 1f) }
                
                _backupState.update { it.copy(
                    isExporting = false,
                    exportProgress = 0f,
                    successMessage = "Backup exported successfully",
                    lastBackupDate = "Just now" // TODO: Format actual date
                )}
            } catch (e: Exception) {
                _backupState.update { it.copy(
                    isExporting = false,
                    exportProgress = 0f,
                    error = "Failed to export backup: ${e.message}"
                )}
            }
        }
    }
    
    fun selectBackupFile(filePath: String) {
        _backupState.update { it.copy(selectedBackupFile = filePath) }
    }
    
    fun importBackup(exportPassphrase: String, newPassphrase: String) {
        val selectedFile = _backupState.value.selectedBackupFile
        if (selectedFile == null) {
            _backupState.update { it.copy(error = "No backup file selected") }
            return
        }
        
        scope.launch {
            _backupState.update { it.copy(isImporting = true, importProgress = 0f, error = null) }
            
            try {
                // Validate new passphrase
                val validation = PassphraseRequirements.validate(newPassphrase)
                if (validation is PassphraseValidationResult.Invalid) {
                    _backupState.update { it.copy(
                        isImporting = false,
                        error = validation.errors.first()
                    )}
                    return@launch
                }
                
                _backupState.update { it.copy(importProgress = 0.3f) }
                
                // TODO: Read backup file and import
                // val backupData = readBackupFile(selectedFile)
                // keyManager.importFromBackup(backupData, exportPassphrase, newPassphrase)
                
                _backupState.update { it.copy(importProgress = 0.7f) }
                
                _backupState.update { it.copy(importProgress = 1f) }
                
                _backupState.update { it.copy(
                    isImporting = false,
                    importProgress = 0f,
                    selectedBackupFile = null,
                    successMessage = "Backup imported successfully"
                )}
            } catch (e: Exception) {
                _backupState.update { it.copy(
                    isImporting = false,
                    importProgress = 0f,
                    error = "Failed to import backup: ${e.message}"
                )}
            }
        }
    }
    
    fun showRecoveryKey() {
        scope.launch {
            _backupState.update { it.copy(isLoading = true) }
            try {
                // TODO: Get recovery key from keyManager
                // This would typically require unlocking first
                _backupState.update { it.copy(
                    isLoading = false,
                    showRecoveryKey = true,
                    recoveryKey = null // TODO: Get actual recovery key
                )}
            } catch (e: Exception) {
                _backupState.update { it.copy(
                    isLoading = false,
                    error = "Failed to retrieve recovery key: ${e.message}"
                )}
            }
        }
    }
    
    fun hideRecoveryKey() {
        _backupState.update { it.copy(
            showRecoveryKey = false,
            recoveryKey = null,
            recoveryKeyCopied = false
        )}
    }
    
    fun copyRecoveryKey() {
        // TODO: Copy to clipboard
        _backupState.update { it.copy(recoveryKeyCopied = true) }
    }
    
    // ========== Security Settings Actions ==========
    
    fun loadSecuritySettings() {
        scope.launch {
            _securityState.update { it.copy(isLoading = true) }
            try {
                // TODO: Load security settings
                _securityState.update { it.copy(
                    isLoading = false,
                    biometricsAvailable = false, // TODO: Check platform
                    biometricsEnabled = false // TODO: Load preference
                )}
            } catch (e: Exception) {
                _securityState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load security settings: ${e.message}"
                )}
            }
        }
    }
    
    fun showChangePassphrase() {
        _securityState.update { it.copy(
            showChangePassphrase = true,
            currentPassphrase = "",
            newPassphrase = "",
            confirmPassphrase = "",
            currentPassphraseError = null,
            newPassphraseError = null,
            confirmPassphraseError = null
        )}
    }
    
    fun hideChangePassphrase() {
        _securityState.update { it.copy(
            showChangePassphrase = false,
            currentPassphrase = "",
            newPassphrase = "",
            confirmPassphrase = ""
        )}
    }
    
    fun setCurrentPassphrase(passphrase: String) {
        _securityState.update { it.copy(
            currentPassphrase = passphrase,
            currentPassphraseError = null
        )}
    }
    
    fun setNewPassphrase(passphrase: String) {
        val validation = PassphraseRequirements.validate(passphrase)
        val error = when (validation) {
            is PassphraseValidationResult.Invalid -> validation.errors.firstOrNull()
            is PassphraseValidationResult.Valid -> null
        }
        
        _securityState.update { state ->
            val confirmError = if (state.confirmPassphrase.isNotBlank() && state.confirmPassphrase != passphrase) {
                "Passphrases don't match"
            } else null
            
            state.copy(
                newPassphrase = passphrase,
                newPassphraseError = error,
                confirmPassphraseError = confirmError
            )
        }
    }
    
    fun setConfirmPassphrase(passphrase: String) {
        _securityState.update { state ->
            val error = if (passphrase != state.newPassphrase) {
                "Passphrases don't match"
            } else null
            
            state.copy(
                confirmPassphrase = passphrase,
                confirmPassphraseError = error
            )
        }
    }
    
    fun changePassphrase() {
        val state = _securityState.value
        if (!state.canChangePassphrase) return
        
        scope.launch {
            _securityState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // TODO: Change passphrase via keyManager
                // keyManager.changePassphrase(state.currentPassphrase, state.newPassphrase)
                
                hideChangePassphrase()
                _securityState.update { it.copy(
                    isLoading = false,
                    successMessage = "Passphrase changed successfully"
                )}
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Failed to change passphrase"
                
                // Check if it's a wrong current passphrase error
                if (errorMessage.contains("incorrect", ignoreCase = true) ||
                    errorMessage.contains("wrong", ignoreCase = true)) {
                    _securityState.update { it.copy(
                        isLoading = false,
                        currentPassphraseError = "Current passphrase is incorrect"
                    )}
                } else {
                    _securityState.update { it.copy(
                        isLoading = false,
                        error = errorMessage
                    )}
                }
            }
        }
    }
    
    fun toggleSecurityBiometrics(enabled: Boolean) {
        if (!_securityState.value.biometricsAvailable) return
        
        scope.launch {
            try {
                // TODO: Enable/disable biometrics
                _securityState.update { it.copy(biometricsEnabled = enabled) }
            } catch (e: Exception) {
                _securityState.update { it.copy(error = "Failed to update biometrics: ${e.message}") }
            }
        }
    }
    
    fun showCryptoEraseConfirm() {
        _securityState.update { it.copy(
            showCryptoEraseConfirm = true,
            cryptoEraseConfirmText = ""
        )}
    }
    
    fun hideCryptoEraseConfirm() {
        _securityState.update { it.copy(
            showCryptoEraseConfirm = false,
            cryptoEraseConfirmText = ""
        )}
    }
    
    fun setCryptoEraseConfirmText(text: String) {
        _securityState.update { it.copy(cryptoEraseConfirmText = text) }
    }
    
    fun executeCryptoErase() {
        if (!_securityState.value.canCryptoErase) return
        
        scope.launch {
            _securityState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // TODO: Execute crypto erase
                // keyManager.cryptoErase()
                
                hideCryptoEraseConfirm()
                _securityState.update { it.copy(
                    isLoading = false,
                    successMessage = "All data has been securely erased"
                )}
                
                // TODO: Navigate to onboarding/setup
            } catch (e: Exception) {
                _securityState.update { it.copy(
                    isLoading = false,
                    error = "Failed to erase data: ${e.message}"
                )}
            }
        }
    }
    
    // ========== Helpers ==========
    
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
