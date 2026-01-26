package com.ledgerlens.receipts

import kotlin.random.Random

data class Participant(
    val id: String,
    val name: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val color: String = DEFAULT_COLORS.first(),
    val isSelf: Boolean = false,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isQuickAdd: Boolean get() = email == null && phoneNumber == null && !isSelf
    val hasContactInfo: Boolean get() = email != null || phoneNumber != null

    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+"))
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "${parts[0].first()}${parts[1].first()}".uppercase()
            }
        }

    companion object {
        const val SELF_ID = "self"
        const val SELF_DEFAULT_NAME = "Me"
        val DEFAULT_COLORS =
            listOf("#4CAF50", "#2196F3", "#9C27B0", "#FF9800", "#E91E63", "#00BCD4", "#FF5722", "#795548", "#607D8B", "#F44336")

        fun createSelf(displayName: String = SELF_DEFAULT_NAME) =
            Participant(id = SELF_ID, name = displayName, color = DEFAULT_COLORS.first(), isSelf = true, isFavorite = true)

        fun quickAdd(name: String, colorIndex: Int = 0) =
            Participant(id = generateId(), name = name.trim(), color = DEFAULT_COLORS[colorIndex % DEFAULT_COLORS.size])

        private fun generateId(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            return (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        }
    }
}

sealed class ParticipantValidationResult {
    data object Valid : ParticipantValidationResult()
    data class Invalid(val reason: String) : ParticipantValidationResult()
}

fun Participant.validate(): ParticipantValidationResult {
    if (id.isBlank()) return ParticipantValidationResult.Invalid("Participant ID cannot be blank")
    if (name.isBlank()) return ParticipantValidationResult.Invalid("Participant name cannot be blank")
    if (name.length > 100) return ParticipantValidationResult.Invalid("Participant name cannot exceed 100 characters")
    if (!color.matches(Regex("^#[0-9A-Fa-f]{6}$"))) return ParticipantValidationResult.Invalid("Invalid color format")
    email?.let {
        if (it.isNotBlank() && !it.contains("@")) return ParticipantValidationResult.Invalid("Invalid email format")
    }
    return ParticipantValidationResult.Valid
}
