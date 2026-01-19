package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.ReceiptMapper
import com.ledgerlens.data.repositories.ItemAllocationEntity
import com.ledgerlens.data.repositories.ReceiptEntity
import com.ledgerlens.data.repositories.ReceiptItemEntity
import com.ledgerlens.data.repositories.ReceiptRepository
import com.ledgerlens.data.repositories.ReceiptWithItems
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight implementation of ReceiptRepository.
 * Manages receipts, items, and allocations for receipt splitting.
 */
class SqlDelightReceiptRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ReceiptRepository {

    private val receiptQueries = database.receiptQueries
    private val receiptItemQueries = database.receiptItemQueries
    private val itemAllocationQueries = database.itemAllocationQueries

    override fun getAllReceipts(): Flow<List<ReceiptEntity>> {
        return receiptQueries.selectAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { receipts -> receipts.map(ReceiptMapper::toDomain) }
    }

    override fun getReceipt(id: String): Flow<ReceiptEntity?> {
        return receiptQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(ReceiptMapper::toDomain) }
    }

    override fun getReceiptWithItems(id: String): Flow<ReceiptWithItems?> {
        return combine(
            receiptQueries.selectById(id).asFlow().mapToOneOrNull(dispatcher),
            receiptItemQueries.selectByReceipt(id).asFlow().mapToList(dispatcher)
        ) { receipt, items ->
            receipt?.let { r ->
                val receiptEntity = ReceiptMapper.toDomain(r)
                val itemEntities = items.map(ReceiptMapper::toReceiptItemEntity)

                // Get allocations for each item
                val allocations = mutableMapOf<String, List<ItemAllocationEntity>>()
                itemEntities.forEach { item ->
                    val itemAllocations = itemAllocationQueries
                        .selectByReceiptItem(item.id)
                        .executeAsList()
                        .map(ReceiptMapper::toItemAllocationEntity)
                    if (itemAllocations.isNotEmpty()) {
                        allocations[item.id] = itemAllocations
                    }
                }

                ReceiptWithItems(
                    receipt = receiptEntity,
                    items = itemEntities,
                    allocations = allocations
                )
            }
        }
    }

    override fun getReceiptsByTransaction(transactionId: String): Flow<List<ReceiptEntity>> {
        return receiptQueries.selectByTransaction(transactionId)
            .asFlow()
            .mapToList(dispatcher)
            .map { receipts -> receipts.map(ReceiptMapper::toDomain) }
    }

    override fun searchReceipts(query: String): Flow<List<ReceiptEntity>> {
        return receiptQueries.search(query, query)
            .asFlow()
            .mapToList(dispatcher)
            .map { receipts -> receipts.map(ReceiptMapper::toDomain) }
    }

    override suspend fun insertReceipt(receipt: ReceiptEntity) = withContext(dispatcher) {
        val params = ReceiptMapper.toDbParams(receipt)
        receiptQueries.insert(
            id = params.id,
            transaction_id = params.transactionId,
            source_file_id = params.sourceFileId,
            merchant_name = params.merchantName,
            receipt_date = params.receiptDate,
            subtotal_minor_units = params.subtotalMinorUnits,
            tax_minor_units = params.taxMinorUnits,
            tip_minor_units = params.tipMinorUnits,
            total_minor_units = params.totalMinorUnits,
            currency_code = params.currencyCode,
            ocr_confidence = params.ocrConfidence,
            ocr_raw_text = params.ocrRawText,
            parse_status = params.parseStatus,
            created_at = params.createdAt
        )
    }

    override suspend fun updateReceipt(receipt: ReceiptEntity) = withContext(dispatcher) {
        val params = ReceiptMapper.toDbParams(receipt)
        receiptQueries.update(
            merchant_name = params.merchantName,
            receipt_date = params.receiptDate,
            subtotal_minor_units = params.subtotalMinorUnits,
            tax_minor_units = params.taxMinorUnits,
            tip_minor_units = params.tipMinorUnits,
            total_minor_units = params.totalMinorUnits,
            currency_code = params.currencyCode,
            ocr_confidence = params.ocrConfidence,
            ocr_raw_text = params.ocrRawText,
            parse_status = params.parseStatus,
            id = params.id
        )
    }

    override suspend fun deleteReceipt(id: String) = withContext(dispatcher) {
        database.transaction {
            // Delete allocations for all items of this receipt
            val items = receiptItemQueries.selectByReceipt(id).executeAsList()
            items.forEach { item ->
                itemAllocationQueries.deleteByReceiptItem(item.id)
            }
            // Delete items
            receiptItemQueries.deleteByReceipt(id)
            // Delete receipt
            receiptQueries.delete(id)
        }
    }

    override suspend fun linkToTransaction(receiptId: String, transactionId: String) = withContext(dispatcher) {
        receiptQueries.linkToTransaction(transactionId, receiptId)
    }

    override suspend fun insertReceiptItems(items: List<ReceiptItemEntity>) = withContext(dispatcher) {
        database.transaction {
            items.forEach { item ->
                receiptItemQueries.insert(
                    id = item.id,
                    receipt_id = item.receiptId,
                    description = item.name,
                    quantity = item.quantity.toDouble(),
                    unit_price_minor_units = item.unitPriceMinorUnits,
                    total_minor_units = item.unitPriceMinorUnits * item.quantity,
                    category_id = null,
                    sort_order = item.sortOrder.toLong(),
                    notes = null
                )
            }
        }
    }

    override suspend fun updateReceiptItems(items: List<ReceiptItemEntity>) = withContext(dispatcher) {
        database.transaction {
            items.forEach { item ->
                receiptItemQueries.update(
                    description = item.name,
                    quantity = item.quantity.toDouble(),
                    unit_price_minor_units = item.unitPriceMinorUnits,
                    total_minor_units = item.unitPriceMinorUnits * item.quantity,
                    category_id = null,
                    notes = null,
                    id = item.id
                )
            }
        }
    }

    override suspend fun deleteReceiptItems(receiptId: String) = withContext(dispatcher) {
        database.transaction {
            // First delete allocations for all items
            val items = receiptItemQueries.selectByReceipt(receiptId).executeAsList()
            items.forEach { item ->
                itemAllocationQueries.deleteByReceiptItem(item.id)
            }
            // Then delete items
            receiptItemQueries.deleteByReceipt(receiptId)
        }
    }

    override suspend fun setItemAllocations(allocations: List<ItemAllocationEntity>) = withContext(dispatcher) {
        database.transaction {
            allocations.forEach { allocation ->
                itemAllocationQueries.upsert(
                    id = allocation.id,
                    receipt_item_id = allocation.receiptItemId,
                    participant_id = allocation.participantId,
                    quantity = allocation.shareNumerator.toDouble() / allocation.shareDenominator,
                    amount_minor_units = allocation.allocatedAmountMinorUnits,
                    notes = null
                )
            }
        }
    }

    override suspend fun countAll(): Long = withContext(dispatcher) {
        receiptQueries.countAll().executeAsOne()
    }
}
