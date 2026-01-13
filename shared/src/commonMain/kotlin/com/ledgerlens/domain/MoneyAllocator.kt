package com.ledgerlens.domain

/**
 * Allocates money across multiple parties with proper remainder handling.
 * Ensures the sum of allocations exactly equals the original amount.
 */
object MoneyAllocator {

    /**
     * Split evenly among N parties.
     * Remainder goes to the first party (typically the payer).
     */
    fun splitEqual(
        total: Money,
        parties: Int,
        remainderRecipient: RemainderRecipient = RemainderRecipient.FIRST
    ): List<Money> {
        require(parties > 0) { "Must have at least one party" }

        val baseAmount = total.minorUnits / parties
        val remainder = (total.minorUnits % parties).toInt()

        return List(parties) { index ->
            val extra = when (remainderRecipient) {
                RemainderRecipient.FIRST -> if (index < remainder) 1L else 0L
                RemainderRecipient.LAST -> if (index >= parties - remainder) 1L else 0L
                RemainderRecipient.LARGEST -> if (index < remainder) 1L else 0L
            }
            total.copy(minorUnits = baseAmount + extra)
        }
    }

    /**
     * Split by fixed-point percentages in basis points (1/10,000).
     * Example: 33.33% = 3333 bps.
     *
     * @param percentagesBps List of basis points (should sum to 10_000)
     */
    fun splitByPercentBps(
        total: Money,
        percentagesBps: List<Int>,
        remainderRecipient: RemainderRecipient = RemainderRecipient.LARGEST
    ): List<Money> {
        require(percentagesBps.isNotEmpty()) { "Must have at least one percentage" }
        require(percentagesBps.all { it >= 0 }) { "Percentages must be non-negative" }
        require(percentagesBps.sum() == 10_000) { "Percentages must sum to 10,000 bps (100.00%)" }

        val baseAllocations = mutableListOf<Long>()
        val remainders = mutableListOf<Long>()

        for (bps in percentagesBps) {
            val numerator = total.minorUnits * bps.toLong()
            baseAllocations += numerator / 10_000L
            remainders += numerator % 10_000L
        }

        var allocated = baseAllocations.sum()
        var remainingCents = total.minorUnits - allocated

        if (remainingCents > 0) {
            val indicesByRemainder = remainders.indices.sortedByDescending { remainders[it] }
            var i = 0
            while (remainingCents > 0 && i < indicesByRemainder.size) {
                val idx = indicesByRemainder[i]
                baseAllocations[idx] = baseAllocations[idx] + 1
                remainingCents--
                i++
            }
        }

        if (remainingCents != 0L) {
            when (remainderRecipient) {
                RemainderRecipient.FIRST -> baseAllocations[0] = baseAllocations[0] + remainingCents
                RemainderRecipient.LAST -> baseAllocations[baseAllocations.lastIndex] =
                    baseAllocations.last() + remainingCents
                RemainderRecipient.LARGEST -> {
                    val maxIndex = percentagesBps.indices.maxByOrNull { percentagesBps[it] } ?: 0
                    baseAllocations[maxIndex] = baseAllocations[maxIndex] + remainingCents
                }
            }
        }

        return baseAllocations.map { total.copy(minorUnits = it) }
    }

    /**
     * Split by explicit ratios.
     * @param ratios Integer ratios (e.g., [1, 2, 1] for 25%, 50%, 25%)
     */
    fun splitByRatio(
        total: Money,
        ratios: List<Int>,
        remainderRecipient: RemainderRecipient = RemainderRecipient.LARGEST
    ): List<Money> {
        require(ratios.isNotEmpty()) { "Must have at least one ratio" }
        require(ratios.all { it > 0 }) { "All ratios must be positive" }

        val totalRatio = ratios.sum().toLong()
        val weights = ratios.map { it.toLong() }
        return splitByWeights(total, weights, totalRatio, remainderRecipient)
    }

    /**
     * Split by integer weights.
     *
     * This is the primitive used for proportional fee allocation and other "share of total" cases.
     */
    fun splitByWeights(
        total: Money,
        weights: List<Long>,
        totalWeight: Long = weights.sum(),
        remainderRecipient: RemainderRecipient = RemainderRecipient.LARGEST
    ): List<Money> {
        require(weights.isNotEmpty()) { "Must have at least one weight" }
        require(weights.all { it >= 0L }) { "Weights must be non-negative" }
        require(totalWeight > 0L) { "Total weight must be positive" }

        val baseAllocations = mutableListOf<Long>()
        val remainders = mutableListOf<Long>()

        for (w in weights) {
            val numerator = total.minorUnits * w
            baseAllocations += numerator / totalWeight
            remainders += numerator % totalWeight
        }

        var remainingCents = total.minorUnits - baseAllocations.sum()
        if (remainingCents > 0) {
            val indicesByRemainder = remainders.indices.sortedByDescending { remainders[it] }
            var i = 0
            while (remainingCents > 0 && i < indicesByRemainder.size) {
                val idx = indicesByRemainder[i]
                baseAllocations[idx] = baseAllocations[idx] + 1
                remainingCents--
                i++
            }
        }

        if (remainingCents != 0L) {
            when (remainderRecipient) {
                RemainderRecipient.FIRST -> baseAllocations[0] = baseAllocations[0] + remainingCents
                RemainderRecipient.LAST -> baseAllocations[baseAllocations.lastIndex] =
                    baseAllocations.last() + remainingCents
                RemainderRecipient.LARGEST -> {
                    val maxIndex = weights.indices.maxByOrNull { weights[it] } ?: 0
                    baseAllocations[maxIndex] = baseAllocations[maxIndex] + remainingCents
                }
            }
        }

        return baseAllocations.map { total.copy(minorUnits = it) }
    }

    enum class RemainderRecipient {
        FIRST,    // First party gets remainder
        LAST,     // Last party gets remainder
        LARGEST   // Party with largest share gets remainder
    }
}
