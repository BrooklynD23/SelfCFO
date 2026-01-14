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
        assertTrue(tree.flatten().isEmpty())
    }

    @Test
    fun `build creates tree with single root category`() {
        val category = Category(id = "food", name = "Food", parentId = null, sortOrder = 0)
        val tree = CategoryTree.build(listOf(category))

        assertEquals(1, tree.getRoots().size)
        assertEquals("food", tree.getRoots()[0].category.id)
        assertEquals(0, tree.getRoots()[0].depth)
        assertFalse(tree.getRoots()[0].hasChildren)
    }

    @Test
    fun `build creates tree with parent-child relationship`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1),
            Category(id = "restaurants", name = "Restaurants", parentId = "food", sortOrder = 2)
        )
        val tree = CategoryTree.build(categories)

        assertEquals(1, tree.getRoots().size)
        val foodNode = tree.getRoots()[0]
        assertEquals("food", foodNode.category.id)
        assertEquals(2, foodNode.children.size)
        assertTrue(foodNode.hasChildren)

        val groceriesNode = foodNode.children[0]
        assertEquals("groceries", groceriesNode.category.id)
        assertEquals(1, groceriesNode.depth)
        assertFalse(groceriesNode.hasChildren)
    }

    @Test
    fun `build creates multi-level hierarchy`() {
        val categories = listOf(
            Category(id = "expenses", name = "Expenses", parentId = null, sortOrder = 0),
            Category(id = "food", name = "Food", parentId = "expenses", sortOrder = 1),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 2)
        )
        val tree = CategoryTree.build(categories)

        val expensesNode = tree.getRoots()[0]
        assertEquals(0, expensesNode.depth)

        val foodNode = expensesNode.children[0]
        assertEquals(1, foodNode.depth)

        val groceriesNode = foodNode.children[0]
        assertEquals(2, groceriesNode.depth)
    }

    @Test
    fun `build throws exception for orphan category`() {
        val categories = listOf(
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 0)
        )

        assertFailsWith<IllegalArgumentException> {
            CategoryTree.build(categories)
        }
    }

    @Test
    fun `build throws exception for circular reference`() {
        val categories = listOf(
            Category(id = "a", name = "A", parentId = "b", sortOrder = 0),
            Category(id = "b", name = "B", parentId = "a", sortOrder = 1)
        )

        assertFailsWith<IllegalArgumentException> {
            CategoryTree.build(categories)
        }
    }

    @Test
    fun `build throws exception when exceeding max depth`() {
        val categories = listOf(
            Category(id = "level0", name = "Level 0", parentId = null, sortOrder = 0),
            Category(id = "level1", name = "Level 1", parentId = "level0", sortOrder = 1),
            Category(id = "level2", name = "Level 2", parentId = "level1", sortOrder = 2),
            Category(id = "level3", name = "Level 3", parentId = "level2", sortOrder = 3)
        )

        assertFailsWith<IllegalArgumentException> {
            CategoryTree.build(categories)
        }
    }

    @Test
    fun `findById returns correct node`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1)
        )
        val tree = CategoryTree.build(categories)

        val node = tree.findById("groceries")
        assertNotNull(node)
        assertEquals("Groceries", node.category.name)
        assertEquals(1, node.depth)
    }

    @Test
    fun `findById returns null for non-existent category`() {
        val tree = CategoryTree.build(listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0)
        ))

        assertNull(tree.findById("nonexistent"))
    }

    @Test
    fun `getPath returns correct path from root to leaf`() {
        val categories = listOf(
            Category(id = "expenses", name = "Expenses", parentId = null, sortOrder = 0),
            Category(id = "food", name = "Food", parentId = "expenses", sortOrder = 1),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 2)
        )
        val tree = CategoryTree.build(categories)

        val path = tree.getPath("groceries")
        assertEquals(3, path.size)
        assertEquals("expenses", path[0].id)
        assertEquals("food", path[1].id)
        assertEquals("groceries", path[2].id)
    }

    @Test
    fun `getPath returns empty list for non-existent category`() {
        val tree = CategoryTree.build(listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0)
        ))

        assertTrue(tree.getPath("nonexistent").isEmpty())
    }

    @Test
    fun `flatten returns all nodes in tree order`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1),
            Category(id = "restaurants", name = "Restaurants", parentId = "food", sortOrder = 2),
            Category(id = "transport", name = "Transport", parentId = null, sortOrder = 10)
        )
        val tree = CategoryTree.build(categories)

        val flattened = tree.flatten()
        assertEquals(4, flattened.size)
        assertEquals("food", flattened[0].category.id)
        assertEquals("groceries", flattened[1].category.id)
        assertEquals("restaurants", flattened[2].category.id)
        assertEquals("transport", flattened[3].category.id)
    }

    @Test
    fun `getCategoriesAtDepth returns correct categories`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1),
            Category(id = "transport", name = "Transport", parentId = null, sortOrder = 10)
        )
        val tree = CategoryTree.build(categories)

        val depth0 = tree.getCategoriesAtDepth(0)
        assertEquals(2, depth0.size)
        assertTrue(depth0.any { it.id == "food" })
        assertTrue(depth0.any { it.id == "transport" })

        val depth1 = tree.getCategoriesAtDepth(1)
        assertEquals(1, depth1.size)
        assertEquals("groceries", depth1[0].id)
    }

    @Test
    fun `getLeafCategories returns only leaf nodes`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1),
            Category(id = "restaurants", name = "Restaurants", parentId = "food", sortOrder = 2),
            Category(id = "transport", name = "Transport", parentId = null, sortOrder = 10)
        )
        val tree = CategoryTree.build(categories)

        val leaves = tree.getLeafCategories()
        assertEquals(3, leaves.size)
        assertTrue(leaves.any { it.id == "groceries" })
        assertTrue(leaves.any { it.id == "restaurants" })
        assertTrue(leaves.any { it.id == "transport" })
        assertFalse(leaves.any { it.id == "food" })
    }

    @Test
    fun `canAddChild returns true when under max depth`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1)
        )
        val tree = CategoryTree.build(categories)

        assertTrue(tree.canAddChild("groceries"))
    }

    @Test
    fun `canAddChild returns false when at max depth`() {
        val categories = listOf(
            Category(id = "level0", name = "Level 0", parentId = null, sortOrder = 0),
            Category(id = "level1", name = "Level 1", parentId = "level0", sortOrder = 1),
            Category(id = "level2", name = "Level 2", parentId = "level1", sortOrder = 2)
        )
        val tree = CategoryTree.build(categories)

        assertFalse(tree.canAddChild("level2"))
    }

    @Test
    fun `getDepth returns correct depth`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1)
        )
        val tree = CategoryTree.build(categories)

        assertEquals(0, tree.getDepth("food"))
        assertEquals(1, tree.getDepth("groceries"))
        assertEquals(-1, tree.getDepth("nonexistent"))
    }

    @Test
    fun `descendantCount returns correct count`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "groceries", name = "Groceries", parentId = "food", sortOrder = 1),
            Category(id = "restaurants", name = "Restaurants", parentId = "food", sortOrder = 2)
        )
        val tree = CategoryTree.build(categories)

        val foodNode = tree.findById("food")
        assertNotNull(foodNode)
        assertEquals(2, foodNode.descendantCount)

        val groceriesNode = tree.findById("groceries")
        assertNotNull(groceriesNode)
        assertEquals(0, groceriesNode.descendantCount)
    }

    @Test
    fun `children are sorted by sortOrder then name`() {
        val categories = listOf(
            Category(id = "food", name = "Food", parentId = null, sortOrder = 0),
            Category(id = "z_item", name = "Z Item", parentId = "food", sortOrder = 1),
            Category(id = "a_item", name = "A Item", parentId = "food", sortOrder = 1),
            Category(id = "first", name = "First", parentId = "food", sortOrder = 0)
        )
        val tree = CategoryTree.build(categories)

        val children = tree.findById("food")?.children
        assertNotNull(children)
        assertEquals(3, children.size)
        assertEquals("first", children[0].category.id)
        assertEquals("a_item", children[1].category.id)
        assertEquals("z_item", children[2].category.id)
    }

    @Test
    fun `empty tree returns empty results`() {
        val tree = CategoryTree.empty()

        assertTrue(tree.getRoots().isEmpty())
        assertTrue(tree.flatten().isEmpty())
        assertNull(tree.findById("any"))
        assertTrue(tree.getPath("any").isEmpty())
        assertTrue(tree.getCategoriesAtDepth(0).isEmpty())
        assertTrue(tree.getLeafCategories().isEmpty())
    }
}
