package com.ledgerlens.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryTreeTest {

    @Test
    fun `build creates empty tree from empty list`() {
        val tree = CategoryTree.build(emptyList())
        assertTrue(tree.getRoots().isEmpty())
    }

    @Test
    fun `build creates tree with parent-child relationship`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1)
        )
        val tree = CategoryTree.build(categories)
        assertEquals(1, tree.getRoots().size)
        assertTrue(tree.getRoots()[0].hasChildren)
    }

    @Test
    fun `build throws for orphan category`() {
        val categories = listOf(Category(id = "groceries", name = "Groceries", parentId = "food"))
        assertFailsWith<IllegalArgumentException> { CategoryTree.build(categories) }
    }

    @Test
    fun `build throws for circular reference`() {
        val categories = listOf(
            Category(id = "a", name = "A", parentId = "b"),
            Category(id = "b", name = "B", parentId = "a")
        )
        assertFailsWith<IllegalArgumentException> { CategoryTree.build(categories) }
    }

    @Test
    fun `build throws when exceeding max depth`() {
        val categories = listOf(
            Category(id = "l0", name = "L0", parentId = null),
            Category(id = "l1", name = "L1", parentId = "l0"),
            Category(id = "l2", name = "L2", parentId = "l1"),
            Category(id = "l3", name = "L3", parentId = "l2")
        )
        assertFailsWith<IllegalArgumentException> { CategoryTree.build(categories) }
    }

    @Test
    fun `findById returns correct node`() {
        val tree = CategoryTree.build(listOf(
            Category(id = "food", name = "Food", parentId = null),
            Category(id = "groceries", name = "Groceries", parentId = "food")
        ))
        assertNotNull(tree.findById("groceries"))
        assertNull(tree.findById("nonexistent"))
    }

    @Test
    fun `getPath returns correct path`() {
        val tree = CategoryTree.build(listOf(
            Category(id = "exp", name = "Expenses", parentId = null),
            Category(id = "food", name = "Food", parentId = "exp"),
            Category(id = "groc", name = "Groceries", parentId = "food")
        ))
        val path = tree.getPath("groc")
        assertEquals(3, path.size)
        assertEquals("exp", path[0].id)
        assertEquals("groc", path[2].id)
    }

    @Test
    fun `getLeafCategories returns only leaves`() {
        val tree = CategoryTree.build(listOf(
            Category(id = "food", name = "Food", parentId = null),
            Category(id = "groc", name = "Groceries", parentId = "food")
        ))
        val leaves = tree.getLeafCategories()
        assertEquals(1, leaves.size)
        assertEquals("groc", leaves[0].id)
    }

    @Test
    fun `canAddChild respects max depth`() {
        val tree = CategoryTree.build(listOf(
            Category(id = "l0", name = "L0", parentId = null),
            Category(id = "l1", name = "L1", parentId = "l0"),
            Category(id = "l2", name = "L2", parentId = "l1")
        ))
        assertFalse(tree.canAddChild("l2"))
        assertTrue(tree.canAddChild("l1"))
    }
}
