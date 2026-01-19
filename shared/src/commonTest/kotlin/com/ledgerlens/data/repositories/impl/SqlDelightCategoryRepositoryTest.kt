package com.ledgerlens.data.repositories.impl

import app.cash.turbine.test
import com.ledgerlens.data.repositories.CategoryEntity
import com.ledgerlens.db.LedgerLensDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightCategoryRepositoryTest {
    private lateinit var database: LedgerLensDatabase
    private lateinit var repository: SqlDelightCategoryRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        repository = SqlDelightCategoryRepository(database, testDispatcher)
    }

    @Test
    fun `insertCategory stores category correctly`() = runTest(testDispatcher) {
        val category = CategoryTestFixtures.createTestCategoryEntity()

        repository.insertCategory(category)

        repository.getCategory(category.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(category.name, result.name)
            assertEquals(category.parentId, result.parentId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllCategories returns all categories ordered by sort order`() = runTest(testDispatcher) {
        val category1 = CategoryTestFixtures.createTestCategoryEntity(id = "cat-1", name = "Zebra", sortOrder = 2)
        val category2 = CategoryTestFixtures.createTestCategoryEntity(id = "cat-2", name = "Apple", sortOrder = 1)

        repository.insertCategory(category1)
        repository.insertCategory(category2)

        repository.getAllCategories().test {
            val categories = awaitItem()
            assertEquals(2, categories.size)
            // Ordered by sort_order, then name
            assertEquals("cat-2", categories[0].id) // sort_order = 1
            assertEquals("cat-1", categories[1].id) // sort_order = 2
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTopLevelCategories returns only categories without parent`() = runTest(testDispatcher) {
        val parent = CategoryTestFixtures.createTestCategoryEntity(id = "parent", name = "Parent")
        val child = CategoryTestFixtures.createTestCategoryEntity(id = "child", name = "Child", parentId = "parent")

        repository.insertCategory(parent)
        repository.insertCategory(child)

        repository.getTopLevelCategories().test {
            val categories = awaitItem()
            assertEquals(1, categories.size)
            assertEquals("parent", categories.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCategoriesByParent returns children of specified parent`() = runTest(testDispatcher) {
        val parent = CategoryTestFixtures.createTestCategoryEntity(id = "parent", name = "Parent")
        val child1 = CategoryTestFixtures.createTestCategoryEntity(id = "child1", name = "Child 1", parentId = "parent")
        val child2 = CategoryTestFixtures.createTestCategoryEntity(id = "child2", name = "Child 2", parentId = "parent")
        val other = CategoryTestFixtures.createTestCategoryEntity(id = "other", name = "Other")

        repository.insertCategory(parent)
        repository.insertCategory(child1)
        repository.insertCategory(child2)
        repository.insertCategory(other)

        repository.getCategoriesByParent("parent").test {
            val children = awaitItem()
            assertEquals(2, children.size)
            assertTrue(children.any { it.id == "child1" })
            assertTrue(children.any { it.id == "child2" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateCategory modifies category correctly`() = runTest(testDispatcher) {
        val category = CategoryTestFixtures.createTestCategoryEntity()
        repository.insertCategory(category)

        val updated = category.copy(name = "Updated Name", color = "#FF0000")
        repository.updateCategory(updated)

        repository.getCategory(category.id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("Updated Name", result.name)
            assertEquals("#FF0000", result.color)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteCategory removes category`() = runTest(testDispatcher) {
        val category = CategoryTestFixtures.createTestCategoryEntity()
        repository.insertCategory(category)

        repository.deleteCategory(category.id)

        repository.getCategory(category.id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exists returns true for existing category`() = runTest(testDispatcher) {
        val category = CategoryTestFixtures.createTestCategoryEntity()
        repository.insertCategory(category)

        assertTrue(repository.exists(category.id))
    }

    @Test
    fun `exists returns false for non-existent category`() = runTest(testDispatcher) {
        assertFalse(repository.exists("non-existent"))
    }

    @Test
    fun `seedDefaultCategories creates default categories`() = runTest(testDispatcher) {
        repository.seedDefaultCategories()

        repository.getAllCategories().test {
            val categories = awaitItem()
            assertTrue(categories.isNotEmpty())
            assertTrue(categories.any { it.id == "income" })
            assertTrue(categories.any { it.id == "food" })
            assertTrue(categories.any { it.id == "uncategorized" })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
