package com.ledgerlens.receipts

import kotlin.test.*
import kotlinx.coroutines.test.runTest

class ParticipantGroupRepositoryTest {
    private fun repo() = InMemoryParticipantGroupRepository()

    @Test fun createQuick_creates_group() = runTest {
        val g = repo().createQuick("Roommates", listOf("p1", "p2"))
        assertEquals("Roommates", g.name)
        assertEquals(2, g.size)
    }

    @Test fun createQuick_fails_blank() = runTest {
        assertFailsWith<GroupException> { repo().createQuick("  ", listOf("p1")) }
    }

    @Test fun createQuick_fails_empty() = runTest {
        assertFailsWith<GroupException> { repo().createQuick("G", emptyList()) }
    }

    @Test fun getById_returns_or_null() = runTest {
        val r = repo()
        val g = r.createQuick("G", listOf("p1"))
        assertEquals(g, r.getById(g.id))
        assertNull(r.getById("none"))
    }

    @Test fun getAll_sorted_by_usage() = runTest {
        val r = repo()
        val a = r.createQuick("A", listOf("p1"))
        r.createQuick("B", listOf("p1"))
        val c = r.createQuick("C", listOf("p1"))
        r.markUsed(c.id)
        r.markUsed(c.id)
        r.markUsed(a.id)
        assertEquals("C", r.getAll()[0].name)
    }

    @Test fun search_finds_by_name() = runTest {
        val r = repo()
        r.createQuick("Work Lunch", listOf("p1"))
        r.createQuick("Family", listOf("p1"))
        assertEquals(1, r.search("lunch").size)
    }

    @Test fun markUsed_increments() = runTest {
        val r = repo()
        val g = r.createQuick("G", listOf("p1"))
        assertEquals(1, r.markUsed(g.id).usageCount)
        assertEquals(2, r.markUsed(g.id).usageCount)
    }

    @Test fun addParticipant_adds() = runTest {
        val r = repo()
        val g = r.createQuick("G", listOf("p1"))
        assertEquals(2, r.addParticipant(g.id, "p2").size)
    }

    @Test fun removeParticipant_removes() = runTest {
        val r = repo()
        val g = r.createQuick("G", listOf("p1", "p2"))
        assertEquals(1, r.removeParticipant(g.id, "p2").size)
    }

    @Test fun removeParticipant_fails_last() = runTest {
        val r = repo()
        val g = r.createQuick("G", listOf("p1"))
        assertFailsWith<GroupException> { r.removeParticipant(g.id, "p1") }
    }

    @Test fun getGroupsContaining_works() = runTest {
        val r = repo()
        r.createQuick("A", listOf("p1", "p2"))
        r.createQuick("B", listOf("p2", "p3"))
        assertEquals(2, r.getGroupsContaining("p2").size)
    }

    @Test fun count_returns_size() = runTest {
        val r = repo()
        r.createQuick("A", listOf("p1"))
        r.createQuick("B", listOf("p1"))
        assertEquals(2, r.count())
    }
}
