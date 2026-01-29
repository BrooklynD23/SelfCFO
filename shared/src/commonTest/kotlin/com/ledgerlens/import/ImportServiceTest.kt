package com.ledgerlens.import

import com.ledgerlens.categorization.pipeline.CategorizationAction
import com.ledgerlens.categorization.pipeline.CategorizationPipeline
import com.ledgerlens.categorization.pipeline.CategorizationPipelineImpl
import com.ledgerlens.categorization.pipeline.CategorizationConfig
import com.ledgerlens.categorization.pipeline.ClassificationExplanation
import com.ledgerlens.categorization.pipeline.ClassificationResult
import com.ledgerlens.categorization.pipeline.PipelineResult
import com.ledgerlens.categorization.pipeline.PipelineStage
import com.ledgerlens.categorization.pipeline.TransactionFeatures
import com.ledgerlens.data.repositories.impl.SqlDelightImportRepository
import com.ledgerlens.data.repositories.impl.SqlDelightDuplicateCandidateRepository
import com.ledgerlens.data.repositories.impl.SqlDelightImportedTransactionRepository
import com.ledgerlens.data.repositories.impl.SqlDelightReviewQueueRepository
import com.ledgerlens.data.repositories.impl.TestDatabaseHelper
import com.ledgerlens.db.LedgerLensDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ImportServiceTest {
    private lateinit var database: LedgerLensDatabase
    private val dispatcher = StandardTestDispatcher()

    private lateinit var importRepository: SqlDelightImportRepository
    private lateinit var importedTxnRepository: SqlDelightImportedTransactionRepository
    private lateinit var reviewQueueRepository: SqlDelightReviewQueueRepository
    private lateinit var duplicateCandidateRepository: SqlDelightDuplicateCandidateRepository

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        importRepository = SqlDelightImportRepository(database, dispatcher)
        importedTxnRepository = SqlDelightImportedTransactionRepository(database, dispatcher)
        reviewQueueRepository = SqlDelightReviewQueueRepository(database = database, dispatcher = dispatcher)
        duplicateCandidateRepository = SqlDelightDuplicateCandidateRepository(database, dispatcher)
    }

    @Test
    fun `importCsv persists transactions and enqueues low-confidence items`() = runTest(dispatcher) {
        val csv = """
            Date,Description,Amount
            2026-01-01,STARBUCKS,-5.25
            2026-01-02,SALARY,1000.00
        """.trimIndent().encodeToByteArray()

        val pipeline = object : CategorizationPipeline {
            override val config: CategorizationConfig = CategorizationConfig.default()
            override fun updateConfig(newConfig: CategorizationConfig) {}
            override fun getStats() = CategorizationPipelineImpl.createDefault().getStats()

            override fun categorize(features: TransactionFeatures, transactionId: String): PipelineResult {
                val isSalary = features.descriptionRaw.contains("salary", ignoreCase = true)
                val confidence = if (isSalary) 0.95f else 0.35f
                val action = if (confidence >= 0.85f) CategorizationAction.AUTO_APPLY else CategorizationAction.QUEUE_FOR_REVIEW

                val classification = ClassificationResult(
                    categoryId = if (isSalary) "income" else "coffee",
                    confidence = confidence,
                    explanation = ClassificationExplanation(
                        classifierUsed = "test",
                        reason = if (isSalary) "High confidence" else "Low confidence"
                    )
                )

                return PipelineResult(
                    transactionId = transactionId,
                    classification = classification,
                    action = action,
                    stageResults = emptyMap(),
                    processingTimeMs = 0,
                    usedStage = PipelineStage.ML_CLASSIFICATION
                )
            }

            override fun categorizeBatch(transactions: List<com.ledgerlens.categorization.pipeline.TransactionInput>): List<PipelineResult> =
                transactions.map { categorize(it.features, it.transactionId) }
        }

        val service = ImportService(
            importRepository = importRepository,
            importedTransactionRepository = importedTxnRepository,
            duplicateCandidateRepository = duplicateCandidateRepository,
            reviewQueueRepository = reviewQueueRepository,
            categorizationPipeline = pipeline,
            csvParser = CsvParserImpl()
        )

        val outcome = service.importCsv(
            ImportRequest(
                fileName = "test.csv",
                filePath = "/tmp/test.csv",
                fileBytes = csv,
                accountId = "acct-1",
                currencyCode = "USD"
            )
        )

        val success = outcome as ImportOutcome.Success
        assertEquals(2, success.totalParsed, "outcome=$success")
        assertEquals(2, success.inserted, "outcome=$success")
        assertEquals(0, success.duplicates, "outcome=$success")
        assertEquals(0, success.errors, "outcome=$success")
        assertEquals(1, success.enqueuedForReview, "outcome=$success")

        val inserted = database.importedTransactionQueries.selectByImportJob(success.jobId).executeAsList()
        assertEquals(2, inserted.size, "inserted.size=${inserted.size}")

        val pending = database.reviewQueueQueries.countPending().executeAsOne()
        assertEquals(1L, pending, "pending=$pending")
    }

    @Test
    fun `importCsv is idempotent for the same file hash`() = runTest(dispatcher) {
        val csv = """
            Date,Description,Amount
            2026-01-01,STARBUCKS,-5.25
            2026-01-02,SALARY,1000.00
        """.trimIndent().encodeToByteArray()

        val pipeline = CategorizationPipelineImpl.createDefault()

        val service = ImportService(
            importRepository = importRepository,
            importedTransactionRepository = importedTxnRepository,
            duplicateCandidateRepository = duplicateCandidateRepository,
            reviewQueueRepository = reviewQueueRepository,
            categorizationPipeline = pipeline,
            csvParser = CsvParserImpl()
        )

        val first = service.importCsv(
            ImportRequest(
                fileName = "test.csv",
                filePath = "/tmp/test.csv",
                fileBytes = csv,
                accountId = "acct-1"
            )
        ) as ImportOutcome.Success

        val second = service.importCsv(
            ImportRequest(
                fileName = "test.csv",
                filePath = "/tmp/test.csv",
                fileBytes = csv,
                accountId = "acct-1"
            )
        ) as ImportOutcome.Success

        assertEquals(2, first.inserted, "first=$first")
        assertEquals(0, first.duplicates, "first=$first")

        assertEquals(0, second.inserted, "second=$second")
        assertEquals(2, second.duplicates, "second=$second")

        val totalTxns = database.importedTransactionQueries.selectRecent(100L).executeAsList()
        assertEquals(2, totalTxns.size)
        assertTrue(database.sourceFileQueries.selectByHash(com.ledgerlens.security.sha256Hex(csv)).executeAsOneOrNull() != null)
    }

    @Test
    fun `importCsv allows repeated same-day same-amount transactions in the same file`() = runTest(dispatcher) {
        val csv = """
            Date,Description,Amount
            2026-01-01,STARBUCKS,-5.25
            2026-01-01,STARBUCKS,-5.25
        """.trimIndent().encodeToByteArray()

        val pipeline = CategorizationPipelineImpl.createDefault()
        val service = ImportService(
            importRepository = importRepository,
            importedTransactionRepository = importedTxnRepository,
            duplicateCandidateRepository = duplicateCandidateRepository,
            reviewQueueRepository = reviewQueueRepository,
            categorizationPipeline = pipeline,
            csvParser = CsvParserImpl()
        )

        val outcome = service.importCsv(
            ImportRequest(
                fileName = "dupes.csv",
                filePath = "/tmp/dupes.csv",
                fileBytes = csv,
                accountId = "acct-1"
            )
        ) as ImportOutcome.Success

        assertEquals(2, outcome.totalParsed, "outcome=$outcome")
        assertEquals(2, outcome.inserted, "outcome=$outcome")
        assertEquals(0, outcome.duplicates, "outcome=$outcome")

        val inserted = database.importedTransactionQueries.selectByImportJob(outcome.jobId).executeAsList()
        assertEquals(2, inserted.size, "inserted.size=${inserted.size}")
    }

    @Test
    fun `importCsv creates duplicate candidates for cross-file fingerprint matches`() = runTest(dispatcher) {
        val csv1 = """
            Date,Description,Amount
            2026-01-01,STARBUCKS,-5.25
        """.trimIndent().encodeToByteArray()
        val csv2 = """
            Date,Description,Amount
            2026-01-01,STARBUCKS,-5.25
            
        """.trimIndent().encodeToByteArray()

        val pipeline = CategorizationPipelineImpl.createDefault()
        val service = ImportService(
            importRepository = importRepository,
            importedTransactionRepository = importedTxnRepository,
            duplicateCandidateRepository = duplicateCandidateRepository,
            reviewQueueRepository = reviewQueueRepository,
            categorizationPipeline = pipeline,
            csvParser = CsvParserImpl()
        )

        val first = service.importCsv(
            ImportRequest(
                fileName = "a.csv",
                filePath = "/tmp/a.csv",
                fileBytes = csv1,
                accountId = "acct-1"
            )
        ) as ImportOutcome.Success
        val second = service.importCsv(
            ImportRequest(
                fileName = "b.csv",
                filePath = "/tmp/b.csv",
                fileBytes = csv2,
                accountId = "acct-1"
            )
        ) as ImportOutcome.Success

        val firstTxns = database.importedTransactionQueries.selectByImportJob(first.jobId).executeAsList()
        val secondTxns = database.importedTransactionQueries.selectByImportJob(second.jobId).executeAsList()
        assertEquals(1, firstTxns.size)
        assertEquals(1, secondTxns.size)

        val pendingDupes = database.duplicateCandidateQueries.countPending().executeAsOne()
        assertEquals(1L, pendingDupes, "pendingDupes=$pendingDupes")
    }
}
