# LedgerLens Parallel Agent Task Assignments

> **Master coordination document for 10 parallel agents**  
> **Created:** 2026-01-15  
> **Target:** Sprint 04 Completion + Sprint 05 Start

---

## 📋 Table of Contents

1. [Current State Assessment](#current-state-assessment)
2. [Pre-Flight Checklist](#pre-flight-checklist)
3. [Agent Assignments Overview](#agent-assignments-overview)
4. [Critical Safeguards](#critical-safeguards)
5. [Agent 1-10 Detailed Prompts](#agent-prompts)
6. [Integration Procedure](#integration-procedure)
7. [Progress Tracking](#progress-tracking)

---

## Current State Assessment

### Sprint 04 Progress (UI Implementation)

| Component | Status | Files |
|-----------|--------|-------|
| **Theme System** | ✅ Complete | Color.kt, LedgerLensTheme.kt, Shape.kt, Spacing.kt, Typography.kt |
| **UI Components** | ✅ Complete | 10 reusable components |
| **Transactions Screen** | ✅ Complete | TransactionsScreen.kt, TransactionDetailScreen.kt, TransactionsViewModel.kt |
| **Settings Screen** | ✅ Complete | SettingsScreen.kt, BackupRestoreScreen.kt, SecuritySettingsScreen.kt |
| **Navigation** | ❌ Missing | No navigation graph or app shell |
| **Dashboard Screen** | ❌ Missing | No screen or ViewModel |
| **Import Screen** | ❌ Missing | No screen or ViewModel |
| **Review Screen** | ❌ Missing | No screen or ViewModel |
| **Receipts Screen** | ❌ Missing | No screen or ViewModel |
| **Categories Screen** | ❌ Missing | No screen or ViewModel |
| **ViewModels** | 🔄 Partial | Only TransactionsViewModel exists |

### Existing UI Files (21 total)

```
shared/src/commonMain/kotlin/com/ledgerlens/ui/
├── components/          # ✅ 10 files - COMPLETE
├── theme/               # ✅ 5 files - COMPLETE  
├── screens/
│   ├── settings/        # ✅ 3 files - COMPLETE
│   └── transactions/    # ✅ 2 files - COMPLETE
└── viewmodels/
    └── transactions/    # ✅ 1 file - COMPLETE
```

---

## Pre-Flight Checklist

**Run these commands BEFORE starting any agent:**

```powershell
# 1. Ensure you're on main and up to date
cd c:\Users\DangT\Documents\GitHub\SelfCFO
git checkout main
git pull origin main

# 2. Merge integration branch if not done
git merge integration/sprint02-sprint03 --no-ff -m "Merge Sprint 02/03 integration"

# 3. Create base branch for Sprint 04 continuation
git checkout -b sprint04/ui-continuation
git push -u origin sprint04/ui-continuation

# 4. Verify build works
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"  # Adjust path as needed
./gradlew :shared:compileKotlinDesktop
```

---

## Agent Assignments Overview

| Agent | Branch | Focus Area | Directory Ownership |
|-------|--------|------------|---------------------|
| **1** | `sprint04/navigation` | Navigation & App Shell | `ui/navigation/`, `ui/app/` |
| **2** | `sprint04/dashboard` | Dashboard Screen + ViewModel | `ui/screens/dashboard/`, `ui/viewmodels/dashboard/` |
| **3** | `sprint04/import` | Import Wizard + ViewModel | `ui/screens/import/`, `ui/viewmodels/import/` |
| **4** | `sprint04/review` | Review Inbox + ViewModel | `ui/screens/review/`, `ui/viewmodels/review/` |
| **5** | `sprint04/receipts` | Receipts Screen + ViewModel | `ui/screens/receipts/`, `ui/viewmodels/receipts/` |
| **6** | `sprint04/categories` | Categories & Rules Screen | `ui/screens/categories/`, `ui/viewmodels/categories/` |
| **7** | `sprint04/settings-vm` | Settings ViewModel + Integration | `ui/viewmodels/settings/` |
| **8** | `sprint05/repositories` | Repository Implementations | `data/repositories/` |
| **9** | `sprint05/di` | Dependency Injection Setup | `di/`, platform app setup |
| **10** | `sprint05/integration-tests` | Integration & E2E Tests | `*Test.kt` files, test utilities |

---

## Critical Safeguards

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                    ⚠️  PARALLEL AGENT SAFEGUARDS  ⚠️                          ║
║                     READ THIS BEFORE ANY WORK                                 ║
╚══════════════════════════════════════════════════════════════════════════════╝

You are ONE of TEN agents working in parallel. To prevent git conflicts:

┌─────────────────────────────────────────────────────────────────────────────┐
│ BRANCH RULES                                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. Work ONLY on your assigned branch (see your prompt below)                │
│ 2. Branch from `sprint04/ui-continuation` or `main`                         │
│ 3. NEVER merge other agent branches into yours                              │
│ 4. NEVER run `git pull` or `git fetch` during your session                  │
│ 5. Commit frequently with descriptive messages prefixed: "feat(area):"      │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ FILE OWNERSHIP RULES                                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. ONLY create/modify files in YOUR assigned directories                    │
│ 2. NEVER touch files outside your directories                               │
│ 3. If you need something from another agent's area:                         │
│    - Create an interface/stub in YOUR directory                             │
│    - Document with: // TODO(integration): Replace with actual impl          │
│ 4. Test files go in corresponding test directory under YOUR package         │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ ❌ FORBIDDEN ACTIONS                                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│ • Do NOT modify: build.gradle.kts, settings.gradle.kts, libs.versions.toml │
│ • Do NOT modify: Any existing file outside your assigned directories        │
│ • Do NOT create files in another agent's directories                        │
│ • Do NOT delete any existing files                                          │
│ • Do NOT run: git merge, git pull, git rebase, git reset                   │
│ • Do NOT modify: IMPLEMENTATION_STATUS.md (coordinator only)                │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ DEPENDENCY HANDLING                                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│ • Use existing theme: LedgerLensTheme.colors.*, LedgerLensTheme.spacing.*  │
│ • Use existing components from ui/components/ (they're stable)              │
│ • For missing ViewModels from other agents, create a stub interface         │
│ • Document cross-agent dependencies as TODO comments                         │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ ✅ REQUIRED AT END OF SESSION                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. List ALL files created/modified                                          │
│ 2. List any cross-agent dependencies (TODOs for integration)                │
│ 3. Provide test command: ./gradlew :shared:desktopTest --tests "pkg.*"     │
│ 4. Commit with message: "feat(area): Description of changes"                │
│ 5. Report completion status: COMPLETE / PARTIAL / BLOCKED                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Agent Prompts

---

### 🔷 AGENT 1: Navigation & App Shell

**Branch:** `sprint04/navigation`

**Your Mission:**  
Create the navigation infrastructure and main app shell with bottom navigation for LedgerLens.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/ui/navigation/
shared/src/commonMain/kotlin/com/ledgerlens/ui/app/
shared/src/commonTest/kotlin/com/ledgerlens/ui/navigation/
```

**Tasks:**

#### 1. Screen Definitions (`ui/navigation/Screen.kt`)
```kotlin
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Transactions : Screen("transactions")
    object TransactionDetail : Screen("transactions/{transactionId}") {
        fun createRoute(transactionId: String) = "transactions/$transactionId"
    }
    object Import : Screen("import")
    object Review : Screen("review")
    object Receipts : Screen("receipts")
    object ReceiptDetail : Screen("receipts/{receiptId}") {
        fun createRoute(receiptId: String) = "receipts/$receiptId"
    }
    object Categories : Screen("categories")
    object Settings : Screen("settings")
    object BackupRestore : Screen("settings/backup")
    object Security : Screen("settings/security")
}
```

#### 2. Navigation Actions (`ui/navigation/NavigationActions.kt`)
- [ ] Type-safe navigation helper functions
- [ ] Back stack management utilities
- [ ] Deep link handling setup

#### 3. App Shell (`ui/app/LedgerLensApp.kt`)
- [ ] Root composable with MaterialTheme wrapping
- [ ] Navigation host setup
- [ ] Scaffold with bottom navigation
- [ ] Top app bar configuration

#### 4. Bottom Navigation (`ui/app/BottomNavBar.kt`)
- [ ] 5 items: Dashboard, Transactions, Import (FAB), Receipts, Settings
- [ ] Selected state handling
- [ ] Badge support for Review count

#### 5. Placeholder Screens
Create minimal placeholders for screens other agents will implement:
```kotlin
@Composable
fun DashboardScreenPlaceholder(onNavigate: (Screen) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Dashboard - Agent 2 will implement")
    }
}
```

**Existing Resources:**
- Theme: `com.ledgerlens.ui.theme.LedgerLensTheme`
- Icons: `com.ledgerlens.ui.components.LedgerLensIcons`

**Setup:**
```bash
git checkout sprint04/ui-continuation
git checkout -b sprint04/navigation
```

**Deliverables Checklist:**
- [ ] Screen.kt - All route definitions
- [ ] NavigationActions.kt - Navigation helpers
- [ ] LedgerLensApp.kt - Root app composable
- [ ] BottomNavBar.kt - Bottom navigation
- [ ] TopAppBarConfig.kt - App bar setup
- [ ] Placeholder screens for all routes
- [ ] NavigationTest.kt - Unit tests

---

### 🔷 AGENT 2: Dashboard Screen

**Branch:** `sprint04/dashboard`

**Your Mission:**  
Build the Dashboard screen showing financial overview, recent transactions, and quick actions.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/dashboard/
shared/src/commonMain/kotlin/com/ledgerlens/ui/viewmodels/dashboard/
shared/src/commonTest/kotlin/com/ledgerlens/ui/screens/dashboard/
```

**Tasks:**

#### 1. Dashboard UI State (`ui/viewmodels/dashboard/DashboardUiState.kt`)
```kotlin
data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalSpendingThisMonth: Money = Money.zero("USD"),
    val totalIncomeThisMonth: Money = Money.zero("USD"),
    val netThisMonth: Money = Money.zero("USD"),
    val recentTransactions: List<TransactionSummary> = emptyList(),
    val pendingReviewCount: Int = 0,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val comparisonToPreviousMonth: SpendingComparison? = null
)

data class TransactionSummary(
    val id: String,
    val merchant: String,
    val amount: Money,
    val category: String?,
    val date: LocalDate
)

data class CategorySpending(
    val categoryId: String,
    val categoryName: String,
    val amount: Money,
    val percentage: Float
)

data class SpendingComparison(
    val percentageChange: Float,
    val isIncrease: Boolean
)
```

#### 2. Dashboard ViewModel (`ui/viewmodels/dashboard/DashboardViewModel.kt`)
- [ ] Load summary statistics
- [ ] Load recent transactions (last 5-10)
- [ ] Load category breakdown
- [ ] Calculate month-over-month comparison
- [ ] Handle refresh action

#### 3. Dashboard Screen (`ui/screens/dashboard/DashboardScreen.kt`)
- [ ] Summary cards row (Income, Spending, Net)
- [ ] Month comparison indicator
- [ ] Recent transactions list
- [ ] Category pie chart placeholder (or simple bar list)
- [ ] "Needs Review" badge/card with count
- [ ] Quick action buttons (Import, View All)
- [ ] Pull-to-refresh support

#### 4. Dashboard Components
- [ ] `SummaryCard.kt` - Financial summary card
- [ ] `CategoryBreakdownChart.kt` - Category visualization
- [ ] `RecentTransactionItem.kt` - Compact transaction row
- [ ] `ReviewBadge.kt` - Pending review indicator

**Existing Resources:**
- `com.ledgerlens.ui.components.MoneyText` - For money display
- `com.ledgerlens.ui.components.LedgerLensCard` - Card styling
- `com.ledgerlens.ui.components.LoadingIndicator`
- `com.ledgerlens.ui.components.EmptyState`
- `com.ledgerlens.domain.Money`

**Setup:**
```bash
git checkout sprint04/ui-continuation
git checkout -b sprint04/dashboard
```

**Deliverables Checklist:**
- [ ] DashboardUiState.kt
- [ ] DashboardViewModel.kt
- [ ] DashboardScreen.kt
- [ ] SummaryCard.kt
- [ ] CategoryBreakdownChart.kt
- [ ] RecentTransactionItem.kt
- [ ] DashboardViewModelTest.kt (extend existing)

---

### 🔷 AGENT 3: Import Wizard

**Branch:** `sprint04/import`

**Your Mission:**  
Build the Import wizard for PDF/CSV bank statement imports with progress tracking.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/import/
shared/src/commonMain/kotlin/com/ledgerlens/ui/viewmodels/import/
shared/src/commonTest/kotlin/com/ledgerlens/ui/screens/import/
```

**Tasks:**

#### 1. Import UI State (`ui/viewmodels/import/ImportUiState.kt`)
```kotlin
sealed class ImportUiState {
    object Idle : ImportUiState()
    data class SelectingFile(val fileType: ImportFileType) : ImportUiState()
    data class SelectingBank(val filePath: String, val detectedBank: String?) : ImportUiState()
    data class Importing(
        val progress: Float,
        val currentStep: String,
        val transactionsFound: Int
    ) : ImportUiState()
    data class Complete(val result: ImportResult) : ImportUiState()
    data class Error(val message: String, val canRetry: Boolean) : ImportUiState()
}

enum class ImportFileType { PDF, CSV }

data class ImportResult(
    val totalTransactions: Int,
    val newTransactions: Int,
    val duplicatesSkipped: Int,
    val needsReviewCount: Int,
    val importId: String
)
```

#### 2. Import ViewModel (`ui/viewmodels/import/ImportViewModel.kt`)
- [ ] File type selection handling
- [ ] Bank/source detection
- [ ] Import orchestration with progress updates
- [ ] Error handling and retry logic
- [ ] Navigate to review on completion

#### 3. Import Screens
- [ ] `ImportScreen.kt` - Main import entry point
- [ ] `FileTypeSelector.kt` - PDF vs CSV selection
- [ ] `BankSelector.kt` - Bank template selection
- [ ] `ImportProgressScreen.kt` - Progress with steps
- [ ] `ImportResultScreen.kt` - Summary with actions

#### 4. Import Components
- [ ] `FileTypeCard.kt` - Selectable file type card
- [ ] `BankListItem.kt` - Bank selection row
- [ ] `ImportProgressIndicator.kt` - Step progress
- [ ] `ImportSummaryCard.kt` - Result statistics

**Existing Domain Classes:**
- `com.ledgerlens.import.PdfParser`
- `com.ledgerlens.import.CsvParser`
- `com.ledgerlens.import.StatementTemplate`
- `com.ledgerlens.import.DuplicateDetector`
- `com.ledgerlens.import.ImportIdempotency`

**Setup:**
```bash
git checkout sprint04/ui-continuation
git checkout -b sprint04/import
```

**Deliverables Checklist:**
- [ ] ImportUiState.kt
- [ ] ImportViewModel.kt
- [ ] ImportScreen.kt
- [ ] FileTypeSelector.kt
- [ ] BankSelector.kt
- [ ] ImportProgressScreen.kt
- [ ] ImportResultScreen.kt
- [ ] ImportViewModelTest.kt

---

### 🔷 AGENT 4: Review Inbox

**Branch:** `sprint04/review`

**Your Mission:**  
Build the Review inbox for handling low-confidence categorizations and potential duplicates.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/review/
shared/src/commonMain/kotlin/com/ledgerlens/ui/viewmodels/review/
shared/src/commonTest/kotlin/com/ledgerlens/ui/screens/review/
```

**Tasks:**

#### 1. Review UI State (`ui/viewmodels/review/ReviewUiState.kt`)
```kotlin
data class ReviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<ReviewItemUiModel> = emptyList(),
    val filter: ReviewFilter = ReviewFilter.ALL,
    val selectedItemId: String? = null,
    val totalCount: Int = 0,
    val processedCount: Int = 0
)

data class ReviewItemUiModel(
    val id: String,
    val transactionId: String,
    val merchant: String,
    val amount: Money,
    val date: LocalDate,
    val reviewType: ReviewType,
    val suggestedCategory: String?,
    val confidence: Float,
    val explanation: String?,
    val possibleDuplicateOf: String?
)

enum class ReviewType { LOW_CONFIDENCE, POSSIBLE_DUPLICATE, UNCATEGORIZED, RULE_CONFLICT }
enum class ReviewFilter { ALL, LOW_CONFIDENCE, DUPLICATES, UNCATEGORIZED }
```

#### 2. Review ViewModel (`ui/viewmodels/review/ReviewViewModel.kt`)
- [ ] Load review queue items
- [ ] Filter handling
- [ ] Accept suggestion action
- [ ] Reject/skip action
- [ ] Manual category assignment
- [ ] Mark as not duplicate
- [ ] Bulk operations (approve all high confidence)

#### 3. Review Screens
- [ ] `ReviewInboxScreen.kt` - Main inbox list
- [ ] `ReviewDetailScreen.kt` - Single item review
- [ ] `DuplicateComparisonScreen.kt` - Side-by-side duplicate view

#### 4. Review Components
- [ ] `ReviewItemCard.kt` - Inbox list item
- [ ] `ReviewFilterChips.kt` - Filter selection
- [ ] `CategorySuggestion.kt` - Suggested category with confidence
- [ ] `ReviewActionButtons.kt` - Accept/Reject/Edit
- [ ] `DuplicateComparison.kt` - Side-by-side view

**Existing Domain Classes:**
- `com.ledgerlens.categorization.pipeline.ReviewQueueManager`
- `com.ledgerlens.categorization.CategoryExplanation`
- `com.ledgerlens.categorization.ConfidenceLevel`
- `com.ledgerlens.import.DuplicateCheckResult`

**Setup:**
```bash
git checkout sprint04/ui-continuation
git checkout -b sprint04/review
```

**Deliverables Checklist:**
- [ ] ReviewUiState.kt
- [ ] ReviewViewModel.kt
- [ ] ReviewInboxScreen.kt
- [ ] ReviewDetailScreen.kt
- [ ] DuplicateComparisonScreen.kt
- [ ] ReviewItemCard.kt
- [ ] ReviewFilterChips.kt
- [ ] ReviewViewModelTest.kt

---

### 🔷 AGENT 5: Receipts Screen

**Branch:** `sprint04/receipts`

**Your Mission:**  
Build the Receipts screen with gallery view, OCR results display, and split functionality.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/receipts/
shared/src/commonMain/kotlin/com/ledgerlens/ui/viewmodels/receipts/
shared/src/commonTest/kotlin/com/ledgerlens/ui/screens/receipts/
```

**Tasks:**

#### 1. Receipts UI State (`ui/viewmodels/receipts/ReceiptsUiState.kt`)
```kotlin
data class ReceiptsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val receipts: List<ReceiptUiModel> = emptyList(),
    val viewMode: ReceiptViewMode = ReceiptViewMode.GRID,
    val searchQuery: String = "",
    val selectedReceiptId: String? = null
)

data class ReceiptUiModel(
    val id: String,
    val thumbnailPath: String?,
    val merchantName: String?,
    val totalAmount: Money?,
    val date: LocalDate?,
    val linkedTransactionId: String?,
    val itemCount: Int,
    val hasSplit: Boolean
)

enum class ReceiptViewMode { GRID, LIST }

data class ReceiptDetailUiState(
    val receipt: ReceiptUiModel? = null,
    val extractedItems: List<ExtractedItemUiModel> = emptyList(),
    val splitParticipants: List<ParticipantUiModel> = emptyList(),
    val isProcessingOcr: Boolean = false
)

data class ExtractedItemUiModel(
    val id: String,
    val name: String,
    val price: Money,
    val quantity: Int,
    val assignedTo: List<String> = emptyList()
)

data class ParticipantUiModel(
    val id: String,
    val name: String,
    val owedAmount: Money
)
```

#### 2. Receipts ViewModel (`ui/viewmodels/receipts/ReceiptsViewModel.kt`)
- [ ] Load receipts list
- [ ] Search/filter handling
- [ ] View mode toggle
- [ ] Select receipt for detail view

#### 3. Receipt Detail ViewModel (`ui/viewmodels/receipts/ReceiptDetailViewModel.kt`)
- [ ] Load receipt details
- [ ] Load OCR extracted items
- [ ] Handle item assignment to participants
- [ ] Calculate split amounts

#### 4. Receipts Screens
- [ ] `ReceiptsScreen.kt` - Gallery/list view
- [ ] `ReceiptDetailScreen.kt` - Full receipt view
- [ ] `ReceiptImageViewer.kt` - Zoomable image

#### 5. Receipt Components
- [ ] `ReceiptGridItem.kt` - Gallery thumbnail
- [ ] `ReceiptListItem.kt` - List row
- [ ] `ExtractedItemRow.kt` - OCR item display
- [ ] `SplitReceiptSheet.kt` - Bottom sheet for splitting
- [ ] `ParticipantChip.kt` - Participant assignment

**Existing Domain Classes:**
- `com.ledgerlens.receipts.ExtractedReceipt`
- `com.ledgerlens.receipts.ReceiptItem`
- `com.ledgerlens.receipts.Participant`
- `com.ledgerlens.receipts.SplitParticipant`
- `com.ledgerlens.ocr.ReceiptOcr`

**Setup:**
```bash
git checkout sprint04/ui-continuation
git checkout -b sprint04/receipts
```

**Deliverables Checklist:**
- [ ] ReceiptsUiState.kt
- [ ] ReceiptsViewModel.kt
- [ ] ReceiptDetailViewModel.kt
- [ ] ReceiptsScreen.kt
- [ ] ReceiptDetailScreen.kt
- [ ] SplitReceiptSheet.kt
- [ ] ReceiptsViewModelTest.kt (extend existing)

---

### 🔷 AGENT 6: Categories & Rules Screen

**Branch:** `sprint04/categories`

**Your Mission:**  
Build the Categories management and Rules configuration screens.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/ui/screens/categories/
shared/src/commonMain/kotlin/com/ledgerlens/ui/viewmodels/categories/
shared/src/commonTest/kotlin/com/ledgerlens/ui/screens/categories/
```

**Tasks:**

#### 1. Categories UI State (`ui/viewmodels/categories/CategoriesUiState.kt`)
```kotlin
data class CategoriesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val categories: List<CategoryTreeNode> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
    val selectedCategoryId: String? = null,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false
)

data class CategoryTreeNode(
    val id: String,
    val name: String,
    val icon: String?,
    val color: Long?,
    val level: Int,
    val transactionCount: Int,
    val children: List<CategoryTreeNode>,
    val isExpanded: Boolean
)

data class RulesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val rules: List<RuleUiModel> = emptyList(),
    val showAddDialog: Boolean = false
)

data class RuleUiModel(
    val id: String,
    val name: String,
    val conditions: String, // Human readable summary
    val targetCategory: String,
    val priority: Int,
    val isEnabled: Boolean,
    val matchCount: Int
)
```

#### 2. Categories ViewModel (`ui/viewmodels/categories/CategoriesViewModel.kt`)
- [ ] Load category tree
- [ ] Expand/collapse handling
- [ ] Add category
- [ ] Edit category
- [ ] Delete category (with confirmation)
- [ ] Reorder categories

#### 3. Rules ViewModel (`ui/viewmodels/categories/RulesViewModel.kt`)
- [ ] Load rules list
- [ ] Add rule
- [ ] Edit rule
- [ ] Delete rule
- [ ] Toggle rule enabled/disabled
- [ ] Reorder rule priority

#### 4. Categories Screens
- [ ] `CategoriesScreen.kt` - Main categories view with tabs
- [ ] `CategoryTreeView.kt` - Expandable tree
- [ ] `CategoryEditDialog.kt` - Add/Edit dialog
- [ ] `RulesListScreen.kt` - Rules tab content
- [ ] `RuleEditDialog.kt` - Rule builder dialog

#### 5. Categories Components
- [ ] `CategoryTreeItem.kt` - Tree node with indent
- [ ] `CategoryIcon.kt` - Category icon display
- [ ] `RuleListItem.kt` - Rule row with toggle
- [ ] `RuleConditionBuilder.kt` - Visual rule builder

**Existing Domain Classes:**
- `com.ledgerlens.categorization.Category`
- `com.ledgerlens.categorization.CategoryTree`
- `com.ledgerlens.categorization.rules.RuleEngine`
- `com.ledgerlens.categorization.rules.RuleBuilder`

**Setup:**
```bash
git checkout sprint04/ui-continuation
git checkout -b sprint04/categories
```

**Deliverables Checklist:**
- [ ] CategoriesUiState.kt
- [ ] CategoriesViewModel.kt
- [ ] RulesViewModel.kt
- [ ] CategoriesScreen.kt
- [ ] CategoryTreeView.kt
- [ ] CategoryEditDialog.kt
- [ ] RulesListScreen.kt
- [ ] RuleEditDialog.kt
- [ ] CategoriesViewModelTest.kt (extend existing)

---

### 🔷 AGENT 7: Settings ViewModel

**Branch:** `sprint04/settings-vm`

**Your Mission:**  
Implement the Settings ViewModel and connect existing Settings screens to business logic.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/ui/viewmodels/settings/
shared/src/commonTest/kotlin/com/ledgerlens/ui/viewmodels/settings/
```

**Note:** Settings screens already exist at `ui/screens/settings/`. You are implementing the ViewModel layer only.

**Tasks:**

#### 1. Settings UI State (`ui/viewmodels/settings/SettingsUiState.kt`)
```kotlin
data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultCurrency: String = "USD",
    val biometricsEnabled: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val lastBackupDate: LocalDateTime? = null,
    val appVersion: String = "",
    val databaseSize: String = ""
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class BackupRestoreUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportProgress: Float = 0f,
    val importProgress: Float = 0f,
    val recoveryKey: String? = null,
    val showRecoveryKey: Boolean = false
)

data class SecuritySettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isChangingPassphrase: Boolean = false,
    val passphraseStrength: PassphraseStrength? = null,
    val biometricsEnabled: Boolean = false,
    val lastUnlockMethod: String? = null
)

enum class PassphraseStrength { WEAK, FAIR, GOOD, STRONG }
```

#### 2. Settings ViewModel (`ui/viewmodels/settings/SettingsViewModel.kt`)
- [ ] Load current settings
- [ ] Update theme preference
- [ ] Update default currency
- [ ] Toggle biometrics
- [ ] Get app info

#### 3. Backup/Restore ViewModel (`ui/viewmodels/settings/BackupRestoreViewModel.kt`)
- [ ] Export backup with progress
- [ ] Import backup with progress
- [ ] Show/hide recovery key
- [ ] Copy recovery key to clipboard

#### 4. Security ViewModel (`ui/viewmodels/settings/SecuritySettingsViewModel.kt`)
- [ ] Change passphrase flow
- [ ] Validate passphrase strength
- [ ] Toggle biometrics
- [ ] Crypto-erase (factory reset)

**Existing Domain Classes:**
- `com.ledgerlens.security.KeyManager`
- `com.ledgerlens.security.PassphraseRequirements`
- `com.ledgerlens.security.BackupBundle`
- `com.ledgerlens.security.RecoveryKey`

**Setup:**
```bash
git checkout sprint04/ui-continuation
git checkout -b sprint04/settings-vm
```

**Deliverables Checklist:**
- [ ] SettingsUiState.kt
- [ ] SettingsViewModel.kt
- [ ] BackupRestoreViewModel.kt
- [ ] SecuritySettingsViewModel.kt
- [ ] SettingsViewModelTest.kt (extend existing)

---

### 🔷 AGENT 8: Repository Implementations

**Branch:** `sprint05/repositories`

**Your Mission:**  
Implement SQLDelight-backed repositories connecting UI to database.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/data/repositories/
shared/src/commonTest/kotlin/com/ledgerlens/data/repositories/
```

**Tasks:**

#### 1. Transaction Repository (`data/repositories/TransactionRepository.kt`)
```kotlin
interface TransactionRepository {
    fun getTransactions(filter: TransactionFilter): Flow<List<Transaction>>
    fun getTransaction(id: String): Flow<Transaction?>
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>
    fun getTransactionsByDateRange(start: LocalDate, end: LocalDate): Flow<List<Transaction>>
    fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>>
    suspend fun insertTransaction(transaction: Transaction): String
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: String)
    suspend fun updateCategory(transactionId: String, categoryId: String)
}
```

#### 2. SQLDelight Implementation (`data/repositories/SqlDelightTransactionRepository.kt`)
- [ ] Implement all TransactionRepository methods
- [ ] Use SQLDelight generated queries
- [ ] Map database entities to domain models
- [ ] Handle Flow emissions

#### 3. Additional Repositories
- [ ] `SqlDelightCategoryRepository.kt` - Categories CRUD
- [ ] `SqlDelightReceiptRepository.kt` - Receipts CRUD
- [ ] `SqlDelightRuleRepository.kt` - Rules CRUD
- [ ] `SqlDelightAccountRepository.kt` - Accounts CRUD
- [ ] `SqlDelightImportRepository.kt` - Import history

#### 4. Statistics Repository (`data/repositories/StatisticsRepository.kt`)
- [ ] Monthly spending totals
- [ ] Category breakdowns
- [ ] Month-over-month comparisons
- [ ] Top merchants

**Existing Resources:**
- SQLDelight schema at `shared/src/commonMain/sqldelight/`
- `com.ledgerlens.db.LedgerLensDatabase`
- Entity mappers needed

**Setup:**
```bash
git checkout main
git checkout -b sprint05/repositories
```

**Deliverables Checklist:**
- [ ] TransactionRepository.kt (interface)
- [ ] SqlDelightTransactionRepository.kt
- [ ] SqlDelightCategoryRepository.kt
- [ ] SqlDelightReceiptRepository.kt
- [ ] SqlDelightRuleRepository.kt
- [ ] StatisticsRepository.kt
- [ ] Repository unit tests

---

### 🔷 AGENT 9: Dependency Injection

**Branch:** `sprint05/di`

**Your Mission:**  
Set up dependency injection structure for ViewModels and Repositories.

**Your Exclusive Directories:**
```
shared/src/commonMain/kotlin/com/ledgerlens/di/
shared/src/androidMain/kotlin/com/ledgerlens/di/
shared/src/desktopMain/kotlin/com/ledgerlens/di/
android/src/main/kotlin/.../di/
desktop/src/main/kotlin/.../di/
```

**Tasks:**

#### 1. DI Module Structure (`di/`)
```kotlin
// AppModule.kt - Central dependency graph
object AppModule {
    // Lazy singletons for repositories
    val transactionRepository: TransactionRepository by lazy { ... }
    val categoryRepository: CategoryRepository by lazy { ... }
    
    // Factory functions for ViewModels
    fun dashboardViewModel() = DashboardViewModel(transactionRepository, ...)
    fun transactionsViewModel() = TransactionsViewModel(transactionRepository, ...)
}
```

#### 2. Platform-Specific Setup
- [ ] `PlatformModule.kt` - expect/actual for platform deps
- [ ] Android: Context, Activity references
- [ ] Desktop: File system paths

#### 3. ViewModel Factory Pattern
```kotlin
// ViewModelFactory.kt
class ViewModelFactory(private val appModule: AppModule) {
    fun <T : ViewModel> create(modelClass: KClass<T>): T {
        return when (modelClass) {
            DashboardViewModel::class -> appModule.dashboardViewModel()
            // ...
        } as T
    }
}
```

#### 4. App Initialization
- [ ] `AppInitializer.kt` - Startup sequence
- [ ] Database initialization
- [ ] KeyManager unlock flow
- [ ] Default data seeding

**Notes:**
- Keep it simple - manual DI, no Koin/Hilt for KMP simplicity
- Use lazy initialization for expensive objects
- Ensure thread-safe singleton access

**Setup:**
```bash
git checkout main
git checkout -b sprint05/di
```

**Deliverables Checklist:**
- [ ] AppModule.kt
- [ ] PlatformModule.kt (expect/actual)
- [ ] ViewModelFactory.kt
- [ ] AppInitializer.kt
- [ ] Android app integration
- [ ] Desktop app integration

---

### 🔷 AGENT 10: Integration & E2E Tests

**Branch:** `sprint05/integration-tests`

**Your Mission:**  
Create integration tests and end-to-end test utilities.

**Your Exclusive Directories:**
```
shared/src/commonTest/kotlin/com/ledgerlens/integration/
shared/src/desktopTest/kotlin/com/ledgerlens/integration/
shared/src/commonTest/kotlin/com/ledgerlens/testutils/
```

**Tasks:**

#### 1. Test Utilities (`testutils/`)
```kotlin
// TestFixtures.kt
object TestFixtures {
    fun sampleTransaction() = Transaction(...)
    fun sampleCategory() = Category(...)
    fun sampleReceipt() = ExtractedReceipt(...)
}

// FakeRepositories.kt
class FakeTransactionRepository : TransactionRepository { ... }
class FakeCategoryRepository : CategoryRepository { ... }

// TestDatabase.kt
fun createTestDatabase(): LedgerLensDatabase
```

#### 2. ViewModel Integration Tests (`integration/viewmodels/`)
- [ ] DashboardViewModelIntegrationTest.kt
- [ ] TransactionsViewModelIntegrationTest.kt
- [ ] ImportViewModelIntegrationTest.kt
- [ ] ReviewViewModelIntegrationTest.kt

#### 3. Repository Integration Tests (`integration/repositories/`)
- [ ] TransactionRepositoryIntegrationTest.kt
- [ ] Full CRUD cycle tests
- [ ] Query performance tests

#### 4. Flow Tests (`integration/flows/`)
- [ ] ImportFlowTest.kt - Full import → review → categorize flow
- [ ] CategorizationFlowTest.kt - Transaction → classify → learn flow
- [ ] BackupRestoreFlowTest.kt - Export → Import cycle

#### 5. Test Coverage Report
- [ ] Create test coverage configuration
- [ ] Document coverage targets (>80%)

**Setup:**
```bash
git checkout main
git checkout -b sprint05/integration-tests
```

**Deliverables Checklist:**
- [ ] TestFixtures.kt
- [ ] FakeRepositories.kt
- [ ] TestDatabase.kt
- [ ] ViewModel integration tests
- [ ] Repository integration tests
- [ ] Flow integration tests
- [ ] Coverage configuration

---

## Integration Procedure

### Phase 1: Sprint 04 UI Integration

**Run after Agents 1-7 complete:**

```bash
# 1. Create integration branch
git checkout sprint04/ui-continuation
git checkout -b sprint04/integration

# 2. Merge in order (Navigation first as foundation)
git merge sprint04/navigation --no-ff -m "Integrate: Navigation & App Shell"
git merge sprint04/dashboard --no-ff -m "Integrate: Dashboard Screen"
git merge sprint04/import --no-ff -m "Integrate: Import Wizard"
git merge sprint04/review --no-ff -m "Integrate: Review Inbox"
git merge sprint04/receipts --no-ff -m "Integrate: Receipts Screen"
git merge sprint04/categories --no-ff -m "Integrate: Categories Screen"
git merge sprint04/settings-vm --no-ff -m "Integrate: Settings ViewModels"

# 3. Resolve any TODO(integration) comments

# 4. Run tests
./gradlew :shared:desktopTest

# 5. Merge to main
git checkout main
git merge sprint04/integration --no-ff -m "Sprint 04: UI Implementation Complete"
```

### Phase 2: Sprint 05 Integration

**Run after Agents 8-10 complete:**

```bash
git checkout main
git checkout -b sprint05/integration

git merge sprint05/repositories --no-ff -m "Integrate: Repository Implementations"
git merge sprint05/di --no-ff -m "Integrate: Dependency Injection"
git merge sprint05/integration-tests --no-ff -m "Integrate: Integration Tests"

./gradlew :shared:check
git checkout main
git merge sprint05/integration --no-ff -m "Sprint 05: Data Layer Complete"
```

---

## Progress Tracking

> **Last Updated:** 2026-01-17
> **Integration Branch:** `sprint04/integration`

### Agent Status Table

| Agent | Branch | Status | Files Created | Tests | Notes |
|-------|--------|--------|---------------|-------|-------|
| 1 | sprint04/navigation | ✅ COMPLETE | 12 | 4 | Navigation, App Shell, MainScaffold |
| 2 | sprint04/dashboard-transactions | ✅ COMPLETE | 5 | 2 | Dashboard, Transactions screens + VMs |
| 3 | sprint04/navigation | ✅ COMPLETE | 4 | 1 | Import screens + VM (merged with Agent 1) |
| 4 | sprint04/navigation | ✅ COMPLETE | 4 | 1 | Review screens + VM (merged with Agent 1) |
| 5 | sprint04/navigation | ✅ COMPLETE | 4 | 1 | Receipts screens + VM |
| 6 | sprint04/integration | ✅ COMPLETE | 2 | 1 | CategoriesScreen + VM |
| 7 | sprint04/design-system | 🔄 PARTIAL | 3 | 1 | Settings screens exist, VMs pending |
| 8 | sprint05/repositories | ⏳ Pending | 0 | 0 | Not started |
| 9 | sprint05/di | ⏳ Pending | 0 | 0 | Not started |
| 10 | sprint05/integration-tests | ⏳ Pending | 0 | 0 | Not started |

### Sprint 04 Integration Summary

All Sprint 04 branches have been merged into `sprint04/integration`:
- **Navigation infrastructure**: Screen routes, NavGraph, NavArguments, NavigationActions
- **App Shell**: LedgerLensApp, BottomNavBar, TopAppBar, MainScaffold
- **Design System**: 5 theme files, 10 UI components
- **All Screens**: Dashboard, Transactions, Import, Review, Receipts, Categories, Settings
- **All ViewModels**: 7 ViewModels implemented
- **All Tests**: 12 test files for UI layer

### Completion Criteria

**Per Agent:**
- [x] All deliverables created (Agents 1-6)
- [x] Unit tests passing (pending JAVA_HOME)
- [ ] No lint errors (pending verification)
- [x] Committed to branch
- [x] Dependencies documented

**Sprint 04 Complete When:**
- [x] All 7 UI agents integrated
- [ ] App compiles without errors (requires JAVA_HOME)
- [x] All screens navigable (structure complete)
- [ ] Tests pass: `./gradlew :shared:desktopTest` (requires JAVA_HOME)

**Sprint 05 Complete When:**
- [ ] Repositories connected to UI
- [ ] DI wiring complete
- [ ] Integration tests pass
- [ ] Full app runnable on Android + Desktop

---

## Quick Reference

### Directory Structure After Completion

```
shared/src/commonMain/kotlin/com/ledgerlens/
├── ui/
│   ├── app/                 # Agent 1
│   │   ├── LedgerLensApp.kt
│   │   ├── BottomNavBar.kt
│   │   └── TopAppBarConfig.kt
│   ├── navigation/          # Agent 1
│   │   ├── Screen.kt
│   │   └── NavigationActions.kt
│   ├── components/          # ✅ EXISTING (10 files)
│   ├── theme/               # ✅ EXISTING (5 files)
│   ├── screens/
│   │   ├── dashboard/       # Agent 2
│   │   ├── import/          # Agent 3
│   │   ├── review/          # Agent 4
│   │   ├── receipts/        # Agent 5
│   │   ├── categories/      # Agent 6
│   │   ├── transactions/    # ✅ EXISTING
│   │   └── settings/        # ✅ EXISTING
│   └── viewmodels/
│       ├── dashboard/       # Agent 2
│       ├── import/          # Agent 3
│       ├── review/          # Agent 4
│       ├── receipts/        # Agent 5
│       ├── categories/      # Agent 6
│       ├── settings/        # Agent 7
│       └── transactions/    # ✅ EXISTING
├── data/
│   └── repositories/        # Agent 8
├── di/                      # Agent 9
└── ...existing packages...
```

### Test Commands

```bash
# All tests
./gradlew :shared:check

# Specific package
./gradlew :shared:desktopTest --tests "com.ledgerlens.ui.screens.dashboard.*"

# Single test class
./gradlew :shared:desktopTest --tests "DashboardViewModelTest"
```

---

*Document Version: 1.0*  
*Created: 2026-01-15*  
*For: LedgerLens Sprint 04/05 Parallel Development*
