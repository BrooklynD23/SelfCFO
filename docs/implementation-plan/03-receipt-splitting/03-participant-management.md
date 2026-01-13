# 03: Participant Management

## Overview

Implement participant creation and management for receipt splits.

---

## Implementation Steps

### Step 1: Participant Entity

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/Participant.kt
package com.ledgerlens.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Participant(
    val id: String,
    val name: String,
    val color: ParticipantColor,
    val isCurrentUser: Boolean = false,
    val contactId: String? = null, // Link to device contacts
    val createdAt: Instant,
    val lastUsedAt: Instant
)

@Serializable
enum class ParticipantColor(val hex: String) {
    BLUE("#2196F3"),
    GREEN("#4CAF50"),
    ORANGE("#FF9800"),
    PURPLE("#9C27B0"),
    RED("#F44336"),
    TEAL("#009688"),
    PINK("#E91E63"),
    INDIGO("#3F51B5");

    companion object {
        private var nextIndex = 0

        fun next(): ParticipantColor {
            val color = values()[nextIndex % values().size]
            nextIndex++
            return color
        }

        fun reset() {
            nextIndex = 0
        }
    }
}
```

### Step 2: Participant Repository

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/data/ParticipantRepository.kt
package com.ledgerlens.data

interface ParticipantRepository {
    /**
     * Create a new participant.
     */
    suspend fun create(participant: Participant): Participant

    /**
     * Get participant by ID.
     */
    suspend fun getById(id: String): Participant?

    /**
     * Get all participants for a receipt split.
     */
    suspend fun getForSplit(splitId: String): List<Participant>

    /**
     * Get recent participants (for quick add).
     */
    suspend fun getRecent(limit: Int = 10): List<Participant>

    /**
     * Search participants by name.
     */
    suspend fun searchByName(query: String): List<Participant>

    /**
     * Update participant.
     */
    suspend fun update(participant: Participant): Participant

    /**
     * Delete participant.
     */
    suspend fun delete(id: String)

    /**
     * Link participant to receipt split.
     */
    suspend fun linkToSplit(participantId: String, splitId: String)

    /**
     * Unlink participant from receipt split.
     */
    suspend fun unlinkFromSplit(participantId: String, splitId: String)
}
```

### Step 3: SQLDelight Schema

```sql
-- shared/src/commonMain/sqldelight/com/ledgerlens/db/Participant.sq

CREATE TABLE Participant (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    color TEXT NOT NULL,
    is_current_user INTEGER NOT NULL DEFAULT 0,
    contact_id TEXT,
    created_at INTEGER NOT NULL,
    last_used_at INTEGER NOT NULL
);

CREATE INDEX idx_participant_name ON Participant(name);
CREATE INDEX idx_participant_last_used ON Participant(last_used_at DESC);

-- Junction table for split participants
CREATE TABLE SplitParticipant (
    split_id TEXT NOT NULL,
    participant_id TEXT NOT NULL,
    joined_at INTEGER NOT NULL,
    PRIMARY KEY (split_id, participant_id),
    FOREIGN KEY (split_id) REFERENCES ReceiptSplit(id) ON DELETE CASCADE,
    FOREIGN KEY (participant_id) REFERENCES Participant(id) ON DELETE CASCADE
);

-- Queries
getById:
SELECT * FROM Participant WHERE id = ?;

getForSplit:
SELECT p.*
FROM Participant p
INNER JOIN SplitParticipant sp ON p.id = sp.participant_id
WHERE sp.split_id = ?
ORDER BY p.name;

getRecent:
SELECT * FROM Participant
ORDER BY last_used_at DESC
LIMIT ?;

searchByName:
SELECT * FROM Participant
WHERE name LIKE '%' || ? || '%'
ORDER BY last_used_at DESC
LIMIT 20;

insert:
INSERT INTO Participant (id, name, color, is_current_user, contact_id, created_at, last_used_at)
VALUES (?, ?, ?, ?, ?, ?, ?);

update:
UPDATE Participant
SET name = ?, color = ?, is_current_user = ?, contact_id = ?, last_used_at = ?
WHERE id = ?;

delete:
DELETE FROM Participant WHERE id = ?;

linkToSplit:
INSERT OR IGNORE INTO SplitParticipant (split_id, participant_id, joined_at)
VALUES (?, ?, ?);

unlinkFromSplit:
DELETE FROM SplitParticipant
WHERE split_id = ? AND participant_id = ?;
```

### Step 4: Participant Service

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/ParticipantService.kt
package com.ledgerlens.splitting

import com.ledgerlens.domain.Participant
import com.ledgerlens.domain.ParticipantColor
import kotlinx.datetime.Clock

class ParticipantService(
    private val participantRepository: ParticipantRepository
) {

    /**
     * Create a new participant for a split.
     */
    suspend fun createParticipant(
        name: String,
        splitId: String,
        isCurrentUser: Boolean = false
    ): Participant {
        val now = Clock.System.now()

        val participant = Participant(
            id = generateId(),
            name = name.trim(),
            color = ParticipantColor.next(),
            isCurrentUser = isCurrentUser,
            createdAt = now,
            lastUsedAt = now
        )

        participantRepository.create(participant)
        participantRepository.linkToSplit(participant.id, splitId)

        return participant
    }

    /**
     * Add existing participant to a split.
     */
    suspend fun addToSplit(
        participantId: String,
        splitId: String
    ) {
        val participant = participantRepository.getById(participantId)
            ?: throw IllegalArgumentException("Participant not found")

        // Update last used time
        participantRepository.update(
            participant.copy(lastUsedAt = Clock.System.now())
        )

        participantRepository.linkToSplit(participantId, splitId)
    }

    /**
     * Remove participant from a split.
     */
    suspend fun removeFromSplit(
        participantId: String,
        splitId: String
    ) {
        participantRepository.unlinkFromSplit(participantId, splitId)
    }

    /**
     * Get participants for a split.
     */
    suspend fun getParticipantsForSplit(splitId: String): List<Participant> {
        return participantRepository.getForSplit(splitId)
    }

    /**
     * Get quick-add suggestions (recent participants not in split).
     */
    suspend fun getSuggestions(
        splitId: String,
        limit: Int = 5
    ): List<Participant> {
        val existingIds = participantRepository.getForSplit(splitId)
            .map { it.id }
            .toSet()

        return participantRepository.getRecent(limit = 20)
            .filter { it.id !in existingIds }
            .take(limit)
    }

    /**
     * Search participants.
     */
    suspend fun search(query: String): List<Participant> {
        if (query.length < 2) return emptyList()
        return participantRepository.searchByName(query)
    }

    /**
     * Update participant details.
     */
    suspend fun updateParticipant(
        id: String,
        name: String? = null,
        color: ParticipantColor? = null
    ): Participant {
        val existing = participantRepository.getById(id)
            ?: throw IllegalArgumentException("Participant not found")

        val updated = existing.copy(
            name = name?.trim() ?: existing.name,
            color = color ?: existing.color,
            lastUsedAt = Clock.System.now()
        )

        return participantRepository.update(updated)
    }

    /**
     * Create default "Me" participant.
     */
    suspend fun getOrCreateCurrentUser(): Participant {
        val existing = participantRepository.getRecent(100)
            .find { it.isCurrentUser }

        if (existing != null) {
            return existing
        }

        val now = Clock.System.now()
        val me = Participant(
            id = generateId(),
            name = "Me",
            color = ParticipantColor.BLUE,
            isCurrentUser = true,
            createdAt = now,
            lastUsedAt = now
        )

        return participantRepository.create(me)
    }
}
```

### Step 5: Quick Add Component

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/splitting/QuickAddParticipants.kt
package com.ledgerlens.splitting

/**
 * Helper for quick participant setup.
 */
class QuickAddParticipants(
    private val participantService: ParticipantService
) {

    /**
     * Quick setup with just names.
     */
    suspend fun quickSetup(
        splitId: String,
        names: List<String>,
        includeCurrentUser: Boolean = true
    ): List<Participant> {
        ParticipantColor.reset()

        val participants = mutableListOf<Participant>()

        if (includeCurrentUser) {
            val me = participantService.getOrCreateCurrentUser()
            participantService.addToSplit(me.id, splitId)
            participants.add(me)
        }

        for (name in names) {
            if (name.isNotBlank()) {
                val participant = participantService.createParticipant(
                    name = name,
                    splitId = splitId
                )
                participants.add(participant)
            }
        }

        return participants
    }

    /**
     * Create common split scenarios.
     */
    suspend fun createScenario(
        splitId: String,
        scenario: SplitScenario
    ): List<Participant> {
        return when (scenario) {
            SplitScenario.COUPLE -> quickSetup(splitId, listOf("Partner"))
            SplitScenario.ROOMMATES -> quickSetup(splitId, listOf("Roommate 1", "Roommate 2"))
            SplitScenario.FRIENDS -> quickSetup(splitId, listOf("Friend 1", "Friend 2", "Friend 3"))
            SplitScenario.FAMILY -> quickSetup(splitId, listOf("Family 1", "Family 2"))
        }
    }
}

enum class SplitScenario {
    COUPLE,
    ROOMMATES,
    FRIENDS,
    FAMILY
}
```

---

## Acceptance Criteria

- [ ] Create participants with name and auto-assigned color
- [ ] Link/unlink participants to/from splits
- [ ] "Me" participant auto-created
- [ ] Recent participants suggested
- [ ] Search participants by name
- [ ] Update participant name/color
- [ ] Quick setup with multiple names
- [ ] Colors cycle through palette

---

## Testing

### Unit Tests
```kotlin
class ParticipantServiceTest {
    @Test
    fun `createParticipant assigns unique color`() {
        val p1 = service.createParticipant("Alice", splitId)
        val p2 = service.createParticipant("Bob", splitId)
        assertNotEquals(p1.color, p2.color)
    }

    @Test
    fun `getOrCreateCurrentUser returns existing`() {
        val first = service.getOrCreateCurrentUser()
        val second = service.getOrCreateCurrentUser()
        assertEquals(first.id, second.id)
    }

    @Test
    fun `getSuggestions excludes existing participants`() {
        service.createParticipant("Alice", splitId)
        val suggestions = service.getSuggestions(splitId)
        assertFalse(suggestions.any { it.name == "Alice" })
    }
}
```

---

## Estimated Complexity

**Low** - Basic CRUD operations with simple logic.

