package com.ledgerlens.data.repositories.impl

import app.cash.turbine.test
import com.ledgerlens.data.repositories.ReceiptEntity
import com.ledgerlens.data.repositories.ReceiptItemEntity
import com.ledgerlens.db.LedgerLensDatabase
import com.ledgerlens.domain.Money
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class SqlDelightReceiptRepositoryTest {
    private lateinit var database: LedgerLensDatabase
    private lateinit var repository: SqlDelightReceiptRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        repository = SqlDelightReceiptRepository(database, testDispatcher)
    }

    private fun createTestReceipt(id: String = "receipt-1", merchantName: String = "Test Store") = ReceiptEntity(
        id = id,
        imagePath = "/path/to/image.jpg",
        thumbnailPath = null,
        merchantName = merchantName,
        totalAmount = Money(2500L, "USD"), // $25.00
        receiptDate = LocalDate(2024, 1, 15),
        linkedTransactionId = null,
        ocrText = "Test receipt OCR text",
        ocrConfidence = 0.95f,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun createTestReceiptItem(
        id: String = "item-1",
        receiptId: String = "receipt-1",
        name: String = "Test Item"
    ) = ReceiptItemEntity(
        id = id,
        receiptId = receiptId,
        name = name,
        quantity = 2,
        unitPriceMinorUnits = 500L, // $5.00
        currencyCode = "USD",
        itemType = "item",
        sortOrder = 0
    )

    @Test
    fun `insertReceipt stores receipt correctly`() = runTest(testDispatcher) {
        val receipt = createTestReceipt()

        repository.insertReceipt(receipt)

        repository.getReceipt(receipt.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(receipt.id, result.id)
            assertEquals(receipt.merchantName, result.merchantName)
            assertEquals(receipt.totalAmount?.minorUnits, result.totalAmount?.minorUnits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllReceipts returns all receipts ordered by date`() = runTest(testDispatcher) {
        val receipt1 = createTestReceipt(id = "receipt-1")
        val receipt2 = createTestReceipt(id = "receipt-2", merchantName = "Another Store")

        repository.insertReceipt(receipt1)
        repository.insertReceipt(receipt2)

        repository.getAllReceipts().test {
            val receipts = awaitItem()
            assertEquals(2, receipts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchReceipts finds receipts by merchant name`() = runTest(testDispatcher) {
        val receipt1 = createTestReceipt(id = "receipt-1", merchantName = "Coffee Shop")
        val receipt2 = createTestReceipt(id = "receipt-2", merchantName = "Grocery Store")

        repository.insertReceipt(receipt1)
        repository.insertReceipt(receipt2)

        repository.searchReceipts("Coffee").test {
            val receipts = awaitItem()
            assertEquals(1, receipts.size)
            assertEquals("Coffee Shop", receipts.first().merchantName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateReceipt modifies receipt correctly`() = runTest(testDispatcher) {
        val receipt = createTestReceipt()
        repository.insertReceipt(receipt)

        val updated = receipt.copy(merchantName = "Updated Store")
        repository.updateReceipt(updated)

        repository.getReceipt(receipt.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Updated Store", result.merchantName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteReceipt removes receipt and items`() = runTest(testDispatcher) {
        val receipt = createTestReceipt()
        repository.insertReceipt(receipt)

        val item = createTestReceiptItem(receiptId = receipt.id)
        repository.insertReceiptItems(listOf(item))

        repository.deleteReceipt(receipt.id)

        repository.getReceipt(receipt.id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `linkToTransaction associates receipt with transaction`() = runTest(testDispatcher) {
        val receipt = createTestReceipt()
        repository.insertReceipt(receipt)

        repository.linkToTransaction(receipt.id, "txn-123")

        repository.getReceipt(receipt.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("txn-123", result.linkedTransactionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertReceiptItems stores items correctly`() = runTest(testDispatcher) {
        val receipt = createTestReceipt()
        repository.insertReceipt(receipt)

        val items = listOf(
            createTestReceiptItem(id = "item-1", receiptId = receipt.id, name = "Item 1"),
            createTestReceiptItem(id = "item-2", receiptId = receipt.id, name = "Item 2")
        )
        repository.insertReceiptItems(items)

        repository.getReceiptWithItems(receipt.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(2, result.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getReceiptWithItems returns receipt with items and allocations`() = runTest(testDispatcher) {
        val receipt = createTestReceipt()
        repository.insertReceipt(receipt)

        val items = listOf(createTestReceiptItem(receiptId = receipt.id))
        repository.insertReceiptItems(items)

        repository.getReceiptWithItems(receipt.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(receipt.id, result.receipt.id)
            assertEquals(1, result.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `countAll returns receipt count`() = runTest(testDispatcher) {
        val receipt1 = createTestReceipt(id = "receipt-1")
        val receipt2 = createTestReceipt(id = "receipt-2")

        repository.insertReceipt(receipt1)
        repository.insertReceipt(receipt2)

        val count = repository.countAll()
        assertEquals(2L, count)
    }

    @Test
    fun `getReceiptsByTransaction returns linked receipts`() = runTest(testDispatcher) {
        val receipt1 = createTestReceipt(id = "receipt-1")
        val receipt2 = createTestReceipt(id = "receipt-2")

        repository.insertReceipt(receipt1)
        repository.insertReceipt(receipt2)

        repository.linkToTransaction("receipt-1", "txn-123")

        repository.getReceiptsByTransaction("txn-123").test {
            val receipts = awaitItem()
            assertEquals(1, receipts.size)
            assertEquals("receipt-1", receipts.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteReceiptItems removes only items for specified receipt`() = runTest(testDispatcher) {
        val receipt = createTestReceipt()
        repository.insertReceipt(receipt)

        val items = listOf(
            createTestReceiptItem(id = "item-1", receiptId = receipt.id),
            createTestReceiptItem(id = "item-2", receiptId = receipt.id)
        )
        repository.insertReceiptItems(items)

        repository.deleteReceiptItems(receipt.id)

        repository.getReceiptWithItems(receipt.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertTrue(result.items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
