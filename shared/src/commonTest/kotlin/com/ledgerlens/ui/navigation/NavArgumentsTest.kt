package com.ledgerlens.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NavArgumentsTest {

    @Test
    fun `TransactionDetailArgs parses valid route`() {
        val args = TransactionDetailArgs.fromRoute("transactions/tx-123")
        assertNotNull(args)
        assertEquals("tx-123", args.transactionId)
    }

    @Test
    fun `TransactionDetailArgs returns null for invalid route`() {
        assertNull(TransactionDetailArgs.fromRoute("receipts/r-123"))
        assertNull(TransactionDetailArgs.fromRoute("transactions"))
        assertNull(TransactionDetailArgs.fromRoute(""))
    }

    @Test
    fun `TransactionDetailArgs handles complex ids`() {
        val args = TransactionDetailArgs.fromRoute("transactions/abc-123-def-456")
        assertNotNull(args)
        assertEquals("abc-123-def-456", args.transactionId)
    }

    @Test
    fun `ReceiptDetailArgs parses valid route`() {
        val args = ReceiptDetailArgs.fromRoute("receipts/receipt-456")
        assertNotNull(args)
        assertEquals("receipt-456", args.receiptId)
    }

    @Test
    fun `ReceiptDetailArgs returns null for invalid route`() {
        assertNull(ReceiptDetailArgs.fromRoute("transactions/tx-123"))
        assertNull(ReceiptDetailArgs.fromRoute("receipts"))
        assertNull(ReceiptDetailArgs.fromRoute(""))
    }

    @Test
    fun `CategoryDetailArgs parses valid route`() {
        val args = CategoryDetailArgs.fromRoute("categories/cat-789")
        assertNotNull(args)
        assertEquals("cat-789", args.categoryId)
    }

    @Test
    fun `CategoryDetailArgs returns null for invalid route`() {
        assertNull(CategoryDetailArgs.fromRoute("transactions/tx-123"))
        assertNull(CategoryDetailArgs.fromRoute("categories"))
    }

    @Test
    fun `AccountDetailArgs parses valid route`() {
        val args = AccountDetailArgs.fromRoute("accounts/acc-101")
        assertNotNull(args)
        assertEquals("acc-101", args.accountId)
    }

    @Test
    fun `AccountDetailArgs returns null for invalid route`() {
        assertNull(AccountDetailArgs.fromRoute("transactions/tx-123"))
        assertNull(AccountDetailArgs.fromRoute("accounts"))
    }

    @Test
    fun `NavArgs constants are defined`() {
        assertEquals("transactionId", NavArgs.TRANSACTION_ID)
        assertEquals("receiptId", NavArgs.RECEIPT_ID)
        assertEquals("categoryId", NavArgs.CATEGORY_ID)
        assertEquals("accountId", NavArgs.ACCOUNT_ID)
        assertEquals("importFilePath", NavArgs.IMPORT_FILE_PATH)
        assertEquals("searchQuery", NavArgs.SEARCH_QUERY)
        assertEquals("dateRangeStart", NavArgs.DATE_RANGE_START)
        assertEquals("dateRangeEnd", NavArgs.DATE_RANGE_END)
    }

    @Test
    fun `SearchArgs has default values`() {
        val args = SearchArgs()
        assertNull(args.initialQuery)
        assertNull(args.dateRangeStart)
        assertNull(args.dateRangeEnd)
    }

    @Test
    fun `SearchArgs accepts custom values`() {
        val args = SearchArgs(
            initialQuery = "coffee",
            dateRangeStart = "2024-01-01",
            dateRangeEnd = "2024-12-31"
        )
        assertEquals("coffee", args.initialQuery)
        assertEquals("2024-01-01", args.dateRangeStart)
        assertEquals("2024-12-31", args.dateRangeEnd)
    }

    @Test
    fun `ImportArgs has default values`() {
        val args = ImportArgs()
        assertNull(args.filePath)
    }

    @Test
    fun `ImportArgs accepts custom path`() {
        val args = ImportArgs(filePath = "/path/to/file.csv")
        assertEquals("/path/to/file.csv", args.filePath)
    }
}
