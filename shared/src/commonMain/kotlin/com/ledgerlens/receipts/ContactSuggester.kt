package com.ledgerlens.receipts

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    @OptIn(ExperimentalUuidApi::class)
    override fun toParticipant(contact: ContactSuggestion) = Participant(
        id = Uuid.random().toString(),
        name = contact.name,
        email = contact.email,
        phoneNumber = contact.phoneNumber,
        color = Participant.DEFAULT_COLORS[contact.contactId.hashCode().mod(Participant.DEFAULT_COLORS.size)]
    )
}

expect object ContactSuggesterFactory {
    fun create(): ContactSuggester
}
