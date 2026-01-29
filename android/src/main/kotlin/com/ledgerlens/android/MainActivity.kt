package com.ledgerlens.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ledgerlens.data.di.appModule
import com.ledgerlens.data.di.createAndroidDatabaseModule
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
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initKoinIfNeeded()

        setContent {
            ledgerLensAndroidApp()
        }
    }

    private fun initKoinIfNeeded() {
        if (GlobalContext.getOrNull() != null) return

        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@MainActivity)
            modules(
                createAndroidDatabaseModule(this@MainActivity),
                appModule
            )
        }
    }
}

@Composable
private fun ledgerLensAndroidApp() {
    LedgerLensTheme {
        val navigationController = remember { NavigationController() }
        val viewModels = rememberAndroidViewModels()
        val onFilePick = rememberAndroidFilePicker(importVm = viewModels.importVm)

        LedgerLensAppWithNavHost(
            navigationController = navigationController,
            screenRegistry = screenRegistry {
                registerDashboardScreen(dashboardVm = viewModels.dashboardVm)
                registerTransactionsScreen(transactionsVm = viewModels.transactionsVm)
                registerImportScreen(importVm = viewModels.importVm, onFilePick = onFilePick)
                registerReviewScreen(reviewVm = viewModels.reviewVm)
                registerCategoriesScreen(categoriesVm = viewModels.categoriesVm)
                registerSettingsScreen(settingsVm = viewModels.settingsVm)
            }
        )
    }
}

private data class AndroidViewModels(
    val dashboardVm: DashboardViewModel,
    val transactionsVm: TransactionsViewModel,
    val importVm: ImportViewModel,
    val reviewVm: ReviewViewModel,
    val categoriesVm: CategoriesViewModel,
    val settingsVm: SettingsViewModel
)

@Composable
private fun rememberAndroidViewModels(): AndroidViewModels {
    return AndroidViewModels(
        dashboardVm = remember { GlobalContext.get().get<DashboardViewModel>() },
        transactionsVm = remember { GlobalContext.get().get<TransactionsViewModel>() },
        importVm = remember { GlobalContext.get().get<ImportViewModel>() },
        reviewVm = remember { GlobalContext.get().get<ReviewViewModel>() },
        categoriesVm = remember { GlobalContext.get().get<CategoriesViewModel>() },
        settingsVm = remember { GlobalContext.get().get<SettingsViewModel>() }
    )
}

@Composable
private fun rememberAndroidFilePicker(importVm: ImportViewModel): (ImportFileType) -> Unit {
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri -> handleFilePicked(context, importVm, selectedUri) }
    }

    return { fileType ->
        // Build MIME type based on file type.
        // Use wildcard pattern to allow all files, then filter by extension.
        val mimeType = when (fileType) {
            ImportFileType.CSV -> "*/*" // Android file picker will show all files
            ImportFileType.PDF -> "application/pdf"
        }
        filePickerLauncher.launch(mimeType)
    }
}

private fun handleFilePicked(context: Context, importVm: ImportViewModel, uri: Uri) {
    val fileName = resolveFileName(context, uri)

    // Infer file type from extension.
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val detectedFileType = ImportFileType.fromExtension(extension)
        ?: importVm.selectedFileType.value

    if (detectedFileType != importVm.selectedFileType.value) {
        importVm.selectFileType(detectedFileType)
    }

    // Convert URI to file path for the ViewModel.
    // Note: On Android, we need to handle URI access properly.
    // For now, pass the URI string and let the ViewModel handle it.
    importVm.onFileSelected(
        filePath = uri.toString(),
        fileName = fileName
    )
}

private fun resolveFileName(context: Context, uri: Uri): String {
    val contentResolver = context.contentResolver
    return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            cursor.getString(nameIndex)
        } else {
            uri.lastPathSegment ?: "file"
        }
    } ?: (uri.lastPathSegment ?: "file")
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

private fun ScreenRegistryBuilder.registerImportScreen(
    importVm: ImportViewModel,
    onFilePick: (ImportFileType) -> Unit
) {
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
                    onFilePick = onFilePick
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
