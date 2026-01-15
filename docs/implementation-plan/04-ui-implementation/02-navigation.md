# 02: Navigation

## Overview

Implement the navigation structure using Compose Navigation with bottom tabs and nested navigation graphs.

---

## Implementation Steps

### Step 1: Navigation Routes

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/navigation/Routes.kt
package com.ledgerlens.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 */
sealed interface Route {

    // Bottom navigation tabs
    @Serializable
    data object Dashboard : Route

    @Serializable
    data object Transactions : Route

    @Serializable
    data object Import : Route

    @Serializable
    data object Settings : Route

    // Transaction routes
    @Serializable
    data class TransactionDetail(val transactionId: String) : Route

    @Serializable
    data class TransactionEdit(val transactionId: String) : Route

    // Import routes
    @Serializable
    data object ImportFile : Route

    @Serializable
    data class ImportProgress(val jobId: String) : Route

    @Serializable
    data class ImportReview(val jobId: String) : Route

    // Review routes
    @Serializable
    data object ReviewInbox : Route

    @Serializable
    data class ReviewItem(val transactionId: String) : Route

    // Receipt routes
    @Serializable
    data object Receipts : Route

    @Serializable
    data class ReceiptCapture(val splitId: String?) : Route

    @Serializable
    data class ReceiptSplit(val splitId: String) : Route

    @Serializable
    data class ReceiptSettlement(val splitId: String) : Route

    // Category routes
    @Serializable
    data object Categories : Route

    @Serializable
    data class CategoryEdit(val categoryId: String?) : Route

    // Rule routes
    @Serializable
    data object Rules : Route

    @Serializable
    data class RuleEdit(val ruleId: String?) : Route

    // Settings routes
    @Serializable
    data object AccountSettings : Route

    @Serializable
    data object DataManagement : Route

    @Serializable
    data object About : Route
}

/**
 * Bottom navigation items.
 */
enum class BottomNavItem(
    val route: Route,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    DASHBOARD(
        route = Route.Dashboard,
        label = "Home",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home
    ),
    TRANSACTIONS(
        route = Route.Transactions,
        label = "Transactions",
        icon = Icons.Outlined.List,
        selectedIcon = Icons.Filled.List
    ),
    IMPORT(
        route = Route.Import,
        label = "Import",
        icon = Icons.Outlined.Add,
        selectedIcon = Icons.Filled.Add
    ),
    SETTINGS(
        route = Route.Settings,
        label = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings
    )
}
```

### Step 2: Navigation Graph

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/navigation/NavGraph.kt
package com.ledgerlens.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute

@Composable
fun LedgerLensNavGraph(
    navController: NavHostController,
    startDestination: Route = Route.Dashboard
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Dashboard
        composable<Route.Dashboard> {
            DashboardScreen(
                onNavigateToTransactions = { navController.navigate(Route.Transactions) },
                onNavigateToReview = { navController.navigate(Route.ReviewInbox) },
                onNavigateToImport = { navController.navigate(Route.Import) }
            )
        }

        // Transactions
        composable<Route.Transactions> {
            TransactionsScreen(
                onTransactionClick = { id ->
                    navController.navigate(Route.TransactionDetail(id))
                },
                onFilterClick = { /* Show filter sheet */ }
            )
        }

        composable<Route.TransactionDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.TransactionDetail>()
            TransactionDetailScreen(
                transactionId = route.transactionId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Route.TransactionEdit(route.transactionId)) }
            )
        }

        composable<Route.TransactionEdit> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.TransactionEdit>()
            TransactionEditScreen(
                transactionId = route.transactionId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // Import flow
        composable<Route.Import> {
            ImportScreen(
                onSelectFile = { navController.navigate(Route.ImportFile) },
                onNavigateToJob = { jobId ->
                    navController.navigate(Route.ImportProgress(jobId))
                }
            )
        }

        composable<Route.ImportFile> {
            ImportFileScreen(
                onBack = { navController.popBackStack() },
                onFileSelected = { jobId ->
                    navController.navigate(Route.ImportProgress(jobId)) {
                        popUpTo(Route.Import)
                    }
                }
            )
        }

        composable<Route.ImportProgress> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ImportProgress>()
            ImportProgressScreen(
                jobId = route.jobId,
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Route.ImportReview(route.jobId)) {
                        popUpTo(Route.Import)
                    }
                }
            )
        }

        composable<Route.ImportReview> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ImportReview>()
            ImportReviewScreen(
                jobId = route.jobId,
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Route.Dashboard) {
                        popUpTo(Route.Dashboard) { inclusive = true }
                    }
                }
            )
        }

        // Review inbox
        composable<Route.ReviewInbox> {
            ReviewInboxScreen(
                onBack = { navController.popBackStack() },
                onReviewItem = { id ->
                    navController.navigate(Route.ReviewItem(id))
                }
            )
        }

        composable<Route.ReviewItem> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ReviewItem>()
            ReviewItemScreen(
                transactionId = route.transactionId,
                onBack = { navController.popBackStack() },
                onNextItem = { nextId ->
                    navController.navigate(Route.ReviewItem(nextId)) {
                        popUpTo(Route.ReviewInbox)
                    }
                }
            )
        }

        // Receipts
        composable<Route.Receipts> {
            ReceiptsScreen(
                onNewReceipt = { navController.navigate(Route.ReceiptCapture(null)) },
                onOpenSplit = { id -> navController.navigate(Route.ReceiptSplit(id)) }
            )
        }

        composable<Route.ReceiptCapture> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ReceiptCapture>()
            ReceiptCaptureScreen(
                existingSplitId = route.splitId,
                onBack = { navController.popBackStack() },
                onCaptureComplete = { splitId ->
                    navController.navigate(Route.ReceiptSplit(splitId)) {
                        popUpTo(Route.Receipts)
                    }
                }
            )
        }

        composable<Route.ReceiptSplit> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ReceiptSplit>()
            ReceiptSplitScreen(
                splitId = route.splitId,
                onBack = { navController.popBackStack() },
                onViewSettlement = {
                    navController.navigate(Route.ReceiptSettlement(route.splitId))
                }
            )
        }

        // Categories & Rules
        composable<Route.Categories> {
            CategoriesScreen(
                onBack = { navController.popBackStack() },
                onEditCategory = { id -> navController.navigate(Route.CategoryEdit(id)) },
                onAddCategory = { navController.navigate(Route.CategoryEdit(null)) }
            )
        }

        composable<Route.Rules> {
            RulesScreen(
                onBack = { navController.popBackStack() },
                onEditRule = { id -> navController.navigate(Route.RuleEdit(id)) },
                onAddRule = { navController.navigate(Route.RuleEdit(null)) }
            )
        }

        // Settings
        composable<Route.Settings> {
            SettingsScreen(
                onNavigateToCategories = { navController.navigate(Route.Categories) },
                onNavigateToRules = { navController.navigate(Route.Rules) },
                onNavigateToAccounts = { navController.navigate(Route.AccountSettings) },
                onNavigateToData = { navController.navigate(Route.DataManagement) },
                onNavigateToAbout = { navController.navigate(Route.About) }
            )
        }

        composable<Route.AccountSettings> {
            AccountSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.DataManagement> {
            DataManagementScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
```

### Step 3: Bottom Navigation Bar

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/navigation/BottomNavBar.kt
package com.ledgerlens.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun LedgerLensBottomNavBar(
    navController: NavController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        BottomNavItem.entries.forEach { item ->
            val selected = currentRoute == item.route::class.qualifiedName

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // Pop up to start destination to avoid building up a large stack
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}
```

### Step 4: Main App Scaffold

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/LedgerLensApp.kt
package com.ledgerlens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.ledgerlens.ui.navigation.*
import com.ledgerlens.ui.theme.LedgerLensTheme

@Composable
fun LedgerLensApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if bottom nav should be shown
    val showBottomNav = remember(currentRoute) {
        currentRoute in listOf(
            Route.Dashboard::class.qualifiedName,
            Route.Transactions::class.qualifiedName,
            Route.Import::class.qualifiedName,
            Route.Settings::class.qualifiedName
        )
    }

    LedgerLensTheme {
        Scaffold(
            bottomBar = {
                if (showBottomNav) {
                    LedgerLensBottomNavBar(navController = navController)
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LedgerLensNavGraph(navController = navController)
            }
        }
    }
}
```

### Step 5: Navigation Extensions

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/navigation/NavExtensions.kt
package com.ledgerlens.ui.navigation

import androidx.navigation.NavController

/**
 * Navigate with result callback.
 */
fun <T> NavController.navigateForResult(
    route: Route,
    key: String,
    onResult: (T?) -> Unit
) {
    currentBackStackEntry?.savedStateHandle?.let { handle ->
        handle.getLiveData<T>(key).observeForever { result ->
            onResult(result)
            handle.remove<T>(key)
        }
    }
    navigate(route)
}

/**
 * Set result for previous screen.
 */
fun <T> NavController.setResult(key: String, value: T) {
    previousBackStackEntry?.savedStateHandle?.set(key, value)
}

/**
 * Navigate and clear back stack.
 */
fun NavController.navigateAndClearStack(route: Route) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
    }
}

/**
 * Check if can navigate back.
 */
fun NavController.canGoBack(): Boolean {
    return previousBackStackEntry != null
}
```

---

## Acceptance Criteria

- [ ] Bottom navigation works with 4 tabs
- [ ] Navigation state preserved on tab switch
- [ ] Deep navigation within tabs works
- [ ] Back button behavior correct
- [ ] Type-safe route parameters work
- [ ] Navigation results between screens work
- [ ] Bottom nav hidden on full-screen flows

---

## Testing

### Unit Tests
```kotlin
class NavigationTest {
    @Test
    fun `bottom nav items have unique routes`() {
        val routes = BottomNavItem.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
```

### UI Tests
- Tab switching maintains state
- Deep link navigation
- Back button behavior

---

## Estimated Complexity

**Medium** - Standard Compose Navigation with multiple nested graphs.

