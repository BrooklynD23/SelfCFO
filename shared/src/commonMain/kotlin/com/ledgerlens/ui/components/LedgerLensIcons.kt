package com.ledgerlens.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized icon definitions for LedgerLens.
 * Uses Material Icons for cross-platform consistency.
 */
object LedgerLensIcons {
    // Navigation
    val Home: ImageVector = Icons.Filled.Home
    val HomeOutlined: ImageVector = Icons.Outlined.Home
    val Dashboard: ImageVector = Icons.Filled.Dashboard
    val DashboardOutlined: ImageVector = Icons.Outlined.Dashboard
    val Menu: ImageVector = Icons.Filled.Menu
    val Back: ImageVector = Icons.Filled.ArrowBack
    val ChevronRight: ImageVector = Icons.Filled.ChevronRight
    val Close: ImageVector = Icons.Filled.Close

    // Transactions
    val Transaction: ImageVector = Icons.Filled.Receipt
    val TransactionOutlined: ImageVector = Icons.Outlined.Receipt
    val Income: ImageVector = Icons.Filled.TrendingUp
    val Expense: ImageVector = Icons.Filled.TrendingDown
    val Transfer: ImageVector = Icons.Filled.SwapHoriz
    val ArrowUp: ImageVector = Icons.Filled.ArrowUpward
    val ArrowDown: ImageVector = Icons.Filled.ArrowDownward

    // Categories
    val Category: ImageVector = Icons.Filled.Category
    val CategoryOutlined: ImageVector = Icons.Outlined.Category
    val Folder: ImageVector = Icons.Filled.Folder

    // Accounts
    val Account: ImageVector = Icons.Filled.AccountBalance
    val AccountOutlined: ImageVector = Icons.Outlined.AccountBalance
    val CreditCard: ImageVector = Icons.Filled.CreditCard
    val Money: ImageVector = Icons.Filled.AttachMoney

    // Analytics
    val Analytics: ImageVector = Icons.Filled.PieChart
    val AnalyticsOutlined: ImageVector = Icons.Outlined.PieChart
    val List: ImageVector = Icons.Filled.List

    // Actions
    val Add: ImageVector = Icons.Filled.Add
    val Edit: ImageVector = Icons.Filled.Edit
    val Delete: ImageVector = Icons.Filled.Delete
    val Search: ImageVector = Icons.Filled.Search
    val Filter: ImageVector = Icons.Filled.FilterList
    val Share: ImageVector = Icons.Filled.Share
    val Upload: ImageVector = Icons.Filled.Upload
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Sync: ImageVector = Icons.Filled.Sync
    val MoreOptions: ImageVector = Icons.Filled.MoreVert

    // Status
    val Check: ImageVector = Icons.Filled.Check
    val CheckCircle: ImageVector = Icons.Filled.CheckCircle
    val Error: ImageVector = Icons.Filled.Error
    val Warning: ImageVector = Icons.Filled.Warning
    val Info: ImageVector = Icons.Filled.Info
    val Help: ImageVector = Icons.Filled.Help

    // Date & Time
    val Calendar: ImageVector = Icons.Filled.CalendarMonth
    val DateRange: ImageVector = Icons.Filled.DateRange
    val History: ImageVector = Icons.Filled.History

    // Settings & Security
    val Settings: ImageVector = Icons.Filled.Settings
    val SettingsOutlined: ImageVector = Icons.Outlined.Settings
    val Lock: ImageVector = Icons.Filled.Lock
    val Visibility: ImageVector = Icons.Filled.Visibility
    val VisibilityOff: ImageVector = Icons.Filled.VisibilityOff

    // User & Profile
    val Profile: ImageVector = Icons.Filled.AccountCircle
    val Notifications: ImageVector = Icons.Filled.Notifications
    val Star: ImageVector = Icons.Filled.Star

    // Import specific
    val Import: ImageVector = Icons.Filled.Upload
    val Receipt: ImageVector = Icons.Filled.Receipt

    // Shopping (for category icons)
    val Shopping: ImageVector = Icons.Filled.ShoppingCart
}

/**
 * Category-specific icon mapping.
 */
object CategoryIcons {
    fun getIconForCategory(categoryId: String): ImageVector {
        return when (categoryId.lowercase()) {
            "food", "dining", "restaurants", "groceries" -> LedgerLensIcons.Receipt
            "transport", "transportation", "travel", "gas" -> LedgerLensIcons.CreditCard
            "shopping", "retail" -> LedgerLensIcons.Shopping
            "entertainment", "fun" -> LedgerLensIcons.Star
            "bills", "utilities" -> LedgerLensIcons.Receipt
            "health", "healthcare", "medical" -> LedgerLensIcons.CheckCircle
            "income", "salary", "wages" -> LedgerLensIcons.Income
            "transfer", "transfers" -> LedgerLensIcons.Transfer
            "uncategorized" -> LedgerLensIcons.Help
            else -> LedgerLensIcons.Category
        }
    }
}

/**
 * Transaction type icon mapping.
 */
object TransactionIcons {
    val income: ImageVector = LedgerLensIcons.Income
    val expense: ImageVector = LedgerLensIcons.Expense
    val transfer: ImageVector = LedgerLensIcons.Transfer
}

/**
 * Navigation icon pairs (filled/outlined).
 */
data class NavigationIconPair(
    val filled: ImageVector,
    val outlined: ImageVector
)

object NavigationIcons {
    val home = NavigationIconPair(LedgerLensIcons.Home, LedgerLensIcons.HomeOutlined)
    val transactions = NavigationIconPair(LedgerLensIcons.Transaction, LedgerLensIcons.TransactionOutlined)
    val categories = NavigationIconPair(LedgerLensIcons.Category, LedgerLensIcons.CategoryOutlined)
    val analytics = NavigationIconPair(LedgerLensIcons.Analytics, LedgerLensIcons.AnalyticsOutlined)
    val settings = NavigationIconPair(LedgerLensIcons.Settings, LedgerLensIcons.SettingsOutlined)
}
