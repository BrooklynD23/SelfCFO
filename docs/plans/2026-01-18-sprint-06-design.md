# Sprint 06 Design: End-to-End Integration

> **Design Document** - Created 2026-01-18
> **Status:** Ready for Implementation
> **Prerequisite:** Sprint 05 Complete (Data Layer Integration)

---

## Executive Summary

Sprint 06 transforms LedgerLens from a mock-driven UI prototype to a fully functional app with real data persistence. All 8 ViewModels will be wired to real SQLDelight repositories, and 4 new production-ready services will be built.

**Goal:** A shippable local-first personal finance app with complete data flow from import to backup.

---

## Scope & Deliverables

| Category | Items | Count |
|----------|-------|-------|
| ViewModel Wiring | All 8 ViewModels connected to real repositories | 8 |
| New Services | ReviewQueueManager, SettingsRepository, BackupService, CorrectionProcessor | 4 |
| New Repository | SqlDelightParticipantRepository | 1 |
| Service Wiring | Existing parsers/encryption to ViewModels | 5+ |
| Schema Updates | ReviewQueue table, Settings table | 2 |
| Koin Updates | ViewModel module, service registrations | 1 module |
| Tests | Integration tests for all new services | ~40+ |

**Estimated Files:** ~25 new/modified files

---

## Architecture

```
UI Layer (Compose Screens)
    │
    ▼
ViewModel Layer (Koin-injected)
    │
    ├─── DashboardViewModel
    ├─── TransactionsViewModel
    ├─── CategoriesViewModel
    ├─── ReceiptsViewModel
    ├─── ReviewViewModel
    ├─── ImportViewModel
    ├─── CorrectionViewModel
    └─── SettingsViewModel
    │
    ▼
Service Layer
    │
    ├─── ReviewQueueManager (NEW)
    ├─── BackupService (NEW)
    ├─── CorrectionProcessor (NEW)
    ├─── CsvParser (EXISTS - Sprint 01)
    ├─── PdfParser (EXISTS - Sprint 01)
    ├─── DuplicateDetector (EXISTS - Sprint 01)
    └─── KeyManager (EXISTS - Sprint 01)
    │
    ▼
Repository Layer (SqlDelight - Sprint 05)
    │
    ├─── TransactionRepository
    ├─── CategoryRepository
    ├─── AccountRepository
    ├─── RuleRepository
    ├─── ImportRepository
    ├─── ReceiptRepository
    ├─── StatisticsRepository
    ├─── SettingsRepository (NEW)
    └─── ParticipantRepository (NEW - replaces InMemory)
    │
    ▼
SQLite Database (SQLCipher encrypted)
```

---

## Implementation Phases

### Phase 1: Schema & Infrastructure

**Objective:** Prepare database schema and DI foundation

#### Tasks

1. **Add ReviewQueue table to SQLDelight**
   - File: `shared/src/commonMain/sqldelight/com/ledgerlens/db/ReviewQueue.sq`
   - Columns: `id`, `transaction_id`, `added_at`, `source` (AUTO/MANUAL), `reason`, `status`
   - Queries: insert, selectAll, selectByStatus, updateStatus, delete, countPending

2. **Add Settings table to SQLDelight**
   - File: `shared/src/commonMain/sqldelight/com/ledgerlens/db/Settings.sq`
   - Columns: `key` (PRIMARY), `value`, `updated_at`
   - Queries: insert, selectByKey, selectAll, upsert, delete

3. **Verify Participant table has needed queries**
   - Check existing `Participant.sq` for CRUD completeness
   - Add missing queries if needed

4. **Update Koin AppModule structure**
   - Add `serviceModule` for new services
   - Add `viewModelModule` for ViewModel factories
   - Update `appModule` to include all modules

#### Files to Create/Modify
- `shared/src/commonMain/sqldelight/com/ledgerlens/db/ReviewQueue.sq` (NEW)
- `shared/src/commonMain/sqldelight/com/ledgerlens/db/Settings.sq` (NEW)
- `shared/src/commonMain/sqldelight/com/ledgerlens/db/Participant.sq` (MODIFY if needed)
- `shared/src/commonMain/kotlin/com/ledgerlens/data/di/AppModule.kt` (MODIFY)

#### Commit After Phase 1
```
feat(sprint06): Add ReviewQueue and Settings schema, update Koin modules
```

---

### Phase 2: Core Services

**Objective:** Build all new services with production-ready implementations

#### Task 2.1: SettingsRepository

**Interface:**
```kotlin
interface SettingsRepository {
    fun getTheme(): Flow<AppTheme>
    suspend fun setTheme(theme: AppTheme)

    fun getCurrency(): Flow<String>
    suspend fun setCurrency(currencyCode: String)

    fun getConfidenceThreshold(): Flow<Float>
    suspend fun setConfidenceThreshold(threshold: Float)

    fun getAllSettings(): Flow<AppSettings>
}
```

**Files:**
- `shared/src/commonMain/kotlin/com/ledgerlens/data/repositories/SettingsRepository.kt` (interface)
- `shared/src/commonMain/kotlin/com/ledgerlens/data/repositories/impl/SqlDelightSettingsRepository.kt` (impl)
- `shared/src/commonMain/kotlin/com/ledgerlens/domain/model/AppSettings.kt` (model)
- `shared/src/commonTest/kotlin/com/ledgerlens/data/repositories/SqlDelightSettingsRepositoryTest.kt` (tests)

#### Task 2.2: ReviewQueueManager

**Interface:**
```kotlin
interface ReviewQueueManager {
    // Query
    fun getQueueItems(limit: Int = 50): Flow<List<ReviewItem>>
    fun getQueueCount(): Flow<Int>

    // Auto-population (confidence-based)
    suspend fun addLowConfidenceTransaction(transactionId: String, confidence: Float)

    // Manual flagging
    suspend fun flagForReview(transactionId: String, reason: String?)

    // Resolution
    suspend fun approve(transactionId: String, categoryId: String)
    suspend fun dismiss(transactionId: String)
}
```

**Behavior:**
- Transactions with confidence < threshold (from SettingsRepository) auto-add to queue
- Users can manually flag any transaction
- Approve updates transaction category and removes from queue
- Dismiss just removes from queue

**Files:**
- `shared/src/commonMain/kotlin/com/ledgerlens/services/review/ReviewQueueManager.kt` (interface)
- `shared/src/commonMain/kotlin/com/ledgerlens/services/review/ReviewQueueManagerImpl.kt` (impl)
- `shared/src/commonMain/kotlin/com/ledgerlens/domain/model/ReviewItem.kt` (model)
- `shared/src/commonMain/kotlin/com/ledgerlens/data/mappers/ReviewQueueMapper.kt` (mapper)
- `shared/src/commonTest/kotlin/com/ledgerlens/services/review/ReviewQueueManagerTest.kt` (tests)

#### Task 2.3: SqlDelightParticipantRepository

**Interface:**
```kotlin
interface ParticipantRepository {
    fun getAll(): Flow<List<Participant>>
    fun getById(id: String): Flow<Participant?>
    fun getByGroupId(groupId: String): Flow<List<Participant>>
    suspend fun insert(participant: Participant): String
    suspend fun update(participant: Participant)
    suspend fun delete(id: String)
    fun search(query: String): Flow<List<Participant>>
}
```

**Files:**
- `shared/src/commonMain/kotlin/com/ledgerlens/data/repositories/ParticipantRepository.kt` (interface - may exist)
- `shared/src/commonMain/kotlin/com/ledgerlens/data/repositories/impl/SqlDelightParticipantRepository.kt` (impl)
- `shared/src/commonMain/kotlin/com/ledgerlens/data/mappers/ParticipantMapper.kt` (mapper)
- `shared/src/commonTest/kotlin/com/ledgerlens/data/repositories/SqlDelightParticipantRepositoryTest.kt` (tests)

#### Task 2.4: CorrectionProcessor

**Interface:**
```kotlin
interface CorrectionProcessor {
    suspend fun processCorrection(
        transactionId: String,
        oldCategoryId: String?,
        newCategoryId: String
    ): CorrectionResult

    suspend fun batchProcess(corrections: List<Correction>): BatchCorrectionResult
}
```

**Implementation Notes:**
- Wraps existing `IncrementalLearner` from Sprint 02
- Updates `MerchantPriorProvider` with correction data
- Uses existing `CorrectionRepository` for persistence

**Files:**
- `shared/src/commonMain/kotlin/com/ledgerlens/services/corrections/CorrectionProcessor.kt` (interface)
- `shared/src/commonMain/kotlin/com/ledgerlens/services/corrections/CorrectionProcessorImpl.kt` (impl)
- `shared/src/commonTest/kotlin/com/ledgerlens/services/corrections/CorrectionProcessorTest.kt` (tests)

#### Task 2.5: BackupService

**Interface:**
```kotlin
interface BackupService {
    // Export
    suspend fun createBackup(destination: Path): BackupResult
    suspend fun createEncryptedBackup(destination: Path, password: String): BackupResult

    // Import
    suspend fun restoreBackup(source: Path): RestoreResult
    suspend fun restoreEncryptedBackup(source: Path, password: String): RestoreResult

    // Metadata
    suspend fun getBackupInfo(path: Path): BackupMetadata?
    fun getLastBackupTime(): Flow<Instant?>
}

data class BackupMetadata(
    val createdAt: Instant,
    val version: Int,
    val transactionCount: Int,
    val isEncrypted: Boolean
)
```

**Implementation Notes:**
- Uses SQLite `.backup()` API for database dump
- Encryption via existing `KeyManager` and AES-256-GCM
- Stores backup metadata in Settings table

**Files:**
- `shared/src/commonMain/kotlin/com/ledgerlens/services/backup/BackupService.kt` (interface)
- `shared/src/commonMain/kotlin/com/ledgerlens/services/backup/BackupServiceImpl.kt` (impl)
- `shared/src/commonMain/kotlin/com/ledgerlens/services/backup/BackupResult.kt` (models)
- `shared/src/commonTest/kotlin/com/ledgerlens/services/backup/BackupServiceTest.kt` (tests)

#### Commit After Phase 2
```
feat(sprint06): Add ReviewQueueManager, SettingsRepository, BackupService, CorrectionProcessor
```

---

### Phase 3: ViewModel Integration

**Objective:** Wire all ViewModels to real dependencies, remove all mock data

#### Task 3.1: DashboardViewModel

**Updated Constructor:**
```kotlin
class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val statisticsRepository: StatisticsRepository,
    private val reviewQueueManager: ReviewQueueManager
)
```

**Changes:**
- Remove `generateMockDashboardData()` method
- Load real monthly summaries from `statisticsRepository.getMonthlySummary()`
- Show actual review queue count from `reviewQueueManager.getQueueCount()`
- Recent transactions from `transactionRepository.getRecent(limit = 5)`

#### Task 3.2: TransactionsViewModel

**Updated Constructor:**
```kotlin
class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
)
```

**Changes:**
- Remove `generateMockTransactions()` and `generateMockCategories()`
- Load transactions via `transactionRepository.getAll()` or filtered queries
- Categories from `categoryRepository.getAll()`

#### Task 3.3: CategoriesViewModel

**Updated Constructor:**
```kotlin
class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
    private val ruleRepository: RuleRepository
)
```

**Changes:**
- Remove any stub data
- Load category tree from `categoryRepository.getCategoryTree()`
- Load rules from `ruleRepository.getAll()`

#### Task 3.4: ReceiptsViewModel

**Updated Constructor:**
```kotlin
class ReceiptsViewModel(
    private val receiptRepository: ReceiptRepository,
    private val participantRepository: ParticipantRepository
)
```

**Changes:**
- Load receipts from `receiptRepository.getAll()`
- Participants from `participantRepository.getAll()`

#### Task 3.5: ReviewViewModel

**Updated Constructor:**
```kotlin
class ReviewViewModel(
    private val reviewQueueManager: ReviewQueueManager,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
)
```

**Changes:**
- Remove `generateMockReviewItems()`
- Load queue from `reviewQueueManager.getQueueItems()`
- Approve/dismiss via `reviewQueueManager.approve()` / `dismiss()`

#### Task 3.6: ImportViewModel

**Updated Constructor:**
```kotlin
class ImportViewModel(
    private val csvParser: CsvParser,
    private val pdfParser: PdfParser,
    private val duplicateDetector: DuplicateDetector,
    private val transactionRepository: TransactionRepository,
    private val importRepository: ImportRepository
)
```

**Changes:**
- Remove simulated import progress
- Wire existing Sprint 01 parsers from `services/import/`
- Track import jobs via `importRepository`
- Real progress reporting

#### Task 3.7: CorrectionViewModel

**Updated Constructor:**
```kotlin
class CorrectionViewModel(
    private val correctionProcessor: CorrectionProcessor,
    private val correctionRepository: CorrectionRepository
)
```

**Changes:**
- Remove optional null dependencies
- Process corrections via `correctionProcessor.processCorrection()`

#### Task 3.8: SettingsViewModel

**Updated Constructor:**
```kotlin
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val keyManager: KeyManager,
    private val backupService: BackupService
)
```

**Changes:**
- Load settings from `settingsRepository.getAllSettings()`
- Backup/restore via `backupService`
- Security settings via existing `keyManager`

#### Commit After Phase 3
```
feat(sprint06): Wire all ViewModels to real repositories and services
```

---

### Phase 4: Testing & Validation

**Objective:** Comprehensive testing and platform verification

#### Unit Tests for New Services

| Service | Test Count | Key Test Cases |
|---------|------------|----------------|
| SettingsRepository | ~8 | Get/set theme, currency, threshold; defaults; persistence |
| ReviewQueueManager | ~10 | Auto-add low confidence, manual flag, approve, dismiss, count |
| BackupService | ~10 | Create backup, restore, encryption round-trip, corrupted file |
| CorrectionProcessor | ~8 | Single correction, batch, prior updates |
| ParticipantRepository | ~8 | CRUD, group membership, search |

#### ViewModel Integration Tests

Each ViewModel: 3-5 integration tests
- Initial load from real repository
- User actions persist to database
- State updates reflect repository changes

#### Platform-Specific Tests

- **Android:** Emulator tests for database operations
- **Desktop:** JVM tests for file backup/restore paths

#### E2E Smoke Test

Critical path verification:
1. Import CSV file → transactions appear
2. View transactions → filter/search works
3. Review low-confidence items → approve category
4. Create backup → verify file created
5. Restore backup → data intact

#### Commit After Phase 4
```
test(sprint06): Add comprehensive tests for services and ViewModel integration
```

---

## Koin Module Configuration

### Final AppModule Structure

```kotlin
// AppModule.kt

val repositoryModule: Module = module {
    // Existing Sprint 05 repositories
    single<AccountRepository> { SqlDelightAccountRepository(get(), Dispatchers.Default) }
    single<CategoryRepository> { SqlDelightCategoryRepository(get(), Dispatchers.Default) }
    single<RuleRepository> { SqlDelightRuleRepository(get(), Dispatchers.Default) }
    single<ImportRepository> { SqlDelightImportRepository(get(), Dispatchers.Default) }
    single<TransactionRepository> { SqlDelightTransactionRepository(get(), Dispatchers.Default) }
    single<StatisticsRepository> { SqlDelightStatisticsRepository(get(), Dispatchers.Default) }
    single<ReceiptRepository> { SqlDelightReceiptRepository(get(), Dispatchers.Default) }

    // New Sprint 06 repositories
    single<SettingsRepository> { SqlDelightSettingsRepository(get(), Dispatchers.Default) }
    single<ParticipantRepository> { SqlDelightParticipantRepository(get(), Dispatchers.Default) }
}

val serviceModule: Module = module {
    // New Sprint 06 services
    single<ReviewQueueManager> { ReviewQueueManagerImpl(get(), get(), Dispatchers.Default) }
    single<BackupService> { BackupServiceImpl(get(), get()) }
    single<CorrectionProcessor> { CorrectionProcessorImpl(get(), get(), get()) }

    // Existing Sprint 01 services (wire if not already)
    single<CsvParser> { CsvParserImpl() }
    single<PdfParser> { PdfParserImpl() }
    single<DuplicateDetector> { DuplicateDetectorImpl() }
    single<KeyManager> { KeyManagerImpl(get()) }
}

val viewModelModule: Module = module {
    factory { DashboardViewModel(get(), get(), get(), get()) }
    factory { TransactionsViewModel(get(), get()) }
    factory { CategoriesViewModel(get(), get()) }
    factory { ReceiptsViewModel(get(), get()) }
    factory { ReviewViewModel(get(), get(), get()) }
    factory { ImportViewModel(get(), get(), get(), get(), get()) }
    factory { CorrectionViewModel(get(), get()) }
    factory { SettingsViewModel(get(), get(), get()) }
}

val appModule: Module = module {
    includes(repositoryModule, serviceModule, viewModelModule)
}
```

---

## Success Criteria

| Criteria | Target |
|----------|--------|
| ViewModels wired | 8/8 using real repositories |
| Mock data removed | 0 `generateMock*()` methods |
| New services | 4/4 production-ready |
| Test coverage | >80% on new code |
| Platform builds | Android APK + Desktop JAR compile |
| E2E smoke test | Import → View → Categorize → Backup works |

---

## PM Agent Orchestration Instructions

### Overview

The PM Agent should orchestrate Sprint 06 using **parallel subagents** where tasks are independent, and **sequential execution** where dependencies exist.

### Recommended Orchestration Strategy

#### Step 1: Phase 1 - Schema & Infrastructure (Sequential)

Launch a single agent to handle all schema changes:

```
Agent: schema-infrastructure
Tasks:
  1. Create ReviewQueue.sq with all queries
  2. Create Settings.sq with all queries
  3. Verify/update Participant.sq
  4. Update AppModule.kt with new module structure

Commit when complete.
```

#### Step 2: Phase 2 - Core Services (Parallel)

Launch 5 parallel agents (tasks are independent):

```
Agent 1: settings-repository
  - Create SettingsRepository interface
  - Create SqlDelightSettingsRepository implementation
  - Create AppSettings model
  - Write 8 unit tests

Agent 2: review-queue-manager
  - Create ReviewQueueManager interface
  - Create ReviewQueueManagerImpl
  - Create ReviewItem model
  - Create ReviewQueueMapper
  - Write 10 unit tests

Agent 3: participant-repository
  - Create/verify ParticipantRepository interface
  - Create SqlDelightParticipantRepository
  - Create ParticipantMapper
  - Write 8 unit tests

Agent 4: correction-processor
  - Create CorrectionProcessor interface
  - Create CorrectionProcessorImpl (wrap existing learner)
  - Write 8 unit tests

Agent 5: backup-service
  - Create BackupService interface
  - Create BackupServiceImpl
  - Create BackupResult/BackupMetadata models
  - Write 10 unit tests
```

Wait for all Phase 2 agents to complete. Commit.

#### Step 3: Phase 3 - ViewModel Integration (Parallel Groups)

Launch 2 parallel agent groups:

```
Agent Group A (4 ViewModels - simpler):
  - DashboardViewModel wiring
  - TransactionsViewModel wiring
  - CategoriesViewModel wiring
  - ReceiptsViewModel wiring

Agent Group B (4 ViewModels - need new services):
  - ReviewViewModel wiring
  - ImportViewModel wiring
  - CorrectionViewModel wiring
  - SettingsViewModel wiring
```

Or launch 8 parallel agents (one per ViewModel) if resources allow.

Wait for all to complete. Commit.

#### Step 4: Phase 4 - Testing (Parallel)

Launch testing agents:

```
Agent 1: integration-tests
  - ViewModel integration tests

Agent 2: platform-tests
  - Android emulator tests
  - Desktop JVM tests

Agent 3: e2e-smoke-test
  - Critical path validation
```

Wait for all to complete. Commit.

### Agent Task Templates

Each agent should receive:

1. **Clear scope** - Which files to create/modify
2. **Interface definitions** - Copy from this document
3. **Test requirements** - Minimum test count and key cases
4. **Commit instruction** - What message to use

### Error Handling

If an agent fails:
1. Check the error output
2. If dependency missing: ensure Phase 1 completed
3. If compilation error: may need to adjust imports/packages
4. Retry with additional context

### Progress Tracking

Use `TodoWrite` to track:
- [ ] Phase 1: Schema & Infrastructure
- [ ] Phase 2: SettingsRepository
- [ ] Phase 2: ReviewQueueManager
- [ ] Phase 2: ParticipantRepository
- [ ] Phase 2: CorrectionProcessor
- [ ] Phase 2: BackupService
- [ ] Phase 3: DashboardViewModel
- [ ] Phase 3: TransactionsViewModel
- [ ] Phase 3: CategoriesViewModel
- [ ] Phase 3: ReceiptsViewModel
- [ ] Phase 3: ReviewViewModel
- [ ] Phase 3: ImportViewModel
- [ ] Phase 3: CorrectionViewModel
- [ ] Phase 3: SettingsViewModel
- [ ] Phase 4: Integration tests
- [ ] Phase 4: Platform tests
- [ ] Phase 4: E2E smoke test

### Verification Commands

After each phase:
```bash
./gradlew :shared:compileKotlinDesktop  # Verify compilation
./gradlew :shared:desktopTest           # Run tests
./gradlew ktlintCheck                   # Code style
```

---

## File Summary

### New Files to Create (~20)

**Schema (2):**
- `ReviewQueue.sq`
- `Settings.sq`

**Interfaces (5):**
- `SettingsRepository.kt`
- `ReviewQueueManager.kt`
- `ParticipantRepository.kt` (if not exists)
- `CorrectionProcessor.kt`
- `BackupService.kt`

**Implementations (5):**
- `SqlDelightSettingsRepository.kt`
- `ReviewQueueManagerImpl.kt`
- `SqlDelightParticipantRepository.kt`
- `CorrectionProcessorImpl.kt`
- `BackupServiceImpl.kt`

**Models (3):**
- `AppSettings.kt`
- `ReviewItem.kt`
- `BackupResult.kt` / `BackupMetadata.kt`

**Mappers (2):**
- `ReviewQueueMapper.kt`
- `ParticipantMapper.kt`

**Tests (5):**
- `SqlDelightSettingsRepositoryTest.kt`
- `ReviewQueueManagerTest.kt`
- `SqlDelightParticipantRepositoryTest.kt`
- `CorrectionProcessorTest.kt`
- `BackupServiceTest.kt`

### Files to Modify (~8)

- `AppModule.kt` - Add new modules
- `DashboardViewModel.kt` - Wire dependencies
- `TransactionsViewModel.kt` - Wire dependencies
- `CategoriesViewModel.kt` - Wire dependencies
- `ReceiptsViewModel.kt` - Wire dependencies
- `ReviewViewModel.kt` - Wire dependencies
- `ImportViewModel.kt` - Wire dependencies
- `CorrectionViewModel.kt` - Wire dependencies
- `SettingsViewModel.kt` - Wire dependencies

---

*Document created: 2026-01-18*
*Ready for PM Agent orchestration*
