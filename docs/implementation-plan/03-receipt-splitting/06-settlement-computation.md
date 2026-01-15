# 06: Settlement Computation

## Overview

Calculate final balances and determine who owes whom based on item allocations and fee distributions.

---

## Implementation Steps

### Step 1: Settlement Models

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/Settlement.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money
import kotlinx.serialization.Serializable

@Serializable
data class Settlement(
    val splitId: String,
    val participantBalances: List<ParticipantBalance>,
    val transactions: List<SettlementTransaction>,
    val totalAmount: Money,
    val currency: String
)

@Serializable
data class ParticipantBalance(
    val participantId: String,
    val participantName: String,
    val itemsSubtotal: Money,
    val taxShare: Money,
    val tipShare: Money,
    val feesShare: Money,
    val totalOwed: Money,
    val amountPaid: Money, // If they paid the bill
    val netBalance: Money  // Positive = owed money, Negative = owes money
)

@Serializable
data class SettlementTransaction(
    val fromParticipantId: String,
    val fromParticipantName: String,
    val toParticipantId: String,
    val toParticipantName: String,
    val amount: Money,
    val status: TransactionStatus = TransactionStatus.PENDING
)

@Serializable
enum class TransactionStatus {
    PENDING,
    COMPLETED,
    CANCELLED
}
```

### Step 2: Settlement Calculator

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/SettlementCalculator.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money

class SettlementCalculator(
    private val itemAllocator: ItemAllocator,
    private val feeDistributionService: FeeDistributionService
) {

    /**
     * Calculate full settlement for a receipt split.
     */
    suspend fun calculateSettlement(
        split: ReceiptSplit,
        fees: ReceiptFees,
        paidByParticipantId: String // Who paid the bill
    ): Settlement {
        val participants = split.participants
        val currency = split.currency

        // 1. Calculate item subtotals per participant
        val itemSubtotals = calculateItemSubtotals(split)

        // 2. Distribute fees
        val feeDistribution = feeDistributionService.distributeFees(split, fees)

        // 3. Calculate total owed per participant
        val balances = participants.map { participant ->
            val itemsSubtotal = itemSubtotals[participant.id] ?: Money(0, currency)
            val taxShare = feeDistribution.taxByParticipant[participant.id] ?: Money(0, currency)
            val tipShare = feeDistribution.tipByParticipant[participant.id] ?: Money(0, currency)
            val feesShare = feeDistribution.getTotalForParticipant(participant.id)

            val totalOwed = Money(
                itemsSubtotal.minorUnits + taxShare.minorUnits +
                        tipShare.minorUnits + feesShare.minorUnits,
                currency
            )

            // Amount paid (only the payer paid the full amount)
            val totalBill = calculateTotalBill(split, fees)
            val amountPaid = if (participant.id == paidByParticipantId) {
                totalBill
            } else {
                Money(0, currency)
            }

            // Net balance: positive means they're owed, negative means they owe
            val netBalance = Money(amountPaid.minorUnits - totalOwed.minorUnits, currency)

            ParticipantBalance(
                participantId = participant.id,
                participantName = participant.name,
                itemsSubtotal = itemsSubtotal,
                taxShare = taxShare,
                tipShare = tipShare,
                feesShare = feesShare,
                totalOwed = totalOwed,
                amountPaid = amountPaid,
                netBalance = netBalance
            )
        }

        // 4. Calculate settlement transactions
        val transactions = calculateTransactions(balances, participants)

        return Settlement(
            splitId = split.id,
            participantBalances = balances,
            transactions = transactions,
            totalAmount = calculateTotalBill(split, fees),
            currency = currency
        )
    }

    /**
     * Calculate optimal settlement transactions.
     * Minimizes number of transactions needed.
     */
    private fun calculateTransactions(
        balances: List<ParticipantBalance>,
        participants: List<Participant>
    ): List<SettlementTransaction> {
        // Separate into creditors (positive balance) and debtors (negative balance)
        val creditors = balances
            .filter { it.netBalance.minorUnits > 0 }
            .sortedByDescending { it.netBalance.minorUnits }
            .toMutableList()

        val debtors = balances
            .filter { it.netBalance.minorUnits < 0 }
            .sortedBy { it.netBalance.minorUnits } // Most negative first
            .toMutableList()

        val transactions = mutableListOf<SettlementTransaction>()

        // Match debtors with creditors
        var creditorIndex = 0
        var debtorIndex = 0

        while (creditorIndex < creditors.size && debtorIndex < debtors.size) {
            val creditor = creditors[creditorIndex]
            val debtor = debtors[debtorIndex]

            val creditRemaining = creditor.netBalance.minorUnits
            val debtRemaining = -debtor.netBalance.minorUnits // Make positive

            val transactionAmount = minOf(creditRemaining, debtRemaining)

            if (transactionAmount > 0) {
                transactions.add(
                    SettlementTransaction(
                        fromParticipantId = debtor.participantId,
                        fromParticipantName = debtor.participantName,
                        toParticipantId = creditor.participantId,
                        toParticipantName = creditor.participantName,
                        amount = Money(transactionAmount, creditor.netBalance.currencyCode)
                    )
                )
            }

            // Update remaining balances
            creditors[creditorIndex] = creditor.copy(
                netBalance = Money(creditRemaining - transactionAmount, creditor.netBalance.currencyCode)
            )
            debtors[debtorIndex] = debtor.copy(
                netBalance = Money(-debtRemaining + transactionAmount, debtor.netBalance.currencyCode)
            )

            // Move to next creditor/debtor if settled
            if (creditors[creditorIndex].netBalance.minorUnits == 0L) {
                creditorIndex++
            }
            if (debtors[debtorIndex].netBalance.minorUnits == 0L) {
                debtorIndex++
            }
        }

        return transactions
    }

    private suspend fun calculateItemSubtotals(
        split: ReceiptSplit
    ): Map<String, Money> {
        val subtotals = mutableMapOf<String, Long>()

        for (item in split.items) {
            val allocations = itemAllocator.getAllocations(item.id)
            for (allocation in allocations) {
                subtotals[allocation.participantId] =
                    (subtotals[allocation.participantId] ?: 0L) + allocation.amount.minorUnits
            }
        }

        return subtotals.mapValues { (_, amount) -> Money(amount, split.currency) }
    }

    private fun calculateTotalBill(
        split: ReceiptSplit,
        fees: ReceiptFees
    ): Money {
        val itemsTotal = split.items.sumOf { it.totalPrice.minorUnits }
        val feesTotal = fees.total.minorUnits
        return Money(itemsTotal + feesTotal, split.currency)
    }
}
```

### Step 3: Settlement Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/SettlementService.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money
import kotlinx.datetime.Clock

class SettlementService(
    private val settlementCalculator: SettlementCalculator,
    private val settlementRepository: SettlementRepository
) {

    /**
     * Create and save settlement for a split.
     */
    suspend fun createSettlement(
        split: ReceiptSplit,
        fees: ReceiptFees,
        paidByParticipantId: String
    ): Settlement {
        val settlement = settlementCalculator.calculateSettlement(
            split = split,
            fees = fees,
            paidByParticipantId = paidByParticipantId
        )

        settlementRepository.save(settlement)
        return settlement
    }

    /**
     * Mark a settlement transaction as completed.
     */
    suspend fun markTransactionComplete(
        settlementId: String,
        transactionIndex: Int
    ): Settlement {
        val settlement = settlementRepository.getById(settlementId)
            ?: throw IllegalArgumentException("Settlement not found")

        val updatedTransactions = settlement.transactions.mapIndexed { index, tx ->
            if (index == transactionIndex) {
                tx.copy(status = TransactionStatus.COMPLETED)
            } else {
                tx
            }
        }

        val updated = settlement.copy(transactions = updatedTransactions)
        settlementRepository.update(updated)

        return updated
    }

    /**
     * Get settlement summary for display.
     */
    fun getSummary(settlement: Settlement): SettlementSummary {
        val pendingCount = settlement.transactions.count {
            it.status == TransactionStatus.PENDING
        }
        val completedCount = settlement.transactions.count {
            it.status == TransactionStatus.COMPLETED
        }

        val pendingAmount = settlement.transactions
            .filter { it.status == TransactionStatus.PENDING }
            .sumOf { it.amount.minorUnits }

        return SettlementSummary(
            totalParticipants = settlement.participantBalances.size,
            totalAmount = settlement.totalAmount,
            pendingTransactions = pendingCount,
            completedTransactions = completedCount,
            pendingAmount = Money(pendingAmount, settlement.currency),
            isFullySettled = pendingCount == 0
        )
    }

    /**
     * Get what current user owes or is owed.
     */
    fun getMyBalance(
        settlement: Settlement,
        myParticipantId: String
    ): MyBalanceSummary {
        val myBalance = settlement.participantBalances
            .find { it.participantId == myParticipantId }
            ?: return MyBalanceSummary(
                netBalance = Money(0, settlement.currency),
                iOwe = emptyList(),
                owedToMe = emptyList()
            )

        val iOwe = settlement.transactions
            .filter { it.fromParticipantId == myParticipantId }
            .filter { it.status == TransactionStatus.PENDING }

        val owedToMe = settlement.transactions
            .filter { it.toParticipantId == myParticipantId }
            .filter { it.status == TransactionStatus.PENDING }

        return MyBalanceSummary(
            netBalance = myBalance.netBalance,
            iOwe = iOwe,
            owedToMe = owedToMe
        )
    }
}

data class SettlementSummary(
    val totalParticipants: Int,
    val totalAmount: Money,
    val pendingTransactions: Int,
    val completedTransactions: Int,
    val pendingAmount: Money,
    val isFullySettled: Boolean
)

data class MyBalanceSummary(
    val netBalance: Money, // Positive = others owe me, Negative = I owe others
    val iOwe: List<SettlementTransaction>,
    val owedToMe: List<SettlementTransaction>
)
```

### Step 4: Payment Integration Helpers

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/PaymentHelper.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Money

/**
 * Generate payment links for popular services.
 */
class PaymentHelper {

    /**
     * Generate Venmo payment link.
     */
    fun generateVenmoLink(
        recipientUsername: String,
        amount: Money,
        note: String
    ): String {
        val amountFormatted = amount.formatForUrl()
        val noteEncoded = note.encodeUrl()
        return "venmo://paycharge?txn=pay&recipients=$recipientUsername&amount=$amountFormatted&note=$noteEncoded"
    }

    /**
     * Generate PayPal.me link.
     */
    fun generatePayPalLink(
        recipientUsername: String,
        amount: Money
    ): String {
        val amountFormatted = amount.formatForUrl()
        return "https://paypal.me/$recipientUsername/$amountFormatted"
    }

    /**
     * Generate Cash App link.
     */
    fun generateCashAppLink(
        recipientCashtag: String,
        amount: Money,
        note: String
    ): String {
        val amountFormatted = amount.formatForUrl()
        val noteEncoded = note.encodeUrl()
        return "https://cash.app/$recipientCashtag/$amountFormatted?note=$noteEncoded"
    }

    /**
     * Generate Zelle search (no direct link, just instructions).
     */
    fun generateZelleInstructions(
        recipientEmail: String,
        amount: Money
    ): String {
        return "Send ${amount.formatForDisplay()} to $recipientEmail via Zelle"
    }

    /**
     * Generate simple text for sharing.
     */
    fun generatePaymentText(
        transaction: SettlementTransaction,
        paymentMethods: Map<String, String> = emptyMap() // participantId -> payment info
    ): String {
        return buildString {
            appendLine("${transaction.fromParticipantName} owes ${transaction.toParticipantName}")
            appendLine("Amount: ${transaction.amount.formatForDisplay()}")

            val paymentInfo = paymentMethods[transaction.toParticipantId]
            if (paymentInfo != null) {
                appendLine("Pay via: $paymentInfo")
            }
        }
    }

    private fun Money.formatForUrl(): String {
        val dollars = minorUnits / 100
        val cents = minorUnits % 100
        return "$dollars.${cents.toString().padStart(2, '0')}"
    }

    private fun String.encodeUrl(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}
```

---

## Acceptance Criteria

- [ ] Calculate total owed per participant (items + tax + tip + fees)
- [ ] Track who paid the bill
- [ ] Calculate net balance (owed - paid)
- [ ] Generate optimal settlement transactions
- [ ] Minimize number of transactions needed
- [ ] Mark transactions as completed
- [ ] Show pending/completed status
- [ ] Generate payment links (Venmo, PayPal, Cash App)
- [ ] Balances always sum to zero across all participants

---

## Testing

### Unit Tests
```kotlin
class SettlementCalculatorTest {
    @Test
    fun `simple two-person split with one payer`() {
        // Alice paid $100 bill, Bob owes $50
        val settlement = calculator.calculateSettlement(
            split = createSplit(
                items = listOf(item1_50, item2_50),
                allocations = mapOf(
                    item1 to alice,
                    item2 to bob
                )
            ),
            fees = ReceiptFees(tax = null, tip = null, serviceFee = null),
            paidByParticipantId = "alice"
        )

        assertEquals(1, settlement.transactions.size)
        val tx = settlement.transactions[0]
        assertEquals("bob", tx.fromParticipantId)
        assertEquals("alice", tx.toParticipantId)
        assertEquals(5000L, tx.amount.minorUnits) // $50
    }

    @Test
    fun `three-person split minimizes transactions`() {
        // Alice paid $90, Bob owes $30, Carol owes $30
        // Should result in 2 transactions, not 3
        val settlement = calculator.calculateSettlement(...)

        assertEquals(2, settlement.transactions.size)
    }

    @Test
    fun `balances sum to zero`() {
        val settlement = calculator.calculateSettlement(...)

        val totalNetBalance = settlement.participantBalances
            .sumOf { it.netBalance.minorUnits }

        assertEquals(0L, totalNetBalance)
    }

    @Test
    fun `includes tax and tip in totals`() {
        val settlement = calculator.calculateSettlement(
            split = createSplit(...),
            fees = ReceiptFees(
                tax = Money(800, "USD"),    // $8 tax
                tip = Money(1500, "USD"),   // $15 tip
                serviceFee = null
            ),
            paidByParticipantId = "alice"
        )

        val totalOwed = settlement.participantBalances.sumOf { it.totalOwed.minorUnits }
        assertEquals(
            itemsTotal + 800 + 1500,
            totalOwed
        )
    }
}
```

---

## Estimated Complexity

**Medium** - Balance calculation with transaction optimization.
