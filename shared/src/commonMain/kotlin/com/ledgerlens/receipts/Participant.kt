package com.ledgerlens.receipts

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Domain model for a participant in receipt splitting.
 * Participants can be other people involved in shared expenses.
 *
 * @property id Unique identifier (UUID)
 * @property name Display name of the participant
 * @property email Optional email address for contact/notifications
 * @property phoneNumber Optional phone number for contact
 * @property color Hex color code for UI display (e.g., "#4CAF50")
 * @property isSelf True if this participant represents the current user ("Me")
 * @property isFavorite True if marked as a favorite for quick access
 * @property lastUsedAt Timestamp of last use for LRU sorting
 * @property createdAt Timestamp when participant was created
 */
data class Participant(
    val id: String,
    val name: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val color: String = DEFAULT_COLORS.first(),
    val isSelf: Boolean = false,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val createdAt: Long = currentTimeMillis()
) {
    /**
     * Returns true if this is a "quick add" participant (name only).
     */
    val isQuickAdd: Boolean
        get() = email == null && phoneNumber == null && !isSelf

    /**
     * Returns true if participant has contact info.
     */
    val hasContactInfo: Boolean
        get() = email != null || phoneNumber != null

    /**
     * Returns display initials (first letters of name parts, max 2).
     */
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

        val DEFAULT_COLORS = listOf(
            "#4CAF50", // Green
            "#2196F3", // Blue
            "#9C27B0", // Purple
            "#FF9800", // Orange
            "#E91E63", // Pink
            "#00BCD4", // Cyan
            "#FF5722", // Deep Orange
            "#795548", // Brown
            "#607D8B", // Blue Grey
            "#F44336"  // Red
        )

        /**
         * Creates the "self" participant representing the current user.
         */
        fun createSelf(displayName: String = SELF_DEFAULT_NAME): Participant {
            return Participant(
                id = SELF_ID,
                name = displayName,
                color = DEFAULT_COLORS.first(),
                isSelf = true,
                isFavorite = true
            )
        }

        /**
         * Quick-add a participant with just a name.
         */
        @OptIn(ExperimentalUuidApi::class)
        fun quickAdd(name: String, colorIndex: Int = 0): Participant {
            return Participant(
                id = Uuid.random().toString(),
                name = name.trim(),
                color = DEFAULT_COLORS[colorIndex % DEFAULT_COLORS.size]
            )
        }
    }
}

/**
 * Validation result for participant operations.
 */
sealed class ParticipantValidationResult {
    data object Valid : ParticipantValidationResult()
    data class Invalid(val reason: String) : ParticipantValidationResult()
}

/**
 * Validates a participant for creation or update.
 */
fun Participant.validate(): ParticipantValidationResult {
    if (id.isBlank()) {
        return ParticipantValidationResult.Invalid("Participant ID cannot be blank")
    }
    if (name.isBlank()) {
        return ParticipantValidationResult.Invalid("Participant name cannot be blank")
    }
    if (name.length > 100) {
        return ParticipantValidationResult.Invalid("Participant name cannot exceed 100 characters")
    }
    if (!color.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
        return ParticipantValidationResult.Invalid("Invalid color format. Expected hex color (e.g., #4CAF50)")
    }
    email?.let {
        if (it.isNotBlank() && !it.contains("@")) {
            return ParticipantValidationResult.Invalid("Invalid email format")
        }
    }
    return ParticipantValidationResult.Valid
}

/**
 * Platform-agnostic current time function.
 * Uses expect/actual for proper implementation.
 */
internal expect fun currentTimeMillis(): Long
