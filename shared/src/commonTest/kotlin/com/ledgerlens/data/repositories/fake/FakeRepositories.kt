package com.ledgerlens.data.repositories.fake

import com.ledgerlens.data.repositories.*
import com.ledgerlens.domain.Money
import com.ledgerlens.security.BackupBundle
import com.ledgerlens.security.KeyManager
import com.ledgerlens.security.RecoveryKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Fake TransactionRepository for testing ViewModels.
 */
class FakeTransactionRepository : TransactionRepository {
    private val transactions = MutableStateFlow<Map<String, Transaction>>(emptyMap())

    fun addTransaction(transaction: Transaction) {
        transactions.value = transactions.value + (transaction.id to transaction)
    }

    fun setTransactions(list: List<Transaction>) {
        transactions.value = list.associateBy { it.id }
    }

    fun clear() {
        transactions.value = emptyMap()
    }

    override fun getTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
        return transactions.map { map ->
            val startDate = filter.startDate
            val endDate = filter.endDate
            val searchQuery = filter.searchQuery
            map.values.filter { tx ->
                (filter.accountId == null || tx.accountId == filter.accountId) &&
                    (filter.categoryId == null || tx.categoryId == filter.categoryId) &&
                    (startDate == null || tx.postedDate >= startDate) &&
                    (endDate == null || tx.postedDate <= endDate) &&
                    (
                        searchQuery.isNullOrBlank() ||
                            tx.descriptionRaw.contains(searchQuery, ignoreCase = true) ||
                            tx.merchantNormalized.contains(searchQuery, ignoreCase = true)
                        ) &&
                    (filter.includeExcluded || !tx.isExcluded) &&
                    (!filter.onlyUnreviewed || !tx.isReviewed)
            }.sortedByDescending { it.postedDate }
        }
    }

    override fun getTransaction(id: String): Flow<Transaction?> {
        return transactions.map { it[id] }
    }

    override fun getRecentTransactions(limit: Int): Flow<List<Transaction>> {
        return transactions.map { map ->
            map.values.sortedByDescending { it.postedDate }.take(limit)
        }
    }

    override fun getTransactionsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        return transactions.map { map ->
            map.values.filter { it.postedDate in startDate..endDate }
                .sortedByDescending { it.postedDate }
        }
    }

    override fun getTransactionsByCategory(categoryId: String): Flow<List<Transaction>> {
        return transactions.map { map ->
            map.values.filter { it.categoryId == categoryId }
                .sortedByDescending { it.postedDate }
        }
    }

    override fun getTransactionsNeedingReview(): Flow<List<Transaction>> {
        return transactions.map { map ->
            map.values.filter { tx ->
                val confidence = tx.categoryConfidence
                !tx.isReviewed && (confidence == null || confidence < 0.5f)
            }.sortedByDescending { it.postedDate }
        }
    }

    override suspend fun insertTransaction(transaction: Transaction): String {
        transactions.value = transactions.value + (transaction.id to transaction)
        return transaction.id
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactions.value = transactions.value + (transaction.id to transaction)
    }

    override suspend fun deleteTransaction(id: String) {
        transactions.value = transactions.value - id
    }

    override suspend fun updateCategory(transactionId: String, categoryId: String, confidence: Float, reason: String) {
        val tx = transactions.value[transactionId] ?: return
        transactions.value = transactions.value + (
            transactionId to tx.copy(
                categoryId = categoryId,
                categoryConfidence = confidence,
                categoryReason = reason
            )
            )
    }

    override suspend fun markAsReviewed(transactionId: String) {
        val tx = transactions.value[transactionId] ?: return
        transactions.value = transactions.value + (transactionId to tx.copy(isReviewed = true))
    }

    override suspend fun markAsExcluded(transactionId: String, excluded: Boolean) {
        val tx = transactions.value[transactionId] ?: return
        transactions.value = transactions.value + (transactionId to tx.copy(isExcluded = excluded))
    }

    override suspend fun updateNotes(transactionId: String, notes: String) {
        val tx = transactions.value[transactionId] ?: return
        transactions.value = transactions.value + (transactionId to tx.copy(notes = notes))
    }

    override suspend fun countByAccount(accountId: String): Long {
        return transactions.value.values.count { it.accountId == accountId }.toLong()
    }

    override suspend fun countAll(): Long {
        return transactions.value.size.toLong()
    }
}

/**
 * Fake CategoryRepository for testing ViewModels.
 */
class FakeCategoryRepository : CategoryRepository {
    private val categories = MutableStateFlow<Map<String, CategoryEntity>>(emptyMap())

    fun addCategory(category: CategoryEntity) {
        categories.value = categories.value + (category.id to category)
    }

    fun setCategories(list: List<CategoryEntity>) {
        categories.value = list.associateBy { it.id }
    }

    fun clear() {
        categories.value = emptyMap()
    }

    override fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categories.map { map ->
            map.values.sortedWith(compareBy({ it.sortOrder }, { it.name }))
        }
    }

    override fun getCategory(id: String): Flow<CategoryEntity?> {
        return categories.map { it[id] }
    }

    override fun getCategoriesByParent(parentId: String): Flow<List<CategoryEntity>> {
        return categories.map { map ->
            map.values.filter { it.parentId == parentId }
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))
        }
    }

    override fun getTopLevelCategories(): Flow<List<CategoryEntity>> {
        return categories.map { map ->
            map.values.filter { it.parentId == null }
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))
        }
    }

    override fun getCategoriesWithStats(): Flow<List<CategoryWithStats>> {
        return categories.map { map ->
            map.values.map { cat ->
                CategoryWithStats(cat, 0, 0L, 0L)
            }.sortedWith(compareBy({ it.category.sortOrder }, { it.category.name }))
        }
    }

    override suspend fun insertCategory(category: CategoryEntity) {
        categories.value = categories.value + (category.id to category)
    }

    override suspend fun updateCategory(category: CategoryEntity) {
        categories.value = categories.value + (category.id to category)
    }

    override suspend fun deleteCategory(id: String) {
        categories.value = categories.value - id
    }

    override suspend fun seedDefaultCategories() {
        val defaults = listOf(
            CategoryEntity("groceries", "Groceries", null, true, false, "🛒", "#4CAF50", 0),
            CategoryEntity("dining", "Dining", null, true, false, "🍽️", "#FF9800", 1),
            CategoryEntity("transportation", "Transportation", null, true, false, "🚗", "#2196F3", 2),
            CategoryEntity("utilities", "Utilities", null, true, false, "💡", "#9C27B0", 3),
            CategoryEntity("entertainment", "Entertainment", null, true, false, "🎬", "#E91E63", 4)
        )
        categories.value = categories.value + defaults.associateBy { it.id }
    }

    override suspend fun exists(id: String): Boolean {
        return id in categories.value
    }
}

/**
 * Fake ReceiptRepository for testing ViewModels.
 */
class FakeReceiptRepository : ReceiptRepository {
    private val receipts = MutableStateFlow<Map<String, ReceiptEntity>>(emptyMap())
    private val items = MutableStateFlow<Map<String, List<ReceiptItemEntity>>>(emptyMap())
    private val allocations = MutableStateFlow<Map<String, List<ItemAllocationEntity>>>(emptyMap())

    fun addReceipt(receipt: ReceiptEntity) {
        receipts.value = receipts.value + (receipt.id to receipt)
    }

    fun setReceipts(list: List<ReceiptEntity>) {
        receipts.value = list.associateBy { it.id }
    }

    fun clear() {
        receipts.value = emptyMap()
        items.value = emptyMap()
        allocations.value = emptyMap()
    }

    override fun getAllReceipts(): Flow<List<ReceiptEntity>> {
        return receipts.map { map ->
            map.values.sortedByDescending { it.createdAt }
        }
    }

    override fun getReceipt(id: String): Flow<ReceiptEntity?> {
        return receipts.map { it[id] }
    }

    override fun getReceiptWithItems(id: String): Flow<ReceiptWithItems?> {
        return receipts.map { receiptMap ->
            val receipt = receiptMap[id] ?: return@map null
            val receiptItems = items.value[id] ?: emptyList()
            val itemAllocations = receiptItems.associate { item ->
                item.id to (allocations.value[item.id] ?: emptyList())
            }
            ReceiptWithItems(receipt, receiptItems, itemAllocations)
        }
    }

    override fun getReceiptsByTransaction(transactionId: String): Flow<List<ReceiptEntity>> {
        return receipts.map { map ->
            map.values.filter { it.linkedTransactionId == transactionId }
        }
    }

    override fun searchReceipts(query: String): Flow<List<ReceiptEntity>> {
        return receipts.map { map ->
            map.values.filter { receipt ->
                receipt.merchantName?.contains(query, ignoreCase = true) == true ||
                    receipt.ocrText?.contains(query, ignoreCase = true) == true
            }
        }
    }

    override suspend fun insertReceipt(receipt: ReceiptEntity) {
        receipts.value = receipts.value + (receipt.id to receipt)
    }

    override suspend fun updateReceipt(receipt: ReceiptEntity) {
        receipts.value = receipts.value + (receipt.id to receipt)
    }

    override suspend fun deleteReceipt(id: String) {
        receipts.value = receipts.value - id
        items.value = items.value - id
    }

    override suspend fun linkToTransaction(receiptId: String, transactionId: String) {
        val receipt = receipts.value[receiptId] ?: return
        receipts.value = receipts.value + (receiptId to receipt.copy(linkedTransactionId = transactionId))
    }

    override suspend fun insertReceiptItems(itemsList: List<ReceiptItemEntity>) {
        val byReceipt = itemsList.groupBy { it.receiptId }
        items.value = items.value + byReceipt.mapValues { (receiptId, newItems) ->
            (items.value[receiptId] ?: emptyList()) + newItems
        }
    }

    override suspend fun updateReceiptItems(itemsList: List<ReceiptItemEntity>) {
        itemsList.forEach { item ->
            val current = items.value[item.receiptId]?.toMutableList() ?: mutableListOf()
            val index = current.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                current[index] = item
                items.value = items.value + (item.receiptId to current)
            }
        }
    }

    override suspend fun deleteReceiptItems(receiptId: String) {
        items.value = items.value - receiptId
    }

    override suspend fun setItemAllocations(allocationsList: List<ItemAllocationEntity>) {
        val byItem = allocationsList.groupBy { it.receiptItemId }
        allocations.value = allocations.value + byItem
    }

    override suspend fun countAll(): Long {
        return receipts.value.size.toLong()
    }
}

/**
 * Fake ImportRepository for testing ViewModels.
 */
class FakeImportRepository : ImportRepository {
    private val jobs = MutableStateFlow<Map<String, ImportJobEntity>>(emptyMap())
    private val files = MutableStateFlow<Map<String, List<SourceFileEntity>>>(emptyMap())
    private val importedHashes = mutableSetOf<String>()

    fun addJob(job: ImportJobEntity) {
        jobs.value = jobs.value + (job.id to job)
    }

    fun clear() {
        jobs.value = emptyMap()
        files.value = emptyMap()
        importedHashes.clear()
    }

    override fun getAllImportJobs(): Flow<List<ImportJobEntity>> {
        return jobs.map { map ->
            map.values.sortedByDescending { it.startedAt }
        }
    }

    override fun getRecentImportJobs(limit: Int): Flow<List<ImportJobEntity>> {
        return jobs.map { map ->
            map.values.sortedByDescending { it.startedAt }.take(limit)
        }
    }

    override fun getImportJob(id: String): Flow<ImportJobEntity?> {
        return jobs.map { it[id] }
    }

    override fun getImportJobWithFiles(id: String): Flow<ImportJobWithFiles?> {
        return jobs.map { jobMap ->
            val job = jobMap[id] ?: return@map null
            val jobFiles = files.value[id] ?: emptyList()
            ImportJobWithFiles(job, jobFiles)
        }
    }

    override suspend fun createImportJob(job: ImportJobEntity): String {
        jobs.value = jobs.value + (job.id to job)
        return job.id
    }

    override suspend fun updateProgress(id: String, importedCount: Int, duplicatesSkipped: Int, errorsCount: Int) {
        val job = jobs.value[id] ?: return
        jobs.value = jobs.value + (
            id to job.copy(
                importedCount = importedCount,
                duplicatesSkipped = duplicatesSkipped,
                errorsCount = errorsCount,
                status = ImportStatus.IN_PROGRESS
            )
            )
    }

    override suspend fun markCompleted(id: String, withErrors: Boolean) {
        val job = jobs.value[id] ?: return
        jobs.value = jobs.value + (
            id to job.copy(
                status = if (withErrors) ImportStatus.COMPLETED_WITH_ERRORS else ImportStatus.COMPLETED,
                completedAt = Clock.System.now()
            )
            )
    }

    override suspend fun markFailed(id: String, errorDetails: String) {
        val job = jobs.value[id] ?: return
        jobs.value = jobs.value + (
            id to job.copy(
                status = ImportStatus.FAILED,
                errorDetails = errorDetails,
                completedAt = Clock.System.now()
            )
            )
    }

    override suspend fun markCancelled(id: String) {
        val job = jobs.value[id] ?: return
        jobs.value = jobs.value + (
            id to job.copy(
                status = ImportStatus.CANCELLED,
                completedAt = Clock.System.now()
            )
            )
    }

    override suspend fun insertSourceFile(file: SourceFileEntity) {
        val current = files.value[file.importJobId] ?: emptyList()
        files.value = files.value + (file.importJobId to (current + file))
        importedHashes.add(file.fileHash)
    }

    override suspend fun isFileAlreadyImported(fileHash: String): Boolean {
        return fileHash in importedHashes
    }

    override suspend fun deleteImportJob(id: String) {
        jobs.value = jobs.value - id
        files.value = files.value - id
    }
}

/**
 * Fake RuleRepository for testing ViewModels.
 */
class FakeRuleRepository : RuleRepository {
    private val rules = MutableStateFlow<Map<String, RuleEntity>>(emptyMap())

    fun addRule(rule: RuleEntity) {
        rules.value = rules.value + (rule.id to rule)
    }

    fun setRules(list: List<RuleEntity>) {
        rules.value = list.associateBy { it.id }
    }

    fun clear() {
        rules.value = emptyMap()
    }

    override fun getAllRules(): Flow<List<RuleEntity>> {
        return rules.map { map ->
            map.values.sortedBy { it.priority }
        }
    }

    override fun getEnabledRules(): Flow<List<RuleEntity>> {
        return rules.map { map ->
            map.values.filter { it.isEnabled }.sortedBy { it.priority }
        }
    }

    override fun getRule(id: String): Flow<RuleEntity?> {
        return rules.map { it[id] }
    }

    override suspend fun insertRule(rule: RuleEntity) {
        rules.value = rules.value + (rule.id to rule)
    }

    override suspend fun updateRule(rule: RuleEntity) {
        rules.value = rules.value + (rule.id to rule)
    }

    override suspend fun deleteRule(id: String) {
        rules.value = rules.value - id
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        val rule = rules.value[id] ?: return
        rules.value = rules.value + (id to rule.copy(isEnabled = enabled))
    }

    override suspend fun updatePriority(id: String, priority: Int) {
        val rule = rules.value[id] ?: return
        rules.value = rules.value + (id to rule.copy(priority = priority))
    }

    override suspend fun incrementMatchCount(id: String) {
        val rule = rules.value[id] ?: return
        rules.value = rules.value + (id to rule.copy(matchCount = rule.matchCount + 1))
    }

    override suspend fun reorderRules(ruleIds: List<String>) {
        val newRules = rules.value.toMutableMap()
        ruleIds.forEachIndexed { index, id ->
            val rule = newRules[id] ?: return@forEachIndexed
            newRules[id] = rule.copy(priority = index)
        }
        rules.value = newRules
    }
}

/**
 * Test data factory for creating test fixtures.
 */
object TestDataFactory {
    private val today: LocalDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    fun createTransaction(
        id: String = "tx-${System.nanoTime()}",
        accountId: String? = "account-1",
        postedDate: LocalDate = today,
        description: String = "Test Transaction",
        merchantNormalized: String = "TEST MERCHANT",
        amountMinorUnits: Long = -2500,
        currencyCode: String = "USD",
        categoryId: String? = "groceries",
        categoryConfidence: Float? = 0.85f,
        isReviewed: Boolean = false,
        isExcluded: Boolean = false
    ) = Transaction(
        id = id,
        accountId = accountId,
        postedDate = postedDate,
        transactionDate = postedDate,
        descriptionRaw = description,
        merchantDisplay = merchantNormalized,
        merchantNormalized = merchantNormalized,
        amount = Money(amountMinorUnits, currencyCode),
        categoryId = categoryId,
        categoryConfidence = categoryConfidence,
        categoryReason = if (categoryId != null) "ml_classification" else null,
        tags = emptyList(),
        notes = "",
        isTransfer = false,
        isExcluded = isExcluded,
        isReviewed = isReviewed,
        hashFingerprint = "hash-$id",
        importedAt = Clock.System.now().toEpochMilliseconds()
    )

    fun createCategory(
        id: String = "cat-${System.nanoTime()}",
        name: String = "Test Category",
        parentId: String? = null,
        icon: String? = "📁",
        color: String? = "#808080",
        isSystemDefault: Boolean = false
    ) = CategoryEntity(
        id = id,
        name = name,
        parentId = parentId,
        isSystemDefault = isSystemDefault,
        isUserCustom = !isSystemDefault,
        icon = icon,
        color = color,
        sortOrder = 0
    )

    fun createReceipt(
        id: String = "receipt-${System.nanoTime()}",
        merchantName: String? = "Test Store",
        totalAmountMinorUnits: Long? = 5000,
        currencyCode: String = "USD",
        linkedTransactionId: String? = null
    ) = ReceiptEntity(
        id = id,
        imagePath = "/path/to/receipt.jpg",
        thumbnailPath = "/path/to/receipt_thumb.jpg",
        merchantName = merchantName,
        totalAmount = totalAmountMinorUnits?.let { Money(it, currencyCode) },
        receiptDate = today,
        linkedTransactionId = linkedTransactionId,
        ocrText = "Sample OCR text",
        ocrConfidence = 0.9f,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds()
    )

    fun createImportJob(
        id: String = "import-${System.nanoTime()}",
        fileName: String = "transactions.csv",
        sourceType: ImportSourceType = ImportSourceType.CSV,
        status: ImportStatus = ImportStatus.COMPLETED,
        totalRows: Int = 100,
        importedCount: Int = 95,
        duplicatesSkipped: Int = 5
    ) = ImportJobEntity(
        id = id,
        sourceFileName = fileName,
        sourceType = sourceType,
        bankTemplate = "chase_checking",
        status = status,
        totalRows = totalRows,
        importedCount = importedCount,
        duplicatesSkipped = duplicatesSkipped,
        errorsCount = 0,
        errorDetails = null,
        startedAt = Clock.System.now(),
        completedAt = if (status == ImportStatus.COMPLETED) Clock.System.now() else null
    )

    fun createRule(
        id: String = "rule-${System.nanoTime()}",
        name: String = "Test Rule",
        targetCategoryId: String = "groceries",
        priority: Int = 0,
        isEnabled: Boolean = true
    ) = RuleEntity(
        id = id,
        name = name,
        conditionsJson = """{"merchant_contains": "WALMART"}""",
        targetCategoryId = targetCategoryId,
        priority = priority,
        isEnabled = isEnabled,
        matchCount = 0,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds()
    )
}

/**
 * Fake KeyManager for testing SettingsViewModel.
 */
class FakeKeyManager : KeyManager {
    private var initialized = false
    private var unlocked = false
    private var currentPassphrase: String? = null

    // Control test behavior
    var shouldFailChangePassphrase = false
    var shouldFailCryptoErase = false

    override suspend fun initializeKeys(passphrase: String): Result<RecoveryKey> {
        initialized = true
        currentPassphrase = passphrase
        unlocked = true
        return Result.success(
            RecoveryKey(
                listOf(
                    "abandon", "ability", "able", "about", "above", "absent",
                    "absorb", "abstract", "absurd", "abuse", "access", "accident",
                    "account", "accuse", "achieve", "acid", "acoustic", "acquire",
                    "across", "act", "action", "actor", "actress", "actual"
                )
            )
        )
    }

    override suspend fun unlock(passphrase: String): Result<Unit> {
        return if (passphrase == currentPassphrase) {
            unlocked = true
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("Invalid passphrase"))
        }
    }

    override fun lock() {
        unlocked = false
    }

    override fun isUnlocked(): Boolean = unlocked

    override suspend fun isInitialized(): Boolean = initialized

    override suspend fun getDatabaseKey(): Result<ByteArray> {
        return if (unlocked) {
            Result.success(ByteArray(32) { it.toByte() })
        } else {
            Result.failure(IllegalStateException("Not unlocked"))
        }
    }

    override suspend fun getFileKey(fileId: String): Result<ByteArray> {
        return if (unlocked) {
            Result.success(ByteArray(32) { (it + fileId.hashCode()).toByte() })
        } else {
            Result.failure(IllegalStateException("Not unlocked"))
        }
    }

    override suspend fun exportForBackup(exportPassphrase: String): Result<BackupBundle> {
        return Result.success(
            BackupBundle(
                encryptedKek = ByteArray(32),
                salt = ByteArray(16),
                version = 1
            )
        )
    }

    override suspend fun importFromBackup(
        backup: BackupBundle,
        exportPassphrase: String,
        newPassphrase: String
    ): Result<Unit> {
        currentPassphrase = newPassphrase
        initialized = true
        unlocked = true
        return Result.success(Unit)
    }

    override suspend fun cryptoErase(): Result<Unit> {
        return if (shouldFailCryptoErase) {
            Result.failure(Exception("Crypto erase failed"))
        } else {
            initialized = false
            unlocked = false
            currentPassphrase = null
            Result.success(Unit)
        }
    }

    override suspend fun changePassphrase(currentPassphrase: String, newPassphrase: String): Result<Unit> {
        return if (shouldFailChangePassphrase) {
            Result.failure(Exception("Failed to change passphrase"))
        } else if (currentPassphrase != this.currentPassphrase) {
            Result.failure(IllegalArgumentException("Invalid current passphrase"))
        } else {
            this.currentPassphrase = newPassphrase
            Result.success(Unit)
        }
    }
}

/**
 * Fake StatisticsRepository for testing DashboardViewModel.
 */
class FakeStatisticsRepository : StatisticsRepository {
    private val _pendingReviewCount = MutableStateFlow(0)
    private val _uncategorizedCount = MutableStateFlow(0)

    fun setPendingReviewCount(count: Int) {
        _pendingReviewCount.value = count
    }

    fun setUncategorizedCount(count: Int) {
        _uncategorizedCount.value = count
    }

    override fun getMonthlyStats(year: Int, month: Int): Flow<MonthlyStats> {
        return MutableStateFlow(
            MonthlyStats(
                year = year,
                month = month,
                totalSpending = Money(-100000, "USD"),
                totalIncome = Money(500000, "USD"),
                netChange = Money(400000, "USD"),
                transactionCount = 25
            )
        )
    }

    override fun getMonthlyStatsList(
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int
    ): Flow<List<MonthlyStats>> {
        return MutableStateFlow(
            listOf(
                MonthlyStats(startYear, startMonth, Money(-80000, "USD"), Money(480000, "USD"), Money(400000, "USD"), 20),
                MonthlyStats(endYear, endMonth, Money(-100000, "USD"), Money(500000, "USD"), Money(400000, "USD"), 25)
            )
        )
    }

    override fun getCategoryBreakdown(year: Int, month: Int): Flow<List<CategorySpendingStats>> {
        return MutableStateFlow(
            listOf(
                CategorySpendingStats("groceries", "Groceries", "#4CAF50", Money(-40000, "USD"), 10, 0.4f),
                CategorySpendingStats("dining", "Dining", "#FF9800", Money(-30000, "USD"), 8, 0.3f),
                CategorySpendingStats("transportation", "Transportation", "#2196F3", Money(-20000, "USD"), 5, 0.2f),
                CategorySpendingStats("utilities", "Utilities", "#9C27B0", Money(-10000, "USD"), 2, 0.1f)
            )
        )
    }

    override fun getCategoryBreakdownForRange(
        startDate: kotlinx.datetime.LocalDate,
        endDate: kotlinx.datetime.LocalDate
    ): Flow<List<CategorySpendingStats>> {
        return getCategoryBreakdown(startDate.year, startDate.monthNumber)
    }

    override fun getMonthComparison(year: Int, month: Int): Flow<MonthComparison> {
        val current = MonthlyStats(year, month, Money(-100000, "USD"), Money(500000, "USD"), Money(400000, "USD"), 25)
        val previous = MonthlyStats(
            if (month == 1) year - 1 else year,
            if (month == 1) 12 else month - 1,
            Money(-90000, "USD"),
            Money(480000, "USD"),
            Money(390000, "USD"),
            22
        )
        return MutableStateFlow(
            MonthComparison(
                currentMonth = current,
                previousMonth = previous,
                spendingChangePercent = 11.1f,
                incomeChangePercent = 4.2f
            )
        )
    }

    override fun getTopMerchants(
        limit: Int,
        startDate: kotlinx.datetime.LocalDate?,
        endDate: kotlinx.datetime.LocalDate?
    ): Flow<List<TopMerchant>> {
        return MutableStateFlow(
            listOf(
                TopMerchant("WALMART", Money(-25000, "USD"), 5),
                TopMerchant("AMAZON", Money(-20000, "USD"), 8),
                TopMerchant("STARBUCKS", Money(-15000, "USD"), 12)
            )
        )
    }

    override fun getDailySpending(year: Int, month: Int): Flow<Map<Int, Money>> {
        return MutableStateFlow(
            (1..28).associateWith { day ->
                Money((-1000..(-5000)).random().toLong(), "USD")
            }
        )
    }

    override fun getYearToDateStats(year: Int): Flow<MonthlyStats> {
        return MutableStateFlow(
            MonthlyStats(
                year = year,
                month = 0, // YTD
                totalSpending = Money(-1200000, "USD"),
                totalIncome = Money(6000000, "USD"),
                netChange = Money(4800000, "USD"),
                transactionCount = 300
            )
        )
    }

    override fun getPendingReviewCount(): Flow<Int> = _pendingReviewCount

    override fun getUncategorizedCount(): Flow<Int> = _uncategorizedCount
}
