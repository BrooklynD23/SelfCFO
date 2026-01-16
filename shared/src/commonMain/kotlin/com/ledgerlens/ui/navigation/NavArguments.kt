package com.ledgerlens.ui.navigation

/**
 * Navigation argument keys used throughout the app.
 * Provides type-safe access to navigation parameters.
 */
object NavArgs {
    const val TRANSACTION_ID = "transactionId"
    const val RECEIPT_ID = "receiptId"
    const val CATEGORY_ID = "categoryId"
    const val ACCOUNT_ID = "accountId"
    const val IMPORT_FILE_PATH = "importFilePath"
    const val SEARCH_QUERY = "searchQuery"
    const val DATE_RANGE_START = "dateRangeStart"
    const val DATE_RANGE_END = "dateRangeEnd"
}

/**
 * Type-safe navigation arguments for transaction detail screen.
 */
data class TransactionDetailArgs(
    val transactionId: String
) {
    companion object {
        fun fromRoute(route: String): TransactionDetailArgs? {
            val regex = "transactions/([^/]+)".toRegex()
            val match = regex.find(route) ?: return null
            return TransactionDetailArgs(
                transactionId = match.groupValues[1]
            )
        }
    }
}

/**
 * Type-safe navigation arguments for receipt detail screen.
 */
data class ReceiptDetailArgs(
    val receiptId: String
) {
    companion object {
        fun fromRoute(route: String): ReceiptDetailArgs? {
            val regex = "receipts/([^/]+)".toRegex()
            val match = regex.find(route) ?: return null
            return ReceiptDetailArgs(
                receiptId = match.groupValues[1]
            )
        }
    }
}

/**
 * Type-safe navigation arguments for category detail screen.
 */
data class CategoryDetailArgs(
    val categoryId: String
) {
    companion object {
        fun fromRoute(route: String): CategoryDetailArgs? {
            val regex = "categories/([^/]+)".toRegex()
            val match = regex.find(route) ?: return null
            return CategoryDetailArgs(
                categoryId = match.groupValues[1]
            )
        }
    }
}

/**
 * Type-safe navigation arguments for account detail screen.
 */
data class AccountDetailArgs(
    val accountId: String
) {
    companion object {
        fun fromRoute(route: String): AccountDetailArgs? {
            val regex = "accounts/([^/]+)".toRegex()
            val match = regex.find(route) ?: return null
            return AccountDetailArgs(
                accountId = match.groupValues[1]
            )
        }
    }
}

/**
 * Type-safe navigation arguments for search screen.
 */
data class SearchArgs(
    val initialQuery: String? = null,
    val dateRangeStart: String? = null,
    val dateRangeEnd: String? = null
)

/**
 * Type-safe navigation arguments for import screen.
 */
data class ImportArgs(
    val filePath: String? = null
)
