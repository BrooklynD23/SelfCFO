package com.ledgerlens.data.repositories

import com.ledgerlens.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Domain model for a financial account.
 */
data class AccountEntity(
    val id: String,
    val displayName: String,
    val institutionName: String?,
    val accountType: AccountType,
    val accountNumberMasked: String?,
    val currencyCode: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Types of financial accounts.
 */
enum class AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    INVESTMENT,
    LOAN,
    OTHER
}

/**
 * Account with balance information.
 */
data class AccountWithBalance(
    val account: AccountEntity,
    val transactionCount: Int,
    val netBalanceMinorUnits: Long,
    val lastTransactionDate: LocalDate?
)

/**
 * Repository interface for account data access.
 */
interface AccountRepository {
    /**
     * Get all accounts.
     */
    fun getAllAccounts(): Flow<List<AccountEntity>>

    /**
     * Get only active accounts.
     */
    fun getActiveAccounts(): Flow<List<AccountEntity>>

    /**
     * Get accounts with balance info.
     */
    fun getAccountsWithBalances(): Flow<List<AccountWithBalance>>

    /**
     * Get an account by ID.
     */
    fun getAccount(id: String): Flow<AccountEntity?>

    /**
     * Insert a new account.
     */
    suspend fun insertAccount(account: AccountEntity)

    /**
     * Update an account.
     */
    suspend fun updateAccount(account: AccountEntity)

    /**
     * Delete an account (soft delete - marks as inactive).
     */
    suspend fun deleteAccount(id: String)

    /**
     * Hard delete an account and its transactions.
     * Use with caution!
     */
    suspend fun hardDeleteAccount(id: String)

    /**
     * Check if an account exists.
     */
    suspend fun exists(id: String): Boolean

    /**
     * Get account by name (for import matching).
     */
    suspend fun findByName(name: String): AccountEntity?
}
