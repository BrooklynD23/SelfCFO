package com.ledgerlens.receipts

import kotlin.test.*
import kotlinx.coroutines.test.runTest

class ParticipantRepositoryTest {
    private fun repo() = InMemoryParticipantRepository()

    @Test fun getSelf_creates_if_not_exists() = runTest {
        val self = repo().getSelf()
        assertEquals(Participant.SELF_ID, self.id)
        assertTrue(self.isSelf)
        assertTrue(self.isFavorite)
    }

    @Test fun quickAdd_creates_participant() = runTest {
        val p = repo().quickAdd("Alice")
        assertEquals("Alice", p.name)
        assertTrue(p.isQuickAdd)
    }

    @Test fun quickAdd_trims_name() = runTest { assertEquals("Bob", repo().quickAdd("  Bob  ").name) }

    @Test fun quickAdd_fails_blank() = runTest { assertFailsWith<ParticipantException> { repo().quickAdd("   ") } }

    @Test fun create_validates() = runTest {
        assertFailsWith<ParticipantException> { repo().create(Participant(id = "", name = "Test")) }
        assertFailsWith<ParticipantException> { repo().create(Participant(id = "t", name = "")) }
    }

    @Test fun create_prevents_duplicates() = runTest {
        val r = repo()
        val p = r.quickAdd("Alice")
        assertFailsWith<ParticipantException> { r.create(p.copy(name = "Other")) }
    }

    @Test fun getById_returns_or_null() = runTest {
        val r = repo()
        val p = r.quickAdd("Alice")
        assertEquals(p, r.getById(p.id))
        assertNull(r.getById("nonexistent"))
    }

    @Test fun getAll_sorted_by_lastUsedAt() = runTest {
        val r = repo()
        val a = r.quickAdd("Alice")
        r.quickAdd("Bob")
        val c = r.quickAdd("Charlie")
        r.markUsed(c.id)
        r.markUsed(a.id)
        val all = r.getAll()
        assertEquals("Alice", all[0].name)
        assertEquals("Charlie", all[1].name)
    }

    @Test fun getFavorites_returns_favorites() = runTest {
        val r = repo()
        r.getSelf()
        val a = r.quickAdd("Alice")
        r.quickAdd("Bob")
        r.toggleFavorite(a.id)
        assertEquals(2, r.getFavorites().size)
    }

    @Test fun search_finds_by_name() = runTest {
        val r = repo()
        r.quickAdd("Alice Smith")
        r.quickAdd("Bob")
        r.quickAdd("Alice Jones")
        assertEquals(2, r.search("alice").size)
    }

    @Test fun search_empty_for_blank() = runTest { assertTrue(repo().search("").isEmpty()) }

    @Test fun update_modifies_fields() = runTest {
        val r = repo()
        val p = r.quickAdd("Alice")
        val u = r.update(p.copy(name = "Alice Smith", email = "a@x.com"))
        assertEquals("Alice Smith", u.name)
        assertEquals("a@x.com", u.email)
    }

    @Test fun update_preserves_isSelf() = runTest {
        val r = repo()
        val s = r.getSelf()
        assertTrue(r.update(s.copy(isSelf = false, name = "Me2")).isSelf)
    }

    @Test fun delete_removes() = runTest {
        val r = repo()
        val p = r.quickAdd("Alice")
        r.delete(p.id)
        assertNull(r.getById(p.id))
    }

    @Test fun delete_fails_for_self() = runTest {
        val r = repo()
        r.getSelf()
        assertFailsWith<ParticipantException> { r.delete(Participant.SELF_ID) }
    }

    @Test fun toggleFavorite_toggles() = runTest {
        val r = repo()
        val p = r.quickAdd("Alice")
        assertTrue(r.toggleFavorite(p.id).isFavorite)
        assertFalse(r.toggleFavorite(p.id).isFavorite)
    }

    @Test fun updateSelfName_works() = runTest {
        val r = repo()
        r.getSelf()
        assertEquals("John", r.updateSelfName("John").name)
    }

    @Test fun getByIds_returns_in_order() = runTest {
        val r = repo()
        val a = r.quickAdd("Alice")
        val b = r.quickAdd("Bob")
        val c = r.quickAdd("Charlie")
        val res = r.getByIds(listOf(c.id, a.id, b.id))
        assertEquals(listOf("Charlie", "Alice", "Bob"), res.map { it.name })
    }

    @Test fun count_excludes_self() = runTest {
        val r = repo()
        r.getSelf()
        r.quickAdd("A")
        r.quickAdd("B")
        assertEquals(2, r.count())
    }

    @Test fun initials_calculated() {
        assertEquals("AL", Participant(id = "1", name = "Alice").initials)
        assertEquals("AJ", Participant(id = "1", name = "Alice Johnson").initials)
    }
}
