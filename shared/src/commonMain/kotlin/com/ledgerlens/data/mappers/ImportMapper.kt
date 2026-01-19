package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.ImportJobEntity
import com.ledgerlens.data.repositories.ImportSourceType
import com.ledgerlens.data.repositories.ImportStatus
import com.ledgerlens.data.repositories.SourceFileEntity
import com.ledgerlens.db.Import_job
import com.ledgerlens.db.Source_file
import kotlinx.datetime.Instant

/**
 * Maps database Import entities to domain models.
 */
object ImportMapper {
    /**
     * Convert database Import_job to domain ImportJobEntity.
     */
    fun toDomain(db: Import_job, sourceFile: Source_file?): ImportJobEntity = ImportJobEntity(
        id = db.id,
        sourceFileName = sourceFile?.original_filename ?: "Unknown",
        sourceType = sourceFile?.file_type?.toImportSourceType() ?: ImportSourceType.CSV,
        bankTemplate = null, // Not stored in schema
        status = db.status.toImportStatus(),
        totalRows = db.transactions_found.toInt(),
        importedCount = db.transactions_new.toInt(),
        duplicatesSkipped = db.transactions_dupe.toInt(),
        errorsCount = db.transactions_error.toInt(),
        errorDetails = db.error_details,
        startedAt = db.started_at?.let { Instant.fromEpochMilliseconds(it) } ?: Instant.DISTANT_PAST,
        completedAt = db.completed_at?.let { Instant.fromEpochMilliseconds(it) }
    )

    /**
     * Convert database Source_file to domain SourceFileEntity.
     */
    fun toSourceFileEntity(db: Source_file): SourceFileEntity = SourceFileEntity(
        id = db.id,
        importJobId = "", // Not stored directly, derived from relationship
        filePath = db.storage_path,
        fileName = db.original_filename,
        fileHash = db.content_hash,
        fileSize = db.size_bytes,
        createdAt = Instant.fromEpochMilliseconds(db.imported_at)
    )

    /**
     * Convert domain ImportJobEntity to database parameters.
     */
    fun toDbParams(entity: ImportJobEntity, sourceFileId: String): ImportJobDbParams = ImportJobDbParams(
        id = entity.id,
        sourceFileId = sourceFileId,
        status = entity.status.toDbValue(),
        startedAt = entity.startedAt.toEpochMilliseconds(),
        completedAt = entity.completedAt?.toEpochMilliseconds(),
        progressPercent = calculateProgress(entity),
        currentStage = entity.status.toStage(),
        transactionsFound = entity.totalRows.toLong(),
        transactionsNew = entity.importedCount.toLong(),
        transactionsDupe = entity.duplicatesSkipped.toLong(),
        transactionsError = entity.errorsCount.toLong(),
        errorMessage = if (entity.status == ImportStatus.FAILED) entity.errorDetails else null,
        errorDetails = entity.errorDetails,
        checkpointData = null
    )

    private fun calculateProgress(entity: ImportJobEntity): Long {
        return when (entity.status) {
            ImportStatus.PENDING -> 0L
            ImportStatus.IN_PROGRESS -> 50L
            ImportStatus.COMPLETED, ImportStatus.COMPLETED_WITH_ERRORS -> 100L
            ImportStatus.FAILED, ImportStatus.CANCELLED -> 100L
        }
    }
}

/**
 * Data class to hold database insert parameters for Import_job.
 */
data class ImportJobDbParams(
    val id: String,
    val sourceFileId: String,
    val status: String,
    val startedAt: Long?,
    val completedAt: Long?,
    val progressPercent: Long,
    val currentStage: String?,
    val transactionsFound: Long,
    val transactionsNew: Long,
    val transactionsDupe: Long,
    val transactionsError: Long,
    val errorMessage: String?,
    val errorDetails: String?,
    val checkpointData: ByteArray?
)

/**
 * Extension to convert file_type string to ImportSourceType.
 */
private fun String.toImportSourceType(): ImportSourceType = when (this.lowercase()) {
    "csv_statement" -> ImportSourceType.CSV
    "pdf_statement", "receipt_pdf", "email_pdf" -> ImportSourceType.PDF
    else -> ImportSourceType.CSV
}

/**
 * Extension to convert status string to ImportStatus.
 */
private fun String.toImportStatus(): ImportStatus = when (this.lowercase()) {
    "queued" -> ImportStatus.PENDING
    "extracting", "parsing", "normalizing", "categorizing", "deduping" -> ImportStatus.IN_PROGRESS
    "complete" -> ImportStatus.COMPLETED
    "failed" -> ImportStatus.FAILED
    "cancelled" -> ImportStatus.CANCELLED
    else -> ImportStatus.PENDING
}

/**
 * Extension to convert ImportStatus to DB status value.
 */
private fun ImportStatus.toDbValue(): String = when (this) {
    ImportStatus.PENDING -> "queued"
    ImportStatus.IN_PROGRESS -> "parsing"
    ImportStatus.COMPLETED -> "complete"
    ImportStatus.COMPLETED_WITH_ERRORS -> "complete"
    ImportStatus.FAILED -> "failed"
    ImportStatus.CANCELLED -> "cancelled"
}

/**
 * Extension to get current stage from ImportStatus.
 */
private fun ImportStatus.toStage(): String? = when (this) {
    ImportStatus.PENDING -> "queued"
    ImportStatus.IN_PROGRESS -> "parsing"
    ImportStatus.COMPLETED, ImportStatus.COMPLETED_WITH_ERRORS -> "complete"
    ImportStatus.FAILED -> "failed"
    ImportStatus.CANCELLED -> "cancelled"
}
