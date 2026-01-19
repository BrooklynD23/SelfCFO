package com.ledgerlens.data.mappers

import com.ledgerlens.data.repositories.AccountEntity
import com.ledgerlens.data.repositories.AccountType
import com.ledgerlens.data.repositories.AccountWithBalance
import com.ledgerlens.db.Account
import com.ledgerlens.db.Account_balance
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Maps database Account entities to domain AccountEntity models.
 */
object AccountMapper {
    /**
     * Convert database Account to domain AccountEntity.
     */
    fun toDomain(db: Account): AccountEntity = AccountEntity(
        id = db.id,
        displayName = db.display_name,
        institutionName = db.institution_name,
        accountType = db.account_type.toAccountType(),
        accountNumberMasked = db.last_four?.let { "****$it" },
        currencyCode = db.currency_code,
        isActive = db.is_active == 1L,
        createdAt = db.created_at,
        updatedAt = db.created_at // No updated_at in schema, use created_at
    )

    /**
     * Convert domain AccountEntity to database parameters.
     */
    fun toDbParams(entity: AccountEntity): AccountDbParams = AccountDbParams(
        id = entity.id,
        displayName = entity.displayName,
        institutionName = entity.institutionName,
        accountType = entity.accountType.toDbValue(),
        currencyCode = entity.currencyCode,
        lastFour = entity.accountNumberMasked?.takeLast(4),
        isActive = if (entity.isActive) 1L else 0L,
        createdAt = entity.createdAt,
        color = null,
        icon = null,
        notes = null
    )

    /**
     * Convert account_balance view to AccountWithBalance.
     */
    fun toAccountWithBalance(balance: Account_balance, account: Account): AccountWithBalance {
        return AccountWithBalance(
            account = toDomain(account),
            transactionCount = balance.transaction_count?.toInt() ?: 0,
            netBalanceMinorUnits = balance.net_balance_minor ?: 0L,
            lastTransactionDate = balance.last_transaction_date?.let { millis ->
                Instant.fromEpochMilliseconds(millis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            }
        )
    }
}

/**
 * Data class to hold database insert/update parameters.
 */
data class AccountDbParams(
    val id: String,
    val displayName: String,
    val institutionName: String?,
    val accountType: String,
    val currencyCode: String,
    val lastFour: String?,
    val isActive: Long,
    val createdAt: Long,
    val color: String?,
    val icon: String?,
    val notes: String?
)

/**
 * Extension to convert string account type from DB to enum.
 */
private fun String.toAccountType(): AccountType = when (this.lowercase()) {
    "checking" -> AccountType.CHECKING
    "savings" -> AccountType.SAVINGS
    "credit" -> AccountType.CREDIT_CARD
    "investment" -> AccountType.INVESTMENT
    "loan" -> AccountType.LOAN
    else -> AccountType.OTHER
}

/**
 * Extension to convert AccountType enum to DB string value.
 */
private fun AccountType.toDbValue(): String = when (this) {
    AccountType.CHECKING -> "checking"
    AccountType.SAVINGS -> "savings"
    AccountType.CREDIT_CARD -> "credit"
    AccountType.INVESTMENT -> "investment"
    AccountType.LOAN -> "other" // DB doesn't have loan type
    AccountType.OTHER -> "other"
}
