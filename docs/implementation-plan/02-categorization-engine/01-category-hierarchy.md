# 01: Category Hierarchy

## Overview

Implement the category entity with parent-child hierarchy and default category seeding.

---

## Implementation Steps

### Step 1: Category Repository

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/data/CategoryRepository.kt
package com.ledgerlens.data

interface CategoryRepository {
    suspend fun getAll(): List<Category>
    suspend fun getById(id: String): Category?
    suspend fun getChildren(parentId: String?): List<Category>
    suspend fun create(category: Category): Category
    suspend fun update(category: Category): Category
    suspend fun delete(id: String)
    suspend fun seedDefaults()
}

data class Category(
    val id: String,
    val name: String,
    val parentId: String?,
    val isSystemDefault: Boolean,
    val isUserCustom: Boolean,
    val icon: String?,
    val color: String?,
    val sortOrder: Int
)
```

### Step 2: Default Categories

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/data/DefaultCategories.kt
package com.ledgerlens.data

object DefaultCategories {
    val categories = listOf(
        // Income
        Category("income", "Income", null, true, false, "arrow_down", "#4CAF50", 0),
        Category("salary", "Salary", "income", true, false, null, null, 1),
        Category("freelance", "Freelance", "income", true, false, null, null, 2),
        Category("investments", "Investments", "income", true, false, null, null, 3),
        Category("refunds", "Refunds", "income", true, false, null, null, 4),

        // Housing
        Category("housing", "Housing", null, true, false, "home", "#795548", 10),
        Category("rent", "Rent/Mortgage", "housing", true, false, null, null, 11),
        Category("utilities", "Utilities", "housing", true, false, null, null, 12),
        Category("home_maintenance", "Home Maintenance", "housing", true, false, null, null, 13),

        // Food & Dining
        Category("food", "Food & Dining", null, true, false, "restaurant", "#FF9800", 20),
        Category("groceries", "Groceries", "food", true, false, null, null, 21),
        Category("restaurants", "Restaurants", "food", true, false, null, null, 22),
        Category("coffee", "Coffee Shops", "food", true, false, null, null, 23),
        Category("delivery", "Food Delivery", "food", true, false, null, null, 24),

        // Transportation
        Category("transport", "Transportation", null, true, false, "car", "#2196F3", 30),
        Category("gas", "Gas & Fuel", "transport", true, false, null, null, 31),
        Category("parking", "Parking", "transport", true, false, null, null, 32),
        Category("rideshare", "Rideshare", "transport", true, false, null, null, 33),
        Category("public_transit", "Public Transit", "transport", true, false, null, null, 34),
        Category("car_maintenance", "Car Maintenance", "transport", true, false, null, null, 35),

        // Shopping
        Category("shopping", "Shopping", null, true, false, "shopping_cart", "#E91E63", 40),
        Category("clothing", "Clothing", "shopping", true, false, null, null, 41),
        Category("electronics", "Electronics", "shopping", true, false, null, null, 42),
        Category("home_goods", "Home Goods", "shopping", true, false, null, null, 43),

        // Entertainment
        Category("entertainment", "Entertainment", null, true, false, "movie", "#9C27B0", 50),
        Category("subscriptions", "Subscriptions", "entertainment", true, false, null, null, 51),
        Category("movies_shows", "Movies & Shows", "entertainment", true, false, null, null, 52),
        Category("games", "Games", "entertainment", true, false, null, null, 53),

        // Health
        Category("health", "Health & Medical", null, true, false, "health", "#F44336", 60),
        Category("doctor", "Doctor", "health", true, false, null, null, 61),
        Category("pharmacy", "Pharmacy", "health", true, false, null, null, 62),
        Category("fitness", "Fitness", "health", true, false, null, null, 63),

        // Personal Care
        Category("personal", "Personal Care", null, true, false, "person", "#00BCD4", 70),

        // Education
        Category("education", "Education", null, true, false, "school", "#3F51B5", 80),

        // Transfers
        Category("transfer", "Transfers", null, true, false, "swap", "#607D8B", 90),
        Category("transfer_out", "Transfer Out", "transfer", true, false, null, null, 91),
        Category("transfer_in", "Transfer In", "transfer", true, false, null, null, 92),

        // Other
        Category("uncategorized", "Uncategorized", null, true, false, "help", "#9E9E9E", 99),
    )
}
```

### Step 3: Category Tree Builder

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/domain/CategoryTree.kt
package com.ledgerlens.domain

data class CategoryNode(
    val category: Category,
    val children: List<CategoryNode>
)

class CategoryTreeBuilder(private val repository: CategoryRepository) {

    suspend fun buildTree(): List<CategoryNode> {
        val allCategories = repository.getAll()
        val byParent = allCategories.groupBy { it.parentId }

        fun buildNode(category: Category): CategoryNode {
            val children = byParent[category.id]
                ?.sortedBy { it.sortOrder }
                ?.map { buildNode(it) }
                ?: emptyList()

            return CategoryNode(category, children)
        }

        return byParent[null]
            ?.sortedBy { it.sortOrder }
            ?.map { buildNode(it) }
            ?: emptyList()
    }

    suspend fun getPath(categoryId: String): List<Category> {
        val path = mutableListOf<Category>()
        var current = repository.getById(categoryId)

        while (current != null) {
            path.add(0, current)
            current = current.parentId?.let { repository.getById(it) }
        }

        return path
    }
}
```

---

## Acceptance Criteria

- [ ] Default categories seeded on first launch
- [ ] Category tree builds correctly
- [ ] Parent-child relationships work
- [ ] Category path retrieval works
- [ ] User can create custom categories
- [ ] User can delete custom (not system) categories

---

## Estimated Complexity

**Low** - Straightforward CRUD with hierarchy.
