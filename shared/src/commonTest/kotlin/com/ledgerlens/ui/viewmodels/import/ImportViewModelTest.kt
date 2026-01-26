package com.ledgerlens.ui.viewmodels.import

import com.ledgerlens.data.repositories.fake.FakeImportRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var importRepository: FakeImportRepository
    private lateinit var viewModel: ImportViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        importRepository = FakeImportRepository()
        viewModel = ImportViewModel(importRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertIs<ImportUiState.Idle>(viewModel.uiState.value)
    }

    @Test
    fun `initial file type is CSV`() {
        assertEquals(ImportFileType.CSV, viewModel.selectedFileType.value)
    }

    @Test
    fun `initial bank selection is null`() {
        assertNull(viewModel.selectedBank.value)
    }

    @Test
    fun `selectFileType updates selected file type`() {
        viewModel.selectFileType(ImportFileType.PDF)
        assertEquals(ImportFileType.PDF, viewModel.selectedFileType.value)

        viewModel.selectFileType(ImportFileType.CSV)
        assertEquals(ImportFileType.CSV, viewModel.selectedFileType.value)
    }

    @Test
    fun `selectBank updates selected bank`() {
        viewModel.selectBank(BankSource.CHASE)
        assertEquals(BankSource.CHASE, viewModel.selectedBank.value)

        viewModel.selectBank(BankSource.BANK_OF_AMERICA)
        assertEquals(BankSource.BANK_OF_AMERICA, viewModel.selectedBank.value)
    }

    @Test
    fun `onFileSelected transitions to FileSelected state`() {
        viewModel.selectFileType(ImportFileType.CSV)
        viewModel.selectBank(BankSource.CHASE)

        viewModel.onFileSelected("/path/to/file.csv", "file.csv")

        val state = viewModel.uiState.value
        assertIs<ImportUiState.FileSelected>(state)
        assertEquals("/path/to/file.csv", state.filePath)
        assertEquals("file.csv", state.fileName)
        assertEquals(ImportFileType.CSV, state.fileType)
        assertEquals(BankSource.CHASE, state.bank)
    }

    @Test
    fun `cancelImport returns to Idle state`() {
        viewModel.onFileSelected("/path/to/file.csv", "file.csv")
        assertIs<ImportUiState.FileSelected>(viewModel.uiState.value)

        viewModel.cancelImport()
        assertIs<ImportUiState.Idle>(viewModel.uiState.value)
    }

    @Test
    fun `resetToIdle clears state and bank selection`() {
        viewModel.selectBank(BankSource.CHASE)
        viewModel.onFileSelected("/path/to/file.csv", "file.csv")

        viewModel.resetToIdle()

        assertIs<ImportUiState.Idle>(viewModel.uiState.value)
        assertNull(viewModel.selectedBank.value)
    }

    @Test
    fun `ImportFileType fromExtension returns correct type for csv`() {
        assertEquals(ImportFileType.CSV, ImportFileType.fromExtension("csv"))
        assertEquals(ImportFileType.CSV, ImportFileType.fromExtension("CSV"))
    }

    @Test
    fun `ImportFileType fromExtension returns correct type for pdf`() {
        assertEquals(ImportFileType.PDF, ImportFileType.fromExtension("pdf"))
        assertEquals(ImportFileType.PDF, ImportFileType.fromExtension("PDF"))
    }

    @Test
    fun `ImportFileType fromExtension returns null for unknown extension`() {
        assertNull(ImportFileType.fromExtension("xlsx"))
        assertNull(ImportFileType.fromExtension("doc"))
    }

    @Test
    fun `BankSource popularBanks contains expected banks`() {
        assertTrue(BankSource.CHASE in BankSource.popularBanks)
        assertTrue(BankSource.BANK_OF_AMERICA in BankSource.popularBanks)
        assertTrue(BankSource.WELLS_FARGO in BankSource.popularBanks)
        assertTrue(BankSource.CAPITAL_ONE in BankSource.popularBanks)
        assertTrue(BankSource.CITI in BankSource.popularBanks)
        assertTrue(BankSource.AMEX in BankSource.popularBanks)
    }

    @Test
    fun `BankSource allBanks contains all enum values`() {
        assertEquals(BankSource.values().size, BankSource.allBanks.size)
        BankSource.values().forEach { bank ->
            assertTrue(bank in BankSource.allBanks)
        }
    }

    @Test
    fun `ImportResult successRate calculates correctly`() {
        val result = ImportResult(
            fileName = "test.csv",
            totalTransactions = 100,
            importedCount = 95,
            duplicatesSkipped = 3,
            duplicatesPossible = 2,
            needsReviewCount = 5,
            categorizedCount = 90,
            uncategorizedCount = 5,
            errors = emptyList()
        )

        assertEquals(0.95f, result.successRate)
    }

    @Test
    fun `ImportResult successRate handles zero transactions`() {
        val result = ImportResult(
            fileName = "empty.csv",
            totalTransactions = 0,
            importedCount = 0,
            duplicatesSkipped = 0,
            duplicatesPossible = 0,
            needsReviewCount = 0,
            categorizedCount = 0,
            uncategorizedCount = 0,
            errors = emptyList()
        )

        assertEquals(0f, result.successRate)
    }

    @Test
    fun `ImportResult hasReviewItems is true when needsReviewCount greater than zero`() {
        val result = ImportResult(
            fileName = "test.csv",
            totalTransactions = 100,
            importedCount = 100,
            duplicatesSkipped = 0,
            duplicatesPossible = 0,
            needsReviewCount = 5,
            categorizedCount = 95,
            uncategorizedCount = 5,
            errors = emptyList()
        )

        assertTrue(result.hasReviewItems)
    }

    @Test
    fun `ImportResult hasReviewItems is true when duplicatesPossible greater than zero`() {
        val result = ImportResult(
            fileName = "test.csv",
            totalTransactions = 100,
            importedCount = 100,
            duplicatesSkipped = 0,
            duplicatesPossible = 3,
            needsReviewCount = 0,
            categorizedCount = 100,
            uncategorizedCount = 0,
            errors = emptyList()
        )

        assertTrue(result.hasReviewItems)
    }

    @Test
    fun `ImportResult isFullySuccessful when no errors and no possible duplicates`() {
        val result = ImportResult(
            fileName = "test.csv",
            totalTransactions = 100,
            importedCount = 100,
            duplicatesSkipped = 5,
            duplicatesPossible = 0,
            needsReviewCount = 0,
            categorizedCount = 100,
            uncategorizedCount = 0,
            errors = emptyList()
        )

        assertTrue(result.isFullySuccessful)
    }

    @Test
    fun `ImportUiState Importing progressPercent calculates correctly`() {
        val state = ImportUiState.Importing(
            fileName = "test.csv",
            progress = 0.75f,
            statusMessage = "Processing...",
            transactionsFound = 100,
            transactionsProcessed = 75
        )

        assertEquals(75, state.progressPercent)
    }
}
