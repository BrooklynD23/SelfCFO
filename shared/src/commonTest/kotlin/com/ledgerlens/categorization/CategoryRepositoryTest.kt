package com.ledgerlens.categorization

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryRepositoryTest {

    private fun repo() = InMemoryCategoryRepository()

    @Test
    fun `create and getById works`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        assertNotNull(r.getById("food"))
    }

    @Test
    fun `create throws for duplicate`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        assertFailsWith<CategoryException> { r.create(Category(id = "food", name = "X", isUserCustom = true)) }
    }

    @Test
    fun `create throws for missing parent`() = runTest {
        val r = repo()
        assertFailsWith<CategoryException> {
            r.create(Category(id = "groc", name = "Groceries", parentId = "food", isUserCustom = true))
        }
    }

    @Test
    fun `create enforces max depth`() = runTest {
        val r = repo()
        r.create(Category(id = "l0", name = "L0", isUserCustom = true))
        r.create(Category(id = "l1", name = "L1", parentId = "l0", isUserCustom = true))
        r.create(Category(id = "l2", name = "L2", parentId = "l1", isUserCustom = true))
        assertFailsWith<CategoryException> {
            r.create(Category(id = "l3", name = "L3", parentId = "l2", isUserCustom = true))
        }
    }

    @Test
    fun `getChildren returns children`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        r.create(Category(id = "groc", name = "Groceries", parentId = "food", isUserCustom = true))
        assertEquals(1, r.getChildren("food").size)
    }

    @Test
    fun `update modifies category`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        val updated = r.update(Category(id = "food", name = "Food & Dining", icon = "restaurant", isUserCustom = true))
        assertEquals("Food & Dining", updated.name)
    }

    @Test
    fun `delete removes category`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        r.delete("food")
        assertNull(r.getById("food"))
    }

    @Test
    fun `delete throws for system default`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isSystemDefault = true))
        assertFailsWith<CategoryException> { r.delete("food") }
    }

    @Test
    fun `delete cascade removes children`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        r.create(Category(id = "groc", name = "Groceries", parentId = "food", isUserCustom = true))
        r.delete("food", cascade = true)
        assertNull(r.getById("groc"))
    }

    @Test
    fun `seedDefaults populates categories`() = runTest {
        val r = repo()
        r.seedDefaults()
        assertTrue(r.hasDefaults())
        assertNotNull(r.getById(Category.UNCATEGORIZED_ID))
    }

    @Test
    fun `getPath returns path`() = runTest {
        val r = repo()
        r.create(Category(id = "exp", name = "Expenses", isUserCustom = true))
        r.create(Category(id = "food", name = "Food", parentId = "exp", isUserCustom = true))
        val path = r.getPath("food")
        assertEquals(2, path.size)
    }

    @Test
    fun `move changes parent`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        r.create(Category(id = "other", name = "Other", isUserCustom = true))
        r.create(Category(id = "groc", name = "Groceries", parentId = "food", isUserCustom = true))
        r.move("groc", "other")
        assertEquals("other", r.getById("groc")?.parentId)
    }

    @Test
    fun `move throws for circular`() = runTest {
        val r = repo()
        r.create(Category(id = "food", name = "Food", isUserCustom = true))
        r.create(Category(id = "groc", name = "Groceries", parentId = "food", isUserCustom = true))
        assertFailsWith<CategoryException> { r.move("food", "groc") }
    }
}
