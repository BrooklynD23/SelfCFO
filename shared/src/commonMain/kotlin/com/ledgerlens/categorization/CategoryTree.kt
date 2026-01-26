package com.ledgerlens.categorization

data class CategoryNode(
    val category: Category,
    val children: List<CategoryNode>,
    val depth: Int
) {
    val id: String get() = category.id
    val name: String get() = category.name
    val hasChildren: Boolean get() = children.isNotEmpty()
    val isLeaf: Boolean get() = children.isEmpty()
}

class CategoryTree private constructor(
    private val roots: List<CategoryNode>,
    private val nodeIndex: Map<String, CategoryNode>
) {
    fun getRoots(): List<CategoryNode> = roots

    fun findById(id: String): CategoryNode? = nodeIndex[id]

    fun getPath(categoryId: String): List<Category> {
        val node = nodeIndex[categoryId] ?: return emptyList()
        return buildPathToRoot(node).reversed()
    }

    private fun buildPathToRoot(node: CategoryNode): List<Category> {
        val path = mutableListOf(node.category)
        var current = node.category.parentId
        while (current != null) {
            val parent = nodeIndex[current] ?: break
            path.add(parent.category)
            current = parent.category.parentId
        }
        return path
    }

    fun getLeafCategories(): List<Category> = nodeIndex.values.filter { it.isLeaf }.map { it.category }

    fun getAllCategories(): List<Category> = nodeIndex.values.map { it.category }

    fun canAddChild(parentId: String): Boolean {
        val node = nodeIndex[parentId] ?: return false
        return node.depth < Category.MAX_HIERARCHY_DEPTH - 1
    }

    fun getDepth(categoryId: String): Int = nodeIndex[categoryId]?.depth ?: -1

    fun getSubtree(categoryId: String): List<Category> {
        val node = nodeIndex[categoryId] ?: return emptyList()
        return collectSubtree(node)
    }

    private fun collectSubtree(node: CategoryNode): List<Category> {
        val result = mutableListOf(node.category)
        for (child in node.children) {
            result.addAll(collectSubtree(child))
        }
        return result
    }

    companion object {
        fun build(categories: List<Category>): CategoryTree {
            if (categories.isEmpty()) {
                return CategoryTree(emptyList(), emptyMap())
            }

            val byId = categories.associateBy { it.id }
            val childrenMap = categories.groupBy { it.parentId }

            // Validate: check for orphans
            for (cat in categories) {
                if (cat.parentId != null && !byId.containsKey(cat.parentId)) {
                    throw IllegalArgumentException("Orphan category '${cat.id}': parent '${cat.parentId}' not found")
                }
            }

            // Validate: check for circular references
            for (cat in categories) {
                val visited = mutableSetOf<String>()
                var current: String? = cat.id
                while (current != null) {
                    if (!visited.add(current)) {
                        throw IllegalArgumentException("Circular reference detected involving '${cat.id}'")
                    }
                    current = byId[current]?.parentId
                }
            }

            // Build tree nodes with depth tracking
            val nodeIndex = mutableMapOf<String, CategoryNode>()

            fun buildNode(category: Category, depth: Int): CategoryNode {
                if (depth >= Category.MAX_HIERARCHY_DEPTH) {
                    throw IllegalArgumentException("Category '${category.id}' exceeds max depth of ${Category.MAX_HIERARCHY_DEPTH}")
                }
                val childCategories = childrenMap[category.id] ?: emptyList()
                val childNodes = childCategories
                    .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                    .map { buildNode(it, depth + 1) }
                val node = CategoryNode(category, childNodes, depth)
                nodeIndex[category.id] = node
                return node
            }

            val rootCategories = childrenMap[null] ?: emptyList()
            val roots = rootCategories
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                .map { buildNode(it, 0) }

            return CategoryTree(roots, nodeIndex)
        }
    }
}
