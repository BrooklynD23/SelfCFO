package com.ledgerlens.receipts

/**
 * Represents a participant's involvement in a specific expense split.
 */
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
        val newStatus = when {
            newPaidAmount >= allocatedAmount -> SettlementStatus.SETTLED
            newPaidAmount > 0 -> SettlementStatus.PARTIAL
            else -> SettlementStatus.PENDING
        }
        return copy(paidAmount = newPaidAmount, status = newStatus)
    }

    fun waive(): SplitParticipant = copy(status = SettlementStatus.WAIVED)

    companion object {
        fun create(participant: Participant, amount: Long, currencyCode: String = "USD"): SplitParticipant {
            return SplitParticipant(participantId = participant.id, participant = participant, allocatedAmount = amount, currencyCode = currencyCode)
        }

        fun equalSplit(participants: List<Participant>, totalAmount: Long, currencyCode: String = "USD"): List<SplitParticipant> {
            if (participants.isEmpty()) return emptyList()
            val baseAmount = totalAmount / participants.size
            val remainder = totalAmount % participants.size
            return participants.mapIndexed { index, participant ->
                val extra = if (index < remainder) 1 else 0
                SplitParticipant(participantId = participant.id, participant = participant, allocatedAmount = baseAmount + extra, currencyCode = currencyCode)
            }
        }

        fun percentageSplit(participantsWithPercentages: List<Pair<Participant, Double>>, totalAmount: Long, currencyCode: String = "USD"): List<SplitParticipant> {
            if (participantsWithPercentages.isEmpty()) return emptyList()
            var allocated = 0L
            return participantsWithPercentages.mapIndexed { index, (participant, percentage) ->
                val amount = if (index == participantsWithPercentages.lastIndex) totalAmount - allocated
                else (totalAmount * percentage / 100.0).toLong()
                allocated += amount
                SplitParticipant(participantId = participant.id, participant = participant, allocatedAmount = amount, currencyCode = currencyCode)
            }
        }
    }
}

enum class SettlementStatus { PENDING, PARTIAL, SETTLED, WAIVED, DISPUTED }

data class SplitResult(
    val participants: List<SplitParticipant>,
    val totalAmount: Long,
    val currencyCode: String,
    val splitType: SplitType,
    val remainder: Long = 0
) {
    val isBalanced: Boolean get() = participants.sumOf { it.allocatedAmount } == totalAmount
    val allocatedTotal: Long get() = participants.sumOf { it.allocatedAmount }
}

enum class SplitType { EQUAL, CUSTOM, PERCENTAGE, BY_ITEM }
