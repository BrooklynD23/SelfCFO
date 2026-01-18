package com.ledgerlens.data.repositories

import com.ledgerlens.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Domain model for a receipt.
 */
data class ReceiptEntity(
    val id: String,
    val imagePath: String,
    val thumbnailPath: String?,
    val merchantName: String?,
    val totalAmount: Money?,
    val receiptDate: LocalDate?,
    val linkedTransactionId: String?,
    val ocrText: String?,
    val ocrConfidence: Float?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Domain model for a receipt item.
 */
data class ReceiptItemEntity(
    val id: String,
    val receiptId: String,
    val name: String,
    val quantity: Int,
    val unitPriceMinorUnits: Long,
    val currencyCode: String,
    val itemType: String,
    val sortOrder: Int
)

/**
 * Domain model for item allocation to participants.
 */
data class ItemAllocationEntity(
    val id: String,
    val receiptItemId: String,
    val participantId: String,
    val shareNumerator: Int,
    val shareDenominator: Int,
    val allocatedAmountMinorUnits: Long
)

/**
 * Receipt with items and allocation info.
 */
data class ReceiptWithItems(
    val receipt: ReceiptEntity,
    val items: List<ReceiptItemEntity>,
    val allocations: Map<String, List<ItemAllocationEntity>> // itemId -> allocations
)

/**
 * Repository interface for receipt data access.
 */
interface ReceiptRepository {
    /**
     * Get all receipts ordered by date.
     */
    fun getAllReceipts(): Flow<List<ReceiptEntity>>

    /**
     * Get a receipt by ID.
     */
    fun getReceipt(id: String): Flow<ReceiptEntity?>

    /**
     * Get receipt with all items and allocations.
     */
    fun getReceiptWithItems(id: String): Flow<ReceiptWithItems?>

    /**
     * Get receipts linked to a transaction.
     */
    fun getReceiptsByTransaction(transactionId: String): Flow<List<ReceiptEntity>>

    /**
     * Search receipts by merchant name or OCR text.
     */
    fun searchReceipts(query: String): Flow<List<ReceiptEntity>>

    /**
     * Insert a new receipt.
     */
    suspend fun insertReceipt(receipt: ReceiptEntity)

    /**
     * Update a receipt.
     */
    suspend fun updateReceipt(receipt: ReceiptEntity)

    /**
     * Delete a receipt and its items.
     */
    suspend fun deleteReceipt(id: String)

    /**
     * Link a receipt to a transaction.
     */
    suspend fun linkToTransaction(receiptId: String, transactionId: String)

    /**
     * Insert receipt items.
     */
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    /**
     * Update receipt items.
     */
    suspend fun updateReceiptItems(items: List<ReceiptItemEntity>)

    /**
     * Delete all items for a receipt.
     */
    suspend fun deleteReceiptItems(receiptId: String)

    /**
     * Set item allocations.
     */
    suspend fun setItemAllocations(allocations: List<ItemAllocationEntity>)

    /**
     * Count all receipts.
     */
    suspend fun countAll(): Long
}
