package com.ledgerlens.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ledgerlens.data.di.appModule
import com.ledgerlens.data.di.createDesktopDatabaseModule
import com.ledgerlens.ui.app.LedgerLensAppWithNavHost
import com.ledgerlens.ui.app.LocalNavigationActions
import com.ledgerlens.ui.app.ScreenRegistryBuilder
import com.ledgerlens.ui.app.screenRegistry
import com.ledgerlens.ui.navigation.NavigationController
import com.ledgerlens.ui.navigation.Screen
import com.ledgerlens.ui.screens.categories.CategoriesScreen
import com.ledgerlens.ui.screens.dashboard.DashboardScreen
import com.ledgerlens.ui.screens.import.ImportProgressScreen
import com.ledgerlens.ui.screens.import.ImportResultScreen
import com.ledgerlens.ui.screens.import.ImportScreen
import com.ledgerlens.ui.screens.resources.FinancialResourcesScreen
import com.ledgerlens.ui.screens.resources.IndexFundDeepDiveScreen
import com.ledgerlens.ui.screens.resources.SavingsComparisonScreen
import com.ledgerlens.ui.screens.review.ReviewDetailScreen
import com.ledgerlens.ui.screens.review.ReviewInboxScreen
import com.ledgerlens.ui.screens.settings.SettingsScreen
import com.ledgerlens.ui.screens.transactions.TransactionsScreen
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.viewmodels.categories.CategoriesViewModel
import com.ledgerlens.ui.viewmodels.dashboard.DashboardViewModel
import com.ledgerlens.ui.viewmodels.import.ImportFileType
import com.ledgerlens.ui.viewmodels.import.ImportUiState
import com.ledgerlens.ui.viewmodels.import.ImportViewModel
import com.ledgerlens.ui.viewmodels.review.ReviewViewModel
import com.ledgerlens.ui.viewmodels.settings.SettingsViewModel
import com.ledgerlens.ui.viewmodels.transactions.TransactionsViewModel
import java.awt.FileDialog
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext

fun main() {
    initKoin()
    startDesktopApp()
}

private fun initKoin() {
    startKoin {
        modules(
            createDesktopDatabaseModule(),
            appModule
        )
    }
}

private fun startDesktopApp() = application {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "LedgerLens",
        state = windowState
    ) {
        ledgerLensDesktopWindow(awtWindow = window)
    }
}

@Composable
private fun ledgerLensDesktopWindow(awtWindow: java.awt.Window) {
    val navigationController = remember { NavigationController() }
    val viewModels = rememberDesktopViewModels()

    // Desktop convenience: drag & drop a file anywhere onto the window to select it for import.
    setupDesktopFileDropTarget(
        awtWindow = awtWindow,
        navigationController = navigationController,
        importVm = viewModels.importVm
    )

    LedgerLensTheme {
        LedgerLensAppWithNavHost(
            navigationController = navigationController,
            screenRegistry = screenRegistry {
                registerDashboardScreen(dashboardVm = viewModels.dashboardVm)
                registerTransactionsScreen(transactionsVm = viewModels.transactionsVm)
                registerImportScreen(importVm = viewModels.importVm, awtWindow = awtWindow)
                registerReviewScreen(reviewVm = viewModels.reviewVm)
                registerCategoriesScreen(categoriesVm = viewModels.categoriesVm)
                registerSettingsScreen(settingsVm = viewModels.settingsVm)
                registerFinancialResourcesScreens()
            }
        )
    }
}

private data class DesktopViewModels(
    val dashboardVm: DashboardViewModel,
    val transactionsVm: TransactionsViewModel,
    val importVm: ImportViewModel,
    val reviewVm: ReviewViewModel,
    val categoriesVm: CategoriesViewModel,
    val settingsVm: SettingsViewModel
)

@Composable
private fun rememberDesktopViewModels(): DesktopViewModels {
    return DesktopViewModels(
        dashboardVm = remember { GlobalContext.get().get<DashboardViewModel>() },
        transactionsVm = remember { GlobalContext.get().get<TransactionsViewModel>() },
        importVm = remember { GlobalContext.get().get<ImportViewModel>() },
        reviewVm = remember { GlobalContext.get().get<ReviewViewModel>() },
        categoriesVm = remember { GlobalContext.get().get<CategoriesViewModel>() },
        settingsVm = remember { GlobalContext.get().get<SettingsViewModel>() }
    )
}

@Composable
private fun setupDesktopFileDropTarget(
    awtWindow: java.awt.Window,
    navigationController: NavigationController,
    importVm: ImportViewModel
) {
    DisposableEffect(awtWindow) {
        val dropTarget = object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.rejectDrop()
                        return
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    @Suppress("UNCHECKED_CAST")
                    val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    val first = files.firstOrNull()
                    if (first != null) {
                        navigationController.navigateToRoot(Screen.Import)
                        importVm.selectFileType(first.inferImportFileType() ?: importVm.selectedFileType.value)
                        importVm.onFileSelected(
                            filePath = first.absolutePath,
                            fileName = first.name
                        )
                    }
                    dtde.dropComplete(true)
                } catch (_: Exception) {
                    dtde.dropComplete(false)
                }
            }
        }

        awtWindow.dropTarget = DropTarget(awtWindow, dropTarget)
        onDispose {
            awtWindow.dropTarget = null
        }
    }
}

private fun ScreenRegistryBuilder.registerDashboardScreen(dashboardVm: DashboardViewModel) {
    screen(Screen.Dashboard) {
        val nav = LocalNavigationActions.current
        DashboardScreen(
            viewModel = dashboardVm,
            onNavigateToImport = { nav.navigateTo(Screen.Import) },
            onNavigateToTransactions = { nav.navigateTo(Screen.Transactions) },
            onNavigateToReview = { nav.navigateTo(Screen.Review) },
            onNavigateToTransactionDetail = { id ->
                nav.navigateToRoute(Screen.TransactionDetail.createRoute(id))
            },
            onNavigateToCategory = { id ->
                nav.navigateToRoute(Screen.CategoryDetail.createRoute(id))
            },
            onNavigateToResources = { nav.navigateTo(Screen.FinancialResources) }
        )
    }
}

private fun ScreenRegistryBuilder.registerTransactionsScreen(transactionsVm: TransactionsViewModel) {
    screen(Screen.Transactions) {
        val nav = LocalNavigationActions.current
        TransactionsScreen(
            viewModel = transactionsVm,
            showBackButton = false,
            onNavigateBack = { nav.navigateBack() },
            onNavigateToDetail = { id ->
                nav.navigateToRoute(Screen.TransactionDetail.createRoute(id))
            }
        )
    }
}

private fun ScreenRegistryBuilder.registerImportScreen(importVm: ImportViewModel, awtWindow: java.awt.Window) {
    screen(Screen.Import) {
        val nav = LocalNavigationActions.current
        val importState = importVm.uiState.collectAsState().value

        // Keep Import as a single route; swap sub-screens by state.
        when (importState) {
            is ImportUiState.Importing -> {
                ImportProgressScreen(
                    viewModel = importVm,
                    onCancel = { importVm.cancelImport() },
                    onComplete = { /* handled by state below */ }
                )
            }

            is ImportUiState.Complete -> {
                ImportResultScreen(
                    viewModel = importVm,
                    onGoToReview = { nav.navigateTo(Screen.Review) },
                    onGoToTransactions = { nav.navigateTo(Screen.Transactions) },
                    onImportAnother = { importVm.resetToIdle() }
                )
            }

            else -> {
                ImportScreen(
                    viewModel = importVm,
                    onNavigateToProgress = { /* state-driven */ },
                    onNavigateToResult = { /* state-driven */ },
                    onFilePick = { fileType ->
                        val parentFrame = awtWindow as? java.awt.Frame
                            ?: return@ImportScreen

                        val picked = showNativeOpenDialog(
                            parent = parentFrame,
                            fileType = fileType
                        ) ?: return@ImportScreen

                        // If the user picked a different extension than the current toggle,
                        // auto-switch the UI toggle to match.
                        picked.inferImportFileType()?.let { detected ->
                            importVm.selectFileType(detected)
                        }

                        importVm.onFileSelected(
                            filePath = picked.absolutePath,
                            fileName = picked.name
                        )
                    }
                )
            }
        }
    }
}

private fun ScreenRegistryBuilder.registerReviewScreen(reviewVm: ReviewViewModel) {
    screen(Screen.Review) {
        val nav = LocalNavigationActions.current
        val selected = reviewVm.selectedItem.collectAsState().value

        // Keep Review as a single route; swap inbox/detail by selection.
        if (selected == null) {
            ReviewInboxScreen(
                viewModel = reviewVm,
                onItemClick = { item -> reviewVm.selectItem(item) },
                onNavigateToCategories = { nav.navigateTo(Screen.Categories) }
            )
        } else {
            ReviewDetailScreen(
                viewModel = reviewVm,
                onNavigateBack = { /* selection clearing handled in screen */ },
                onNavigateToCategories = { nav.navigateTo(Screen.Categories) }
            )
        }
    }
}

private fun ScreenRegistryBuilder.registerCategoriesScreen(categoriesVm: CategoriesViewModel) {
    screen(Screen.Categories) {
        CategoriesScreen(
            viewModel = categoriesVm,
            onNavigateToRules = { /* FIXME: add Rules screen route */ }
        )
    }
}

private fun ScreenRegistryBuilder.registerSettingsScreen(settingsVm: SettingsViewModel) {
    screen(Screen.Settings) {
        SettingsScreen(
            viewModel = settingsVm,
            onNavigateToBackup = { /* FIXME: add Backup screen route */ },
            onNavigateToSecurity = { /* FIXME: add Security screen route */ }
        )
    }
}

private fun ScreenRegistryBuilder.registerFinancialResourcesScreens() {
    // Financial Resources screens (StitchUI)
    screen(Screen.FinancialResources) {
        val nav = LocalNavigationActions.current
        FinancialResourcesScreen(
            onNavigateToIndexFunds = { nav.navigateTo(Screen.IndexFundDeepDive) },
            onNavigateToSavingsComparison = { nav.navigateTo(Screen.SavingsComparison) },
            onNavigateToConcept = { conceptId ->
                nav.navigateToRoute(Screen.ConceptExplorer.createRoute(conceptId))
            }
        )
    }

    screen(Screen.IndexFundDeepDive) {
        val nav = LocalNavigationActions.current
        IndexFundDeepDiveScreen(
            onNavigateBack = { nav.navigateBack() },
            onNavigateToResources = { nav.navigateTo(Screen.FinancialResources) }
        )
    }

    screen(Screen.SavingsComparison) {
        val nav = LocalNavigationActions.current
        SavingsComparisonScreen(
            onNavigateBack = { nav.navigateBack() },
            onSelectOption = { /* Handle option selection */ }
        )
    }
}

private fun showNativeOpenDialog(parent: java.awt.Frame, fileType: ImportFileType): File? {
    val downloadsDir = File(System.getProperty("user.home"), "Downloads").takeIf { it.exists() }
    val dialog = FileDialog(parent, "Select ${fileType.displayName}", FileDialog.LOAD).apply {
        // Start in Downloads when available (helps with “just downloaded” files).
        directory = downloadsDir?.absolutePath
        // Hint pattern (Windows honors this in many cases)
        file = "*.${fileType.extensions.firstOrNull() ?: "*"}"
        isVisible = true
    }

    val chosenName = dialog.file ?: return null
    val chosenDir: String = dialog.directory ?: return null
    return File(chosenDir, chosenName)
}

private fun File.inferImportFileType(): ImportFileType? {
    val ext = extension.lowercase().removePrefix(".")
    return ImportFileType.fromExtension(ext)
}
