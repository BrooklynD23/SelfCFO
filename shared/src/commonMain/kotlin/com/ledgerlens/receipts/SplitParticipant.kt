package com.ledgerlens.receipts

data class SplitParticipant(
    val participantId: String,
    val participant: Participant? = null,
    val allocatedAmount: Long,
    val currencyCode: String = "USD",
    val status: SettlementStatus = SettlementStatus.PENDING,
    val paidAmount: Long = 0,
    val notes: String? = null
) {
    val outstandingBalance: Long get() = allocatedAmount - paidAmount
    val isSettled: Boolean get() = status == SettlementStatus.SETTLED || paidAmount >= allocatedAmount
    val isPayer: Boolean get() = allocatedAmount < 0
    val displayName: String get() = participant?.name ?: "Participant $participantId"

    fun settle(amount: Long = allocatedAmount): SplitParticipant {
        val newPaidAmount = paidAmount + amount
        val newStatus = when { newPaidAmount >= allocatedAmount -> SettlementStatus.SETTLED; newPaidAmount > 0 -> SettlementStatus.PARTIAL; else -> SettlementStatus.PENDING }
        return copy(paidAmount = newPaidAmount, status = newStatus)
    }

    fun waive() = copy(status = SettlementStatus.WAIVED)

    companion object {
        fun create(participant: Participant, amount: Long, currencyCode: String = "USD") =
            SplitParticipant(participantId = participant.id, participant = participant, allocatedAmount = amount, currencyCode = currencyCode)

        fun equalSplit(participants: List<Participant>, totalAmount: Long, currencyCode: String = "USD"): List<SplitParticipant> {
            if (participants.isEmpty()) return emptyList()
            val baseAmount = totalAmount / participants.size
            val remainder = totalAmount % participants.size
            return participants.mapIndexed { i, p -> SplitParticipant(participantId = p.id, participant = p, allocatedAmount = baseAmount + if (i < remainder) 1 else 0, currencyCode = currencyCode) }
        }
    }
}

enum class SettlementStatus { PENDING, PARTIAL, SETTLED, WAIVED, DISPUTED }
enum class SplitType { EQUAL, CUSTOM, PERCENTAGE, BY_ITEM }

data class SplitResult(val participants: List<SplitParticipant>, val totalAmount: Long, val currencyCode: String, val splitType: SplitType, val remainder: Long = 0) {
    val isBalanced: Boolean get() = participants.sumOf { it.allocatedAmount } == totalAmount
    val allocatedTotal: Long get() = participants.sumOf { it.allocatedAmount }
}
