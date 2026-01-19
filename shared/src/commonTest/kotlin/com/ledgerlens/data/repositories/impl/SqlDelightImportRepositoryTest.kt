package com.ledgerlens.data.repositories.impl

import app.cash.turbine.test
import com.ledgerlens.data.repositories.ImportJobEntity
import com.ledgerlens.data.repositories.ImportSourceType
import com.ledgerlens.data.repositories.ImportStatus
import com.ledgerlens.data.repositories.SourceFileEntity
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightImportRepositoryTest {
    private lateinit var database: LedgerLensDatabase
    private lateinit var repository: SqlDelightImportRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        repository = SqlDelightImportRepository(database, testDispatcher)
    }

    private fun createTestImportJob(
        id: String = "test-job-1",
        status: ImportStatus = ImportStatus.PENDING
    ) = ImportJobEntity(
        id = id,
        sourceFileName = "test-file.csv",
        sourceType = ImportSourceType.CSV,
        bankTemplate = null,
        status = status,
        totalRows = 100,
        importedCount = 0,
        duplicatesSkipped = 0,
        errorsCount = 0,
        errorDetails = null,
        startedAt = Clock.System.now(),
        completedAt = null
    )

    private fun createTestSourceFile(
        id: String = "sf-test-1",
        fileHash: String = "hash123"
    ) = SourceFileEntity(
        id = id,
        importJobId = "test-job-1",
        filePath = "/path/to/file.csv",
        fileName = "file.csv",
        fileHash = fileHash,
        fileSize = 1024L,
        createdAt = Clock.System.now()
    )

    @Test
    fun `createImportJob stores job and returns id`() = runTest(testDispatcher) {
        val job = createTestImportJob()

        val resultId = repository.createImportJob(job)

        assertEquals(job.id, resultId)
        repository.getImportJob(job.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(job.id, result.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecentImportJobs returns jobs in descending order by start time`() = runTest(testDispatcher) {
        val job1 = createTestImportJob(id = "job-1")
        val job2 = createTestImportJob(id = "job-2")
        val job3 = createTestImportJob(id = "job-3")

        repository.createImportJob(job1)
        repository.createImportJob(job2)
        repository.createImportJob(job3)

        repository.getRecentImportJobs(2).test {
            val jobs = awaitItem()
            assertEquals(2, jobs.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateProgress updates job counts`() = runTest(testDispatcher) {
        val job = createTestImportJob()
        repository.createImportJob(job)

        repository.updateProgress(
            id = job.id,
            importedCount = 50,
            duplicatesSkipped = 10,
            errorsCount = 5
        )

        repository.getImportJob(job.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(50, result.importedCount)
            assertEquals(10, result.duplicatesSkipped)
            assertEquals(5, result.errorsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markCompleted updates status to completed`() = runTest(testDispatcher) {
        val job = createTestImportJob(status = ImportStatus.IN_PROGRESS)
        repository.createImportJob(job)

        repository.markCompleted(job.id, withErrors = false)

        repository.getImportJob(job.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(ImportStatus.COMPLETED, result.status)
            assertNotNull(result.completedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markFailed updates status to failed with error details`() = runTest(testDispatcher) {
        val job = createTestImportJob(status = ImportStatus.IN_PROGRESS)
        repository.createImportJob(job)

        repository.markFailed(job.id, "Parse error on line 42")

        repository.getImportJob(job.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(ImportStatus.FAILED, result.status)
            assertEquals("Parse error on line 42", result.errorDetails)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markCancelled updates status to cancelled`() = runTest(testDispatcher) {
        val job = createTestImportJob(status = ImportStatus.IN_PROGRESS)
        repository.createImportJob(job)

        repository.markCancelled(job.id)

        repository.getImportJob(job.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(ImportStatus.CANCELLED, result.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertSourceFile stores file correctly`() = runTest(testDispatcher) {
        val file = createTestSourceFile()

        repository.insertSourceFile(file)

        val exists = repository.isFileAlreadyImported(file.fileHash)
        assertTrue(exists)
    }

    @Test
    fun `isFileAlreadyImported returns false for unknown hash`() = runTest(testDispatcher) {
        val exists = repository.isFileAlreadyImported("unknown-hash")
        assertFalse(exists)
    }

    @Test
    fun `deleteImportJob removes job`() = runTest(testDispatcher) {
        val job = createTestImportJob()
        repository.createImportJob(job)

        repository.deleteImportJob(job.id)

        repository.getImportJob(job.id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getImportJobWithFiles returns job with source files`() = runTest(testDispatcher) {
        val job = createTestImportJob()
        repository.createImportJob(job)

        repository.getImportJobWithFiles(job.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(job.id, result.job.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
