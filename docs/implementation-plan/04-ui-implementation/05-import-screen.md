# 05: Import Screen

## Overview

Implement the import wizard for selecting files, showing import progress, and reviewing imported transactions.

---

## Implementation Steps

### Step 1: Import State

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/import/ImportState.kt
package com.ledgerlens.ui.screens.import

data class ImportHomeState(
    val recentImports: List<ImportJobSummary> = emptyList(),
    val supportedFormats: List<SupportedFormat> = emptyList(),
    val isLoading: Boolean = false
)

data class ImportJobSummary(
    val jobId: String,
    val fileName: String,
    val importDate: Instant,
    val transactionCount: Int,
    val status: ImportJobStatus
)

data class SupportedFormat(
    val name: String,
    val extensions: List<String>,
    val icon: ImageVector
)

data class ImportProgressState(
    val jobId: String,
    val status: ImportJobStatus = ImportJobStatus.PENDING,
    val currentStep: ImportStep = ImportStep.PARSING,
    val progress: Float = 0f,
    val transactionsParsed: Int = 0,
    val transactionsTotal: Int = 0,
    val duplicatesFound: Int = 0,
    val warnings: List<String> = emptyList(),
    val error: String? = null
)

enum class ImportStep(val label: String) {
    PARSING("Parsing file..."),
    NORMALIZING("Normalizing merchants..."),
    DEDUPLICATING("Finding duplicates..."),
    CATEGORIZING("Categorizing transactions..."),
    COMPLETE("Import complete!")
}

data class ImportReviewState(
    val jobId: String,
    val isLoading: Boolean = true,
    val transactions: List<ImportedTransactionReview> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val warnings: List<ImportWarning> = emptyList(),
    val stats: ImportStats? = null
)

data class ImportedTransactionReview(
    val id: String,
    val merchantNormalized: String,
    val amount: Money,
    val date: LocalDate,
    val categoryName: String?,
    val confidence: Float?,
    val isDuplicate: Boolean,
    val hasWarning: Boolean
)

data class DuplicateGroup(
    val fingerprint: String,
    val transactions: List<ImportedTransactionReview>,
    val resolution: DuplicateResolution = DuplicateResolution.PENDING
)

enum class DuplicateResolution {
    PENDING, KEEP_ALL, KEEP_FIRST, SKIP_ALL
}

data class ImportStats(
    val totalParsed: Int,
    val newTransactions: Int,
    val duplicatesSkipped: Int,
    val categorized: Int,
    val needsReview: Int
)
```

### Step 2: Import ViewModel

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/ImportViewModel.kt
package com.ledgerlens.viewmodel

class ImportViewModel(
    private val importService: ImportService,
    private val importJobRepository: ImportJobRepository
) : BaseViewModel() {

    private val _homeState = MutableStateFlow(ImportHomeState(isLoading = true))
    val homeState: StateFlow<ImportHomeState> = _homeState.asStateFlow()

    init {
        loadRecentImports()
    }

    fun loadRecentImports() {
        viewModelScope.launch {
            _homeState.update { it.copy(isLoading = true) }

            val imports = importJobRepository.getRecent(limit = 10)
            val summaries = imports.map { job ->
                ImportJobSummary(
                    jobId = job.id,
                    fileName = job.fileName,
                    importDate = job.createdAt,
                    transactionCount = job.transactionCount ?: 0,
                    status = job.status
                )
            }

            _homeState.update {
                it.copy(
                    isLoading = false,
                    recentImports = summaries,
                    supportedFormats = getSupportedFormats()
                )
            }
        }
    }

    suspend fun startImport(fileUri: String, fileName: String): String {
        val job = importService.createImportJob(
            fileUri = fileUri,
            fileName = fileName
        )
        return job.id
    }

    private fun getSupportedFormats(): List<SupportedFormat> = listOf(
        SupportedFormat("PDF Statement", listOf("pdf"), Icons.Default.PictureAsPdf),
        SupportedFormat("CSV Export", listOf("csv"), Icons.Default.TableChart),
        SupportedFormat("OFX/QFX", listOf("ofx", "qfx"), Icons.Default.AccountBalance)
    )
}

class ImportProgressViewModel(
    private val jobId: String,
    private val importService: ImportService
) : BaseViewModel() {

    private val _state = MutableStateFlow(ImportProgressState(jobId = jobId))
    val state: StateFlow<ImportProgressState> = _state.asStateFlow()

    init {
        observeProgress()
        startImport()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            importService.observeJobProgress(jobId).collect { progress ->
                _state.update {
                    it.copy(
                        status = progress.status,
                        currentStep = progress.step,
                        progress = progress.progress,
                        transactionsParsed = progress.transactionsParsed,
                        transactionsTotal = progress.transactionsTotal,
                        duplicatesFound = progress.duplicatesFound
                    )
                }
            }
        }
    }

    private fun startImport() {
        viewModelScope.launch {
            try {
                importService.processJob(jobId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        status = ImportJobStatus.FAILED,
                        error = e.message
                    )
                }
            }
        }
    }

    fun retry() {
        _state.update {
            it.copy(
                status = ImportJobStatus.PENDING,
                error = null,
                progress = 0f
            )
        }
        startImport()
    }
}
```

### Step 3: Import Home Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/import/ImportScreen.kt
package com.ledgerlens.ui.screens.import

@Composable
fun ImportScreen(
    onSelectFile: () -> Unit,
    onNavigateToJob: (String) -> Unit,
    viewModel: ImportViewModel = koinViewModel()
) {
    val state by viewModel.homeState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Import") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSelectFile,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Import File") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Supported formats info
            item {
                SupportedFormatsCard(formats = state.supportedFormats)
            }

            // Recent imports
            if (state.recentImports.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Imports",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(state.recentImports) { import ->
                    ImportJobCard(
                        import = import,
                        onClick = { onNavigateToJob(import.jobId) }
                    )
                }
            } else if (!state.isLoading) {
                item {
                    EmptyState(
                        icon = Icons.Default.Upload,
                        title = "No imports yet",
                        message = "Import your bank statements to get started"
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportedFormatsCard(formats: List<SupportedFormat>) {
    LedgerCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Supported Formats",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                formats.forEach { format ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(format.icon, null)
                        Text(
                            text = format.name,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportJobCard(
    import: ImportJobSummary,
    onClick: () -> Unit
) {
    LedgerCard(onClick = onClick) {
        ListItem(
            headlineContent = { Text(import.fileName) },
            supportingContent = {
                Text("${import.transactionCount} transactions • ${import.importDate.formatRelative()}")
            },
            leadingContent = {
                when (import.status) {
                    ImportJobStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, null, tint = Income)
                    ImportJobStatus.FAILED -> Icon(Icons.Default.Error, null, tint = Expense)
                    else -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            trailingContent = {
                Icon(Icons.Default.ChevronRight, null)
            }
        )
    }
}
```

### Step 4: Import Progress Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/import/ImportProgressScreen.kt
package com.ledgerlens.ui.screens.import

@Composable
fun ImportProgressScreen(
    jobId: String,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: ImportProgressViewModel = koinViewModel { parametersOf(jobId) }
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.status) {
        if (state.status == ImportJobStatus.COMPLETED) {
            delay(500) // Brief pause to show completion
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importing...") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                state.error != null -> {
                    ErrorState(
                        message = state.error!!,
                        onRetry = viewModel::retry
                    )
                }
                state.status == ImportJobStatus.COMPLETED -> {
                    ImportCompleteState(state = state)
                }
                else -> {
                    ImportingState(state = state)
                }
            }
        }
    }
}

@Composable
private fun ImportingState(state: ImportProgressState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CircularProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp
        )

        Text(
            text = state.currentStep.label,
            style = MaterialTheme.typography.titleMedium
        )

        if (state.transactionsTotal > 0) {
            Text(
                text = "${state.transactionsParsed} of ${state.transactionsTotal} transactions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.duplicatesFound > 0) {
            Text(
                text = "${state.duplicatesFound} potential duplicates found",
                style = MaterialTheme.typography.bodySmall,
                color = Pending
            )
        }
    }
}

@Composable
private fun ImportCompleteState(state: ImportProgressState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Income,
            modifier = Modifier.size(80.dp)
        )

        Text(
            text = "Import Complete!",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "${state.transactionsParsed} transactions imported",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
```

### Step 5: Import Review Screen

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/import/ImportReviewScreen.kt
package com.ledgerlens.ui.screens.import

@Composable
fun ImportReviewScreen(
    jobId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ImportReviewViewModel = koinViewModel { parametersOf(jobId) }
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onDone) {
                        Text("Done")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Stats summary
            state.stats?.let { stats ->
                ImportStatsSummary(stats = stats)
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All (${state.transactions.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Duplicates (${state.duplicates.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Warnings (${state.warnings.size})") }
                )
            }

            // Tab content
            when (selectedTab) {
                0 -> TransactionReviewList(
                    transactions = state.transactions,
                    onTransactionClick = { /* Show detail */ }
                )
                1 -> DuplicateReviewList(
                    duplicates = state.duplicates,
                    onResolutionChange = viewModel::resolveDuplicate
                )
                2 -> WarningsList(warnings = state.warnings)
            }
        }
    }
}

@Composable
private fun ImportStatsSummary(stats: ImportStats) {
    LedgerCard(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "New", value = stats.newTransactions.toString())
            StatItem(label = "Duplicates", value = stats.duplicatesSkipped.toString())
            StatItem(label = "Categorized", value = "${stats.categorized}")
            StatItem(label = "Review", value = stats.needsReview.toString())
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

---

## Acceptance Criteria

- [ ] File picker shows supported formats
- [ ] Import progress updates in real-time
- [ ] Steps shown (parsing, normalizing, categorizing)
- [ ] Duplicate count displayed
- [ ] Error handling with retry option
- [ ] Review screen shows all imported transactions
- [ ] Duplicate resolution options work
- [ ] Warnings displayed clearly
- [ ] Stats summary accurate

---

## Estimated Complexity

**Medium** - Multi-step wizard with progress tracking.

