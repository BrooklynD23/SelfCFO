package com.ledgerlens.data.repositories.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ledgerlens.data.mappers.AccountMapper
import com.ledgerlens.data.repositories.AccountEntity
import com.ledgerlens.data.repositories.AccountRepository
import com.ledgerlens.data.repositories.AccountWithBalance
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight implementation of AccountRepository.
 */
class SqlDelightAccountRepository(
    private val database: LedgerLensDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AccountRepository {

    private val accountQueries = database.accountQueries
    private val viewsQueries = database.viewsQueries

    override fun getAllAccounts(): Flow<List<AccountEntity>> {
        return accountQueries.selectAllIncludingInactive()
            .asFlow()
            .mapToList(dispatcher)
            .map { accounts -> accounts.map(AccountMapper::toDomain) }
    }

    override fun getActiveAccounts(): Flow<List<AccountEntity>> {
        return accountQueries.selectAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { accounts -> accounts.map(AccountMapper::toDomain) }
    }

    override fun getAccountsWithBalances(): Flow<List<AccountWithBalance>> {
        // Combine account data with balance view
        return combine(
            accountQueries.selectAll().asFlow().mapToList(dispatcher),
            viewsQueries.selectAccountBalances().asFlow().mapToList(dispatcher)
        ) { accounts, balances ->
            val balanceMap = balances.associateBy { it.account_id }
            accounts.mapNotNull { account ->
                balanceMap[account.id]?.let { balance ->
                    AccountMapper.toAccountWithBalance(balance, account)
                }
            }
        }
    }

    override fun getAccount(id: String): Flow<AccountEntity?> {
        return accountQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(AccountMapper::toDomain) }
    }

    override suspend fun insertAccount(account: AccountEntity) = withContext(dispatcher) {
        val params = AccountMapper.toDbParams(account)
        accountQueries.insert(
            id = params.id,
            display_name = params.displayName,
            institution_name = params.institutionName,
            account_type = params.accountType,
            currency_code = params.currencyCode,
            last_four = params.lastFour,
            is_active = params.isActive,
            created_at = params.createdAt,
            color = params.color,
            icon = params.icon,
            notes = params.notes
        )
    }

    override suspend fun updateAccount(account: AccountEntity) = withContext(dispatcher) {
        val params = AccountMapper.toDbParams(account)
        accountQueries.update(
            display_name = params.displayName,
            institution_name = params.institutionName,
            account_type = params.accountType,
            currency_code = params.currencyCode,
            last_four = params.lastFour,
            is_active = params.isActive,
            color = params.color,
            icon = params.icon,
            notes = params.notes,
            id = params.id
        )
    }

    override suspend fun deleteAccount(id: String) = withContext(dispatcher) {
        accountQueries.deactivate(id)
    }

    override suspend fun hardDeleteAccount(id: String) = withContext(dispatcher) {
        accountQueries.delete(id)
    }

    override suspend fun exists(id: String): Boolean = withContext(dispatcher) {
        accountQueries.exists(id).executeAsOne()
    }

    override suspend fun findByName(name: String): AccountEntity? = withContext(dispatcher) {
        accountQueries.selectByName(name).executeAsOneOrNull()?.let(AccountMapper::toDomain)
    }
}
