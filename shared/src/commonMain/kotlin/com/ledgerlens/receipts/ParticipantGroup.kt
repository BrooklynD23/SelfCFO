package com.ledgerlens.receipts

import kotlin.random.Random

data class ParticipantGroup(
    val id: String,
    val name: String,
    val participantIds: List<String>,
    val icon: String? = null,
    val color: String? = null,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val size: Int get() = participantIds.size
    val isEmpty: Boolean get() = participantIds.isEmpty()
    fun contains(participantId: String) = participantIds.contains(participantId)
    fun withParticipant(participantId: String) =
        if (participantIds.contains(participantId)) this else copy(participantIds = participantIds + participantId)
    fun withoutParticipant(participantId: String) = copy(participantIds = participantIds.filter { it != participantId })
    fun markUsed() = copy(usageCount = usageCount + 1, lastUsedAt = System.currentTimeMillis())

    companion object {
        const val MAX_NAME_LENGTH = 50
        const val MAX_PARTICIPANTS = 20

        fun create(name: String, participantIds: List<String>, icon: String? = null, color: String? = null) =
            ParticipantGroup(id = generateId(), name = name.trim(), participantIds = participantIds.distinct(), icon = icon, color = color)

        private fun generateId(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            return (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        }
    }
}

sealed class GroupValidationResult {
    data object Valid : GroupValidationResult()
    data class Invalid(val reason: String) : GroupValidationResult()
}

fun ParticipantGroup.validate(): GroupValidationResult {
    if (id.isBlank()) return GroupValidationResult.Invalid("Group ID cannot be blank")
    if (name.isBlank()) return GroupValidationResult.Invalid("Group name cannot be blank")
    if (name.length > ParticipantGroup.MAX_NAME_LENGTH) return GroupValidationResult.Invalid("Group name cannot exceed ${ParticipantGroup.MAX_NAME_LENGTH} characters")
    if (participantIds.isEmpty()) return GroupValidationResult.Invalid("Group must have at least one participant")
    if (participantIds.size > ParticipantGroup.MAX_PARTICIPANTS) return GroupValidationResult.Invalid("Group cannot have more than ${ParticipantGroup.MAX_PARTICIPANTS} participants")
    color?.let { if (!it.matches(Regex("^#[0-9A-Fa-f]{6}$"))) return GroupValidationResult.Invalid("Invalid color format") }
    return GroupValidationResult.Valid
}
