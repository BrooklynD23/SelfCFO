package com.ledgerlens.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Domain model for an import job.
 */
data class ImportJobEntity(
    val id: String,
    val sourceFileName: String,
    val sourceType: ImportSourceType,
    val bankTemplate: String?,
    val status: ImportStatus,
    val totalRows: Int,
    val importedCount: Int,
    val duplicatesSkipped: Int,
    val errorsCount: Int,
    val errorDetails: String?,
    val startedAt: Instant,
    val completedAt: Instant?
)

/**
 * Types of import sources.
 */
enum class ImportSourceType {
    CSV,
    PDF,
    OFX,
    QFX,
    MANUAL
}

/**
 * Status of an import job.
 */
enum class ImportStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED
}

/**
 * Domain model for a source file.
 */
data class SourceFileEntity(
    val id: String,
    val importJobId: String,
    val filePath: String,
    val fileName: String,
    val fileHash: String,
    val fileSize: Long,
    val createdAt: Instant
)

/**
 * Import job with source files.
 */
data class ImportJobWithFiles(
    val job: ImportJobEntity,
    val sourceFiles: List<SourceFileEntity>
)

/**
 * Repository interface for import job data access.
 */
interface ImportRepository {
    /**
     * Get all import jobs.
     */
    fun getAllImportJobs(): Flow<List<ImportJobEntity>>

    /**
     * Get recent import jobs.
     */
    fun getRecentImportJobs(limit: Int = 10): Flow<List<ImportJobEntity>>

    /**
     * Get an import job by ID.
     */
    fun getImportJob(id: String): Flow<ImportJobEntity?>

    /**
     * Get import job with source files.
     */
    fun getImportJobWithFiles(id: String): Flow<ImportJobWithFiles?>

    /**
     * Create a new import job.
     */
    suspend fun createImportJob(job: ImportJobEntity): String

    /**
     * Update import job progress.
     */
    suspend fun updateProgress(
        id: String,
        importedCount: Int,
        duplicatesSkipped: Int,
        errorsCount: Int
    )

    /**
     * Mark job as completed.
     */
    suspend fun markCompleted(id: String, withErrors: Boolean = false)

    /**
     * Mark job as failed.
     */
    suspend fun markFailed(id: String, errorDetails: String)

    /**
     * Mark job as cancelled.
     */
    suspend fun markCancelled(id: String)

    /**
     * Insert a source file record.
     */
    suspend fun insertSourceFile(file: SourceFileEntity)

    /**
     * Check if a file has been imported (by hash).
     */
    suspend fun isFileAlreadyImported(fileHash: String): Boolean

    /**
     * Delete an import job and related data.
     */
    suspend fun deleteImportJob(id: String)
}
