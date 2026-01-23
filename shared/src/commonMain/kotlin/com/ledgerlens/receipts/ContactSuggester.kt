package com.ledgerlens.receipts

import kotlin.random.Random

interface ContactSuggester {
    suspend fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun searchContacts(query: String, limit: Int = 10): List<ContactSuggestion>
    suspend fun getAllContacts(limit: Int? = null): List<ContactSuggestion>
    fun toParticipant(contact: ContactSuggestion): Participant
}

data class ContactSuggestion(
    val contactId: String,
    val name: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val photoUri: String? = null
) {
    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+"))
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "${parts[0].first()}${parts[1].first()}".uppercase()
            }
        }
}

class StubContactSuggester : ContactSuggester {
    override suspend fun hasPermission(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
    override suspend fun searchContacts(query: String, limit: Int): List<ContactSuggestion> = emptyList()
    override suspend fun getAllContacts(limit: Int?): List<ContactSuggestion> = emptyList()

    override fun toParticipant(contact: ContactSuggestion) = Participant(
        id = generateId(),
        name = contact.name,
        email = contact.email,
        phoneNumber = contact.phoneNumber,
        color = Participant.DEFAULT_COLORS[contact.contactId.hashCode().mod(Participant.DEFAULT_COLORS.size)]
    )

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}

expect object ContactSuggesterFactory {
    fun create(): ContactSuggester
}
