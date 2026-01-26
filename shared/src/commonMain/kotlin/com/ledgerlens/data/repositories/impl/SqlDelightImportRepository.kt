package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.ImportMapper
import com.ledgerlens.data.repositories.ImportJobEntity
import com.ledgerlens.data.repositories.ImportJobWithFiles
import com.ledgerlens.data.repositories.ImportRepository
import com.ledgerlens.data.repositories.SourceFileEntity
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * SQLDelight implementation of ImportRepository.
 */
class SqlDelightImportRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ImportRepository {

    private val importJobQueries = database.importJobQueries
    private val sourceFileQueries = database.sourceFileQueries

    override fun getAllImportJobs(): Flow<List<ImportJobEntity>> {
        return combine(
            importJobQueries.selectAll().asFlow().mapToList(dispatcher),
            sourceFileQueries.selectPending().asFlow().mapToList(dispatcher) // Get source files
        ) { jobs, _ ->
            jobs.map { job ->
                val sourceFile = sourceFileQueries.selectById(job.source_file_id).executeAsOneOrNull()
                ImportMapper.toDomain(job, sourceFile)
            }
        }
    }

    override fun getRecentImportJobs(limit: Int): Flow<List<ImportJobEntity>> {
        return importJobQueries.selectRecent(limit.toLong())
            .asFlow()
            .mapToList(dispatcher)
            .map { jobs ->
                jobs.map { job ->
                    val sourceFile = sourceFileQueries.selectById(job.source_file_id).executeAsOneOrNull()
                    ImportMapper.toDomain(job, sourceFile)
                }
            }
    }

    override fun getImportJob(id: String): Flow<ImportJobEntity?> {
        return importJobQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { job ->
                job?.let {
                    val sourceFile = sourceFileQueries.selectById(it.source_file_id).executeAsOneOrNull()
                    ImportMapper.toDomain(it, sourceFile)
                }
            }
    }

    override fun getImportJobWithFiles(id: String): Flow<ImportJobWithFiles?> {
        return importJobQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { job ->
                job?.let {
                    val sourceFile = sourceFileQueries.selectById(it.source_file_id).executeAsOneOrNull()
                    val jobEntity = ImportMapper.toDomain(it, sourceFile)
                    val files = sourceFile?.let { sf ->
                        listOf(ImportMapper.toSourceFileEntity(sf))
                    } ?: emptyList()
                    ImportJobWithFiles(jobEntity, files)
                }
            }
    }

    override suspend fun createImportJob(job: ImportJobEntity): String = withContext(dispatcher) {
        // First, we need a source file ID. For now, create a placeholder.
        val sourceFileId = "sf-${job.id}"
        val params = ImportMapper.toDbParams(job, sourceFileId)

        importJobQueries.insert(
            id = params.id,
            source_file_id = params.sourceFileId,
            status = params.status,
            started_at = params.startedAt,
            completed_at = params.completedAt,
            progress_percent = params.progressPercent,
            current_stage = params.currentStage,
            transactions_found = params.transactionsFound,
            transactions_new = params.transactionsNew,
            transactions_dupe = params.transactionsDupe,
            transactions_error = params.transactionsError,
            error_message = params.errorMessage,
            error_details = params.errorDetails,
            checkpoint_data = params.checkpointData
        )
        job.id
    }

    override suspend fun updateProgress(id: String, importedCount: Int, duplicatesSkipped: Int, errorsCount: Int) =
        withContext(dispatcher) {
            val total = importedCount + duplicatesSkipped + errorsCount
            importJobQueries.updateCounts(
                transactions_found = total.toLong(),
                transactions_new = importedCount.toLong(),
                transactions_dupe = duplicatesSkipped.toLong(),
                transactions_error = errorsCount.toLong(),
                id = id
            )
        }

    override suspend fun markCompleted(id: String, withErrors: Boolean) = withContext(dispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (withErrors) {
            importJobQueries.markCompletedWithErrors(now, id)
        } else {
            importJobQueries.markCompleted(now, id)
        }
    }

    override suspend fun markFailed(id: String, errorDetails: String) = withContext(dispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        importJobQueries.markFailed(
            completed_at = now,
            error_message = "Import failed",
            error_details = errorDetails,
            id = id
        )
    }

    override suspend fun markCancelled(id: String) = withContext(dispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        importJobQueries.markCancelled(now, id)
    }

    override suspend fun insertSourceFile(file: SourceFileEntity) = withContext(dispatcher) {
        sourceFileQueries.insert(
            id = file.id,
            content_hash = file.fileHash,
            original_filename = file.fileName,
            mime_type = "application/octet-stream", // Default
            size_bytes = file.fileSize,
            file_type = "csv_statement", // Default
            imported_at = file.createdAt.toEpochMilliseconds(),
            encryption_key_id = "default", // Placeholder
            storage_path = file.filePath,
            page_count = null,
            parse_status = "pending",
            parse_error = null,
            retention_policy = "keep",
            account_id = null
        )
    }

    override suspend fun isFileAlreadyImported(fileHash: String): Boolean = withContext(dispatcher) {
        sourceFileQueries.selectByHash(fileHash).executeAsOneOrNull() != null
    }

    override suspend fun deleteImportJob(id: String) = withContext(dispatcher) {
        importJobQueries.delete(id)
    }
}
