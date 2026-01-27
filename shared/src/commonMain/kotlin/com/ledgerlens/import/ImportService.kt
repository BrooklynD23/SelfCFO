package com.ledgerlens.import

import com.ledgerlens.categorization.pipeline.AmountBucket
import com.ledgerlens.categorization.pipeline.CategorizationPipeline
import com.ledgerlens.categorization.pipeline.PipelineResult
import com.ledgerlens.categorization.pipeline.TransactionFeatures
import com.ledgerlens.categorization.pipeline.ReviewItemMetadata
import com.ledgerlens.data.Ids
import com.ledgerlens.data.repositories.ImportJobEntity
import com.ledgerlens.data.repositories.ImportRepository
import com.ledgerlens.data.repositories.ImportSourceType
import com.ledgerlens.data.repositories.ImportStatus
import com.ledgerlens.data.repositories.ImportedTransactionEntity
import com.ledgerlens.data.repositories.ImportedTransactionRepository
import com.ledgerlens.data.repositories.DuplicateCandidateRepository
import com.ledgerlens.data.repositories.ReviewQueueRepository
import com.ledgerlens.data.repositories.SourceFileEntity
import com.ledgerlens.domain.Money
import com.ledgerlens.security.sha256Hex
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Core import orchestrator (non-UI).
 *
 * For now, this implements CSV import end-to-end: parse -> persist -> categorize -> enqueue review.
 * PDF import can be added later via PdfParser with the same persistence path.
 */
class ImportService(
    private val importRepository: ImportRepository,
    private val importedTransactionRepository: ImportedTransactionRepository,
    private val duplicateCandidateRepository: DuplicateCandidateRepository,
    private val reviewQueueRepository: ReviewQueueRepository,
    private val categorizationPipeline: CategorizationPipeline,
    private val csvParser: CsvParser
) {
    suspend fun importCsv(request: ImportRequest): ImportOutcome {
        val now = Clock.System.now()
        val jobId = Ids.newId("import")

        val fileHash = sha256Hex(request.fileBytes)
        val alreadyImported = importRepository.isFileAlreadyImported(fileHash)
        val existingSourceFileId = if (alreadyImported) importRepository.getSourceFileIdByHash(fileHash) else null
        val sourceFileId = existingSourceFileId ?: Ids.newId("sf")

        val job = ImportJobEntity(
            id = jobId,
            sourceFileName = request.fileName,
            sourceType = ImportSourceType.CSV,
            bankTemplate = null,
            status = ImportStatus.IN_PROGRESS,
            totalRows = 0,
            importedCount = 0,
            duplicatesSkipped = 0,
            errorsCount = 0,
            errorDetails = null,
            startedAt = now,
            completedAt = null
        )

        if (existingSourceFileId == null) {
            // Insert source file first so the import_job FK is valid.
            importRepository.insertSourceFile(
                SourceFileEntity(
                    id = sourceFileId,
                    importJobId = jobId,
                    filePath = request.filePath,
                    fileName = request.fileName,
                    fileHash = fileHash,
                    fileSize = request.fileBytes.size.toLong(),
                    createdAt = now
                )
            )
        }

        importRepository.createImportJobForSourceFile(job, sourceFileId)

        val parse = csvParser.parse(
            csvData = request.fileBytes,
            options = CsvParseOptions(
                delimiter = request.delimiter,
                hasHeader = request.hasHeader,
                currencyCode = request.currencyCode
            )
        )

        return when (parse) {
            is CsvParseResult.Failure -> {
                importRepository.markFailed(jobId, parse.error.message ?: "CSV parse failed")
                ImportOutcome.Failure(jobId, parse.error)
            }

            is CsvParseResult.NeedsMapping -> {
                importRepository.markFailed(jobId, "CSV needs column mapping")
                ImportOutcome.NeedsMapping(jobId, parse.headers, parse.sampleRows, parse.suggestedMapping)
            }

            is CsvParseResult.Success -> {
                val parsedTransactions = parse.transactions
                var inserted = 0
                var dupes = 0
                var errors = 0
                var enqueued = 0

                // If alreadyImported, treat everything as duplicate (but still report counts).
                if (alreadyImported) {
                    dupes = parsedTransactions.size
                    importRepository.updateProgress(jobId, importedCount = 0, duplicatesSkipped = dupes, errorsCount = 0)
                    importRepository.markCompleted(jobId, withErrors = false)
                    return ImportOutcome.Success(
                        jobId = jobId,
                        sourceFileId = sourceFileId,
                        totalParsed = parsedTransactions.size,
                        inserted = 0,
                        duplicates = dupes,
                        errors = 0,
                        enqueuedForReview = 0
                    )
                }

                val toInsert = mutableListOf<ImportedTransactionEntity>()
                val fingerprintMatchesByTxnId = mutableMapOf<String, Pair<String, List<String>>>()
                val featuresByTxnId = mutableMapOf<String, TransactionFeatures>()
                val pipelineByTxnId = mutableMapOf<String, PipelineResult>()

                parsedTransactions.forEach { pt ->
                    try {
                        val merchantNormalized = normalizeMerchant(pt.descriptionRaw)
                        val fingerprint = FingerprintGenerator.fingerprint(
                            merchantNormalized = merchantNormalized,
                            postedDate = pt.postedDate,
                            amount = pt.amount,
                            accountId = request.accountId
                        )
                        val matchIds = importedTransactionRepository
                            .findMatchRefsByFingerprint(fingerprint)
                            .filter { it.sourceFileId != sourceFileId }
                            .map { it.id }

                        val txnId = Ids.newId("tx")

                        val imported = ImportedTransactionEntity(
                            id = txnId,
                            importJobId = jobId,
                            sourceFileId = sourceFileId,
                            sourceRowRef = pt.rowRef,
                            fingerprint = fingerprint,
                            accountId = request.accountId,
                            postedDate = pt.postedDate,
                            transactionDate = pt.transactionDate,
                            descriptionRaw = pt.descriptionRaw,
                            merchantNormalized = merchantNormalized,
                            merchantId = null,
                            amount = pt.amount,
                            balanceAfterMinorUnits = pt.balance?.minorUnits,
                            categoryIdAuto = null,
                            categoryConfidence = null,
                            categoryReason = null,
                            parseWarnings = null,
                            importedAt = now
                        )

                        toInsert += imported
                        if (matchIds.isNotEmpty()) {
                            fingerprintMatchesByTxnId[txnId] = fingerprint to matchIds
                        }

                        val features = toPipelineFeatures(
                            merchantNormalized = merchantNormalized,
                            descriptionRaw = pt.descriptionRaw,
                            amount = pt.amount,
                            postedDate = pt.postedDate,
                            accountId = request.accountId
                        )
                        val pipeline = categorizationPipeline.categorize(features, txnId)

                        featuresByTxnId[txnId] = features
                        pipelineByTxnId[txnId] = pipeline
                    } catch (_: Exception) {
                        errors += 1
                    }
                }

                importedTransactionRepository.insertBatch(toInsert)
                inserted = toInsert.size

                val candidateMetadata = buildJsonObject {
                    request.accountId?.let { put("accountId", it) }
                    put("importJobId", jobId)
                    put("sourceFileId", sourceFileId)
                    put("source", "csv_import")
                }.toString()

                toInsert.forEach { txn ->
                    val matches = fingerprintMatchesByTxnId[txn.id] ?: return@forEach
                    val (fingerprint, matchIds) = matches
                    duplicateCandidateRepository.insertFingerprintMatches(
                        transactionId = txn.id,
                        matchedTransactionIds = matchIds,
                        fingerprint = fingerprint,
                        metadataJson = candidateMetadata
                    )
                }

                // Persist auto-category + enqueue review (if needed)
                toInsert.forEach { txn ->
                    val pipeline = pipelineByTxnId[txn.id] ?: return@forEach
                    importedTransactionRepository.updateAutoCategory(
                        transactionId = txn.id,
                        categoryId = pipeline.categoryId,
                        confidence = pipeline.confidence,
                        reason = pipeline.classification.explanation.reason
                    )

                    if (pipeline.needsReview) {
                        val features = featuresByTxnId[txn.id] ?: return@forEach
                        reviewQueueRepository.enqueue(
                            transactionId = txn.id,
                            features = features,
                            pipelineResult = pipeline,
                            metadata = ReviewItemMetadata(
                                accountId = request.accountId,
                                importBatchId = jobId,
                                source = "csv_import",
                                tags = emptySet()
                            )
                        )
                        enqueued += 1
                    }
                }

                importRepository.updateProgress(jobId, importedCount = inserted, duplicatesSkipped = dupes, errorsCount = errors)
                importRepository.markCompleted(jobId, withErrors = errors > 0)

                ImportOutcome.Success(
                    jobId = jobId,
                    sourceFileId = sourceFileId,
                    totalParsed = parsedTransactions.size,
                    inserted = inserted,
                    duplicates = dupes,
                    errors = errors,
                    enqueuedForReview = enqueued
                )
            }
        }
    }

    private fun toPipelineFeatures(
        merchantNormalized: String,
        descriptionRaw: String,
        amount: Money,
        postedDate: LocalDate,
        accountId: String?
    ): TransactionFeatures {
        val cents = amount.minorUnits
        val tokens = tokenize(descriptionRaw)
        return TransactionFeatures(
            merchantNormalized = merchantNormalized,
            descriptionRaw = descriptionRaw,
            descriptionTokens = tokens,
            amountCents = cents,
            amountBucket = AmountBucket.fromCents(cents),
            isDebit = amount.minorUnits < 0,
            dayOfWeek = postedDate.dayOfWeek.ordinal,
            dayOfMonth = postedDate.dayOfMonth,
            accountId = accountId
        )
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .distinct()
            .take(20)
    }

    private fun normalizeMerchant(merchant: String): String {
        return merchant
            .lowercase()
            .replace(MERCHANT_NOISE_PATTERN, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can", "this",
            "that", "these", "those", "it", "its", "payment", "purchase", "debit",
            "credit", "card", "pos", "ach", "fee", "ref", "id"
        )

        private val MERCHANT_NOISE_PATTERN = Regex(
            """(\d{4,})|""" + // Long numbers (IDs, zip codes)
                """([*#]+\d+)|""" + // Masked card numbers
                """(\b[A-Z]{2}\s*\d{5}\b)|""" + // State + ZIP
                """(\bPOS\b)|(\bDEBIT\b)|(\bPURCHASE\b)|""" + // Common noise words
                """(\d{2}/\d{2})""", // Date patterns
            RegexOption.IGNORE_CASE
        )
    }
}

data class ImportRequest(
    val fileName: String,
    val filePath: String,
    val fileBytes: ByteArray,
    val accountId: String? = null,
    val currencyCode: String = "USD",
    val delimiter: Char? = null,
    val hasHeader: Boolean? = null
)

sealed class ImportOutcome {
    data class Success(
        val jobId: String,
        val sourceFileId: String,
        val totalParsed: Int,
        val inserted: Int,
        val duplicates: Int,
        val errors: Int,
        val enqueuedForReview: Int
    ) : ImportOutcome()

    data class NeedsMapping(
        val jobId: String,
        val headers: List<String>,
        val sampleRows: List<List<String>>,
        val suggestedMapping: ColumnMapping
    ) : ImportOutcome()

    data class Failure(val jobId: String, val error: Throwable) : ImportOutcome()
}

