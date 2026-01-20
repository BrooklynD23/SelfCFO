package com.ledgerlens.ui.viewmodels.import

import com.ledgerlens.data.repositories.ImportJobEntity
import com.ledgerlens.data.repositories.ImportRepository
import com.ledgerlens.data.repositories.ImportSourceType
import com.ledgerlens.data.repositories.ImportStatus
import com.ledgerlens.data.repositories.SourceFileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * ViewModel for the Import wizard flow.
 * Manages file selection, import progress, and result display.
 */
class ImportViewModel(
    private val importRepository: ImportRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Track current import job ID for progress updates
    private var currentJobId: String? = null

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _selectedFileType = MutableStateFlow(ImportFileType.CSV)
    val selectedFileType: StateFlow<ImportFileType> = _selectedFileType.asStateFlow()

    private val _selectedBank = MutableStateFlow<BankSource?>(null)
    val selectedBank: StateFlow<BankSource?> = _selectedBank.asStateFlow()

    fun selectFileType(fileType: ImportFileType) {
        _selectedFileType.value = fileType
    }

    fun selectBank(bank: BankSource) {
        _selectedBank.value = bank
    }

    fun onFileSelected(filePath: String, fileName: String) {
        _uiState.update {
            ImportUiState.FileSelected(
                filePath = filePath,
                fileName = fileName,
                fileType = _selectedFileType.value,
                bank = _selectedBank.value
            )
        }
    }

    fun startImport() {
        val currentState = _uiState.value
        if (currentState !is ImportUiState.FileSelected) return

        viewModelScope.launch {
            try {
                // Create import job in repository
                val jobId = generateJobId()
                val job = ImportJobEntity(
                    id = jobId,
                    sourceFileName = currentState.fileName,
                    sourceType = currentState.fileType.toSourceType(),
                    bankTemplate = currentState.bank?.templateId,
                    status = ImportStatus.IN_PROGRESS,
                    totalRows = 0,
                    importedCount = 0,
                    duplicatesSkipped = 0,
                    errorsCount = 0,
                    errorDetails = null,
                    startedAt = Clock.System.now(),
                    completedAt = null
                )
                importRepository.createImportJob(job)
                currentJobId = jobId

                // Record source file
                val sourceFile = SourceFileEntity(
                    id = generateSourceFileId(),
                    importJobId = jobId,
                    filePath = currentState.filePath,
                    fileName = currentState.fileName,
                    fileHash = currentState.filePath.hashCode().toString(), // Simplified hash for now
                    fileSize = 0L, // Would need actual file size
                    createdAt = Clock.System.now()
                )
                importRepository.insertSourceFile(sourceFile)

                _uiState.value = ImportUiState.Importing(
                    fileName = currentState.fileName,
                    progress = 0f,
                    statusMessage = "Reading file...",
                    transactionsFound = 0,
                    transactionsProcessed = 0
                )

                // Simulated import progress (actual parsing not yet implemented)
                simulateImportProgress(currentState.fileName)
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error(
                    message = e.message ?: "Failed to start import",
                    errorType = ImportErrorType.UNKNOWN,
                    filePath = currentState.filePath,
                    fileName = currentState.fileName
                )
            }
        }
    }

    private suspend fun simulateImportProgress(fileName: String) {
        val stages = listOf(
            "Reading file..." to 0.1f,
            "Parsing transactions..." to 0.3f,
            "Normalizing merchants..." to 0.5f,
            "Detecting duplicates..." to 0.7f,
            "Categorizing transactions..." to 0.9f,
            "Finalizing import..." to 1.0f
        )

        var transactionsFound = 0
        var transactionsProcessed = 0
        var duplicatesSkipped = 0

        for ((message, progress) in stages) {
            delay(500) // Simulated delay
            transactionsFound = (progress * 150).toInt()
            transactionsProcessed = (progress * 142).toInt()
            duplicatesSkipped = (progress * 5).toInt()

            // Update repository progress
            currentJobId?.let { jobId ->
                try {
                    importRepository.updateProgress(
                        id = jobId,
                        importedCount = transactionsProcessed,
                        duplicatesSkipped = duplicatesSkipped,
                        errorsCount = 0
                    )
                } catch (_: Exception) {
                    // Continue even if progress update fails
                }
            }

            _uiState.value = ImportUiState.Importing(
                fileName = fileName,
                progress = progress,
                statusMessage = message,
                transactionsFound = transactionsFound,
                transactionsProcessed = transactionsProcessed
            )
        }

        // Mark job as completed in repository
        currentJobId?.let { jobId ->
            try {
                importRepository.markCompleted(jobId, withErrors = false)
            } catch (_: Exception) {
                // Continue even if completion update fails
            }
        }

        // Simulated result
        _uiState.value = ImportUiState.Complete(
            result = ImportResult(
                fileName = fileName,
                totalTransactions = 150,
                importedCount = 142,
                duplicatesSkipped = 5,
                duplicatesPossible = 3,
                needsReviewCount = 12,
                categorizedCount = 130,
                uncategorizedCount = 12,
                errors = emptyList()
            )
        )
        currentJobId = null
    }

    fun cancelImport() {
        viewModelScope.launch {
            currentJobId?.let { jobId ->
                try {
                    importRepository.markCancelled(jobId)
                } catch (_: Exception) {
                    // Ignore errors on cancel
                }
            }
            currentJobId = null
            _uiState.value = ImportUiState.Idle
        }
    }

    fun resetToIdle() {
        currentJobId = null
        _uiState.value = ImportUiState.Idle
        _selectedBank.value = null
    }

    fun retryImport() {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Error) {
            _uiState.value = ImportUiState.FileSelected(
                filePath = currentState.filePath,
                fileName = currentState.fileName,
                fileType = _selectedFileType.value,
                bank = _selectedBank.value
            )
        }
    }

    fun dismissError() {
        _uiState.value = ImportUiState.Idle
    }

    private fun generateJobId(): String {
        return "import_${Clock.System.now().toEpochMilliseconds()}"
    }

    private fun generateSourceFileId(): String {
        return "file_${Clock.System.now().toEpochMilliseconds()}"
    }

    private fun ImportFileType.toSourceType(): ImportSourceType {
        return when (this) {
            ImportFileType.CSV -> ImportSourceType.CSV
            ImportFileType.PDF -> ImportSourceType.PDF
        }
    }
}

/**
 * Sealed class representing all possible states of the Import flow.
 */
sealed class ImportUiState {
    object Idle : ImportUiState()

    data class FileSelected(
        val filePath: String,
        val fileName: String,
        val fileType: ImportFileType,
        val bank: BankSource?
    ) : ImportUiState()

    data class Importing(
        val fileName: String,
        val progress: Float,
        val statusMessage: String,
        val transactionsFound: Int,
        val transactionsProcessed: Int
    ) : ImportUiState() {
        val progressPercent: Int get() = (progress * 100).toInt()
    }

    data class Complete(
        val result: ImportResult
    ) : ImportUiState()

    data class Error(
        val message: String,
        val errorType: ImportErrorType,
        val filePath: String,
        val fileName: String,
        val canRetry: Boolean = true
    ) : ImportUiState()
}

/**
 * Result of an import operation.
 */
data class ImportResult(
    val fileName: String,
    val totalTransactions: Int,
    val importedCount: Int,
    val duplicatesSkipped: Int,
    val duplicatesPossible: Int,
    val needsReviewCount: Int,
    val categorizedCount: Int,
    val uncategorizedCount: Int,
    val errors: List<ImportError>
) {
    val successRate: Float
        get() = if (totalTransactions > 0) importedCount.toFloat() / totalTransactions else 0f

    val hasReviewItems: Boolean
        get() = needsReviewCount > 0 || duplicatesPossible > 0

    val isFullySuccessful: Boolean
        get() = errors.isEmpty() && duplicatesPossible == 0
}

data class ImportError(
    val lineNumber: Int?,
    val message: String,
    val rawData: String?
)

enum class ImportFileType(val displayName: String, val extensions: List<String>) {
    CSV("CSV File", listOf("csv")),
    PDF("PDF Statement", listOf("pdf"));

    companion object {
        fun fromExtension(ext: String): ImportFileType? =
            values().find { ext.lowercase() in it.extensions }
    }
}

enum class BankSource(val displayName: String, val templateId: String) {
    CHASE("Chase", "chase"),
    BANK_OF_AMERICA("Bank of America", "boa"),
    WELLS_FARGO("Wells Fargo", "wells_fargo"),
    CAPITAL_ONE("Capital One", "capital_one"),
    CITI("Citi", "citi"),
    AMEX("American Express", "amex"),
    DISCOVER("Discover", "discover"),
    US_BANK("US Bank", "us_bank"),
    PNC("PNC", "pnc"),
    GENERIC("Other/Generic", "generic");

    companion object {
        val popularBanks = listOf(CHASE, BANK_OF_AMERICA, WELLS_FARGO, CAPITAL_ONE, CITI, AMEX)
        val allBanks = values().toList()
    }
}

enum class ImportErrorType {
    FILE_NOT_FOUND,
    INVALID_FORMAT,
    PARSE_ERROR,
    PERMISSION_DENIED,
    FILE_TOO_LARGE,
    ENCRYPTION_ERROR,
    UNKNOWN
}
