package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.ItemAllocationEntity
import com.ledgerlens.data.repositories.ReceiptEntity
import com.ledgerlens.data.repositories.ReceiptItemEntity
import com.ledgerlens.db.Receipt
import com.ledgerlens.db.Receipt_item
import com.ledgerlens.domain.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Maps database Receipt entities to domain models.
 */
object ReceiptMapper {
    /**
     * Convert database Receipt to domain ReceiptEntity.
     */
    fun toDomain(db: Receipt): ReceiptEntity {
        val currencyCode = db.currency_code
        return ReceiptEntity(
            id = db.id,
            imagePath = db.source_file_id ?: "", // Using source_file_id as image path reference
            thumbnailPath = null,
            merchantName = db.merchant_name,
            totalAmount = Money(db.total_minor_units, currencyCode),
            receiptDate = db.receipt_date?.let { epochMillisToLocalDate(it) },
            linkedTransactionId = db.transaction_id,
            ocrText = db.ocr_raw_text,
            ocrConfidence = db.ocr_confidence?.toFloat(),
            createdAt = db.created_at,
            updatedAt = db.created_at // No updated_at in schema
        )
    }

    /**
     * Convert database Receipt_item to domain ReceiptItemEntity.
     */
    fun toReceiptItemEntity(db: Receipt_item): ReceiptItemEntity = ReceiptItemEntity(
        id = db.id,
        receiptId = db.receipt_id,
        name = db.description,
        quantity = db.quantity.toInt(),
        unitPriceMinorUnits = db.unit_price_minor_units,
        currencyCode = "USD", // Default, not stored per item
        itemType = "item", // Default
        sortOrder = db.sort_order.toInt()
    )

    /**
     * Convert database Item_allocation to domain ItemAllocationEntity.
     * Note: The DB schema uses a simpler model, so we adapt it.
     */
    fun toItemAllocationEntity(db: SelectByReceiptItem): ItemAllocationEntity = ItemAllocationEntity(
        id = db.id,
        receiptItemId = db.receipt_item_id,
        participantId = db.participant_id,
        shareNumerator = db.quantity.toInt(),
        shareDenominator = 1,
        allocatedAmountMinorUnits = db.amount_minor_units
    )

    /**
     * Convert domain ReceiptEntity to database parameters.
     */
    fun toDbParams(entity: ReceiptEntity): ReceiptDbParams = ReceiptDbParams(
        id = entity.id,
        transactionId = entity.linkedTransactionId,
        sourceFileId = entity.imagePath.takeIf { it.isNotBlank() },
        merchantName = entity.merchantName,
        receiptDate = entity.receiptDate?.let { localDateToEpochMillis(it) },
        subtotalMinorUnits = null,
        taxMinorUnits = null,
        tipMinorUnits = null,
        totalMinorUnits = entity.totalAmount?.minorUnits ?: 0L,
        currencyCode = entity.totalAmount?.currencyCode ?: "USD",
        ocrConfidence = entity.ocrConfidence?.toDouble(),
        ocrRawText = entity.ocrText,
        parseStatus = "complete",
        createdAt = entity.createdAt
    )

    /**
     * Convert epoch milliseconds to LocalDate.
     */
    private fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
        return Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    }

    /**
     * Convert LocalDate to epoch milliseconds.
     */
    private fun localDateToEpochMillis(date: LocalDate): Long {
        return date.toEpochDays() * 24 * 60 * 60 * 1000L
    }
}

/**
 * Type alias for the join result from selectByReceiptItem query.
 */
typealias SelectByReceiptItem = com.ledgerlens.db.SelectByReceiptItem

/**
 * Data class to hold database insert/update parameters for Receipt.
 */
data class ReceiptDbParams(
    val id: String,
    val transactionId: String?,
    val sourceFileId: String?,
    val merchantName: String?,
    val receiptDate: Long?,
    val subtotalMinorUnits: Long?,
    val taxMinorUnits: Long?,
    val tipMinorUnits: Long?,
    val totalMinorUnits: Long,
    val currencyCode: String,
    val ocrConfidence: Double?,
    val ocrRawText: String?,
    val parseStatus: String,
    val createdAt: Long
)
