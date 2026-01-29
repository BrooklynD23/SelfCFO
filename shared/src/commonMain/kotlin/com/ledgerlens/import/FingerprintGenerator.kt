package com.ledgerlens.import

import com.ledgerlens.domain.Money
import com.ledgerlens.security.sha256Hex
import kotlinx.datetime.LocalDate

/**
 * Deterministic transaction fingerprint for idempotent import.
 *
 * This is used for deduplication and to create duplicate-candidate records later.
 */
object FingerprintGenerator {
    /**
     * Fingerprint strategy (stable):
     * sha256(merchantNormalized|postedDate|abs(amountMinor)|currency|accountId?)
     */
    fun fingerprint(
        merchantNormalized: String,
        postedDate: LocalDate,
        amount: Money,
        accountId: String?
    ): String {
        val s = buildString {
            append(merchantNormalized.trim().lowercase())
            append('|')
            append(postedDate.toString())
            append('|')
            append(kotlin.math.abs(amount.minorUnits))
            append('|')
            append(amount.currencyCode.uppercase())
            append('|')
            append(accountId ?: "")
        }
        return sha256Hex(s.encodeToByteArray())
    }
}
