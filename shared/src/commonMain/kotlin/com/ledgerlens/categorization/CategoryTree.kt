package com.ledgerlens.categorization

/**
 * A node in the category tree representing a category and its children.
 *
 * @property category The category at this node
 * @property children Child nodes sorted by sortOrder
 * @property depth The depth of this node in the tree (0 = root)
 */
data class CategoryNode(
    val category: Category,
    val children: List<CategoryNode> = emptyList(),
    val depth: Int = 0
) {
    /**
     * Returns true if this node has any children.
     */
    val hasChildren: Boolean
        get() = children.isNotEmpty()

    /**
     * Returns the total count of descendants (all children, grandchildren, etc.).
     */
    val descendantCount: Int
        get() = children.size + children.sumOf { it.descendantCount }

    /**
     * Flattens this node and all descendants into a list.
     * Useful for displaying in a flat list with indentation.
     */
    fun flatten(): List<CategoryNode> {
        return listOf(this) + children.flatMap { it.flatten() }
    }

    /**
     * Finds a node by category ID in this subtree.
     */
    fun findById(id: String): CategoryNode? {
        if (category.id == id) return this
        for (child in children) {
            child.findById(id)?.let { return it }
        }
        return null
    }
}

/**
 * Builds and manages a hierarchical tree of categories.
 */
class CategoryTree private constructor(
    private val rootNodes: List<CategoryNode>
) {
    /**
     * Returns all top-level category nodes.
     */
    fun getRoots(): List<CategoryNode> = rootNodes

    /**
     * Returns a flattened list of all category nodes in tree order.
     */
    fun flatten(): List<CategoryNode> = rootNodes.flatMap { it.flatten() }

    /**
     * Finds a category node by ID.
     */
    fun findById(id: String): CategoryNode? {
        for (root in rootNodes) {
            root.findById(id)?.let { return it }
        }
        return null
    }

    /**
     * Returns the path from root to the specified category.
     * Returns empty list if category not found.
     */
    fun getPath(categoryId: String): List<Category> {
        fun findPath(node: CategoryNode, targetId: String): List<Category>? {
            if (node.category.id == targetId) {
                return listOf(node.category)
            }
            for (child in node.children) {
                findPath(child, targetId)?.let { childPath ->
                    return listOf(node.category) + childPath
                }
            }
            return null
        }

        for (root in rootNodes) {
            findPath(root, categoryId)?.let { return it }
        }
        return emptyList()
    }

    /**
     * Returns all categories at a specific depth level.
     */
    fun getCategoriesAtDepth(depth: Int): List<Category> {
        return flatten().filter { it.depth == depth }.map { it.category }
    }

    /**
     * Returns all leaf categories (categories with no children).
     */
    fun getLeafCategories(): List<Category> {
        return flatten().filter { !it.hasChildren }.map { it.category }
    }

    /**
     * Checks if adding a child to the given parent would exceed max depth.
     */
    fun canAddChild(parentId: String): Boolean {
        val parentNode = findById(parentId) ?: return true // New root is allowed
        return parentNode.depth < Category.MAX_HIERARCHY_DEPTH - 1
    }

    /**
     * Returns the depth of a category in the tree.
     * Returns -1 if category not found.
     */
    fun getDepth(categoryId: String): Int {
        return findById(categoryId)?.depth ?: -1
    }

    companion object {
        /**
         * Builds a category tree from a flat list of categories.
         *
         * @param categories All categories to include in the tree
         * @return A CategoryTree with proper hierarchy
         * @throws IllegalArgumentException if hierarchy depth exceeds MAX_HIERARCHY_DEPTH
         */
        fun build(categories: List<Category>): CategoryTree {
            val byId = categories.associateBy { it.id }
            val byParent = categories.groupBy { it.parentId }

            fun buildNode(category: Category, depth: Int): CategoryNode {
                if (depth >= Category.MAX_HIERARCHY_DEPTH) {
                    throw IllegalArgumentException(
                        "Category hierarchy exceeds maximum depth of ${Category.MAX_HIERARCHY_DEPTH}. " +
                        "Category '${category.name}' (${category.id}) is at depth $depth."
                    )
                }

                val children = byParent[category.id]
                    ?.sortedWith(compareBy({ it.sortOrder }, { it.name }))
                    ?.map { buildNode(it, depth + 1) }
                    ?: emptyList()

                return CategoryNode(category, children, depth)
            }

            // Validate no orphan categories (parent doesn't exist)
            for (category in categories) {
                category.parentId?.let { parentId ->
                    if (!byId.containsKey(parentId)) {
                        throw IllegalArgumentException(
                            "Category '${category.name}' (${category.id}) references non-existent parent '$parentId'"
                        )
                    }
                }
            }

            // Validate no circular references
            fun hasCircularReference(categoryId: String, visited: Set<String> = emptySet()): Boolean {
                if (categoryId in visited) return true
                val category = byId[categoryId] ?: return false
                val parentId = category.parentId ?: return false
                return hasCircularReference(parentId, visited + categoryId)
            }

            for (category in categories) {
                if (hasCircularReference(category.id)) {
                    throw IllegalArgumentException(
                        "Circular reference detected involving category '${category.name}' (${category.id})"
                    )
                }
            }

            val rootNodes = byParent[null]
                ?.sortedWith(compareBy({ it.sortOrder }, { it.name }))
                ?.map { buildNode(it, 0) }
                ?: emptyList()

            return CategoryTree(rootNodes)
        }

        /**
         * Creates an empty category tree.
         */
        fun empty(): CategoryTree = CategoryTree(emptyList())
    }
}

/**
 * Result of a category tree operation.
 */
sealed class CategoryTreeResult<out T> {
    data class Success<T>(val value: T) : CategoryTreeResult<T>()
    data class Error(val message: String) : CategoryTreeResult<Nothing>()
}
