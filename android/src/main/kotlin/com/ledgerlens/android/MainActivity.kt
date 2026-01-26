package com.ledgerlens.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ledgerlens.data.di.appModule
import com.ledgerlens.data.di.createAndroidDatabaseModule
import com.ledgerlens.ui.app.LedgerLensAppWithNavHost
import com.ledgerlens.ui.app.LocalNavigationActions
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

        // Initialize Koin dependency injection
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger(Level.INFO)
                androidContext(this@MainActivity)
                modules(
                    createAndroidDatabaseModule(this@MainActivity),
                    appModule
                )
            }
        }

        setContent {
            LedgerLensTheme {
                val navigationController = remember { NavigationController() }

                // ViewModels are registered as Koin factories, so make them stable per-activity.
                val dashboardVm = remember { GlobalContext.get().get<DashboardViewModel>() }
                val transactionsVm = remember { GlobalContext.get().get<TransactionsViewModel>() }
                val importVm = remember { GlobalContext.get().get<ImportViewModel>() }
                val reviewVm = remember { GlobalContext.get().get<ReviewViewModel>() }
                val categoriesVm = remember { GlobalContext.get().get<CategoriesViewModel>() }
                val settingsVm = remember { GlobalContext.get().get<SettingsViewModel>() }

                val context = LocalContext.current

                // File picker launcher for Android
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let { selectedUri ->
                        val contentResolver = context.contentResolver
                        val fileName = contentResolver.query(
                            selectedUri,
                            null,
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex >= 0) {
                                cursor.getString(nameIndex)
                            } else {
                                selectedUri.lastPathSegment ?: "file"
                            }
                        } ?: (selectedUri.lastPathSegment ?: "file")

                        // Infer file type from extension
                        val extension = fileName.substringAfterLast('.', "").lowercase()
                        val detectedFileType = ImportFileType.fromExtension(extension)
                            ?: importVm.selectedFileType.value

                        // Update file type if detected
                        if (detectedFileType != importVm.selectedFileType.value) {
                            importVm.selectFileType(detectedFileType)
                        }

                        // Convert URI to file path for the ViewModel
                        // Note: On Android, we need to handle URI access properly
                        // For now, we'll pass the URI string and let the ViewModel handle it
                        val filePath = selectedUri.toString()
                        importVm.onFileSelected(
                            filePath = filePath,
                            fileName = fileName
                        )
                    }
                }

                LedgerLensAppWithNavHost(
                    navigationController = navigationController,
                    screenRegistry = screenRegistry {
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
                                            // Build MIME type based on file type
                                            // Use wildcard pattern to allow all files, then filter by extension
                                            val mimeType = when (fileType) {
                                                ImportFileType.CSV -> "*/*" // Android file picker will show all files
                                                ImportFileType.PDF -> "application/pdf"
                                            }
                                            filePickerLauncher.launch(mimeType)
                                        }
                                    )
                                }
                            }
                        }

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

                        screen(Screen.Categories) {
                            CategoriesScreen(
                                viewModel = categoriesVm,
                                onNavigateToRules = { /* TODO: add Rules screen route */ }
                            )
                        }

                        screen(Screen.Settings) {
                            SettingsScreen(
                                viewModel = settingsVm,
                                onNavigateToBackup = { /* TODO: add Backup screen route */ },
                                onNavigateToSecurity = { /* TODO: add Security screen route */ }
                            )
                        }
                    }
                )
            }
        }
    }
}
