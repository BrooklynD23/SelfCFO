package com.ledgerlens.receipts

/**
 * Represents a rectangular bounding box for text regions.
 *
 * Coordinates are in pixels relative to the original image.
 * Origin (0, 0) is top-left corner.
 *
 * @property x Left edge x-coordinate
 * @property y Top edge y-coordinate
 * @property width Width of the bounding box
 * @property height Height of the bounding box
 */
data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    val area: Int get() = width * height

    /**
     * Check if this box intersects with another.
     */
    fun intersects(other: BoundingBox): Boolean {
        return x < other.right && right > other.x &&
               y < other.bottom && bottom > other.y
    }

    /**
     * Check if this box contains a point.
     */
    fun contains(px: Int, py: Int): Boolean {
        return px >= x && px < right && py >= y && py < bottom
    }

    /**
     * Expand the bounding box by a margin on all sides.
     */
    fun expand(margin: Int): BoundingBox = BoundingBox(
        x = x - margin,
        y = y - margin,
        width = width + margin * 2,
        height = height + margin * 2
    )

    companion object {
        val EMPTY = BoundingBox(0, 0, 0, 0)
    }
}

/**
 * A recognized text region from OCR processing.
 *
 * Represents a contiguous block of text with its location
 * and recognition confidence.
 *
 * @property text The recognized text content
 * @property boundingBox Location of the text in the image
 * @property confidence Recognition confidence from 0.0 to 1.0
 * @property lineNumber Optional line number for ordering
 */
data class TextRegion(
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float,
    val lineNumber: Int = 0
) {
    /**
     * Whether this region has high confidence recognition.
     */
    val isHighConfidence: Boolean get() = confidence >= HIGH_CONFIDENCE_THRESHOLD

    /**
     * Whether this region might need manual review.
     */
    val needsReview: Boolean get() = confidence < REVIEW_THRESHOLD

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
        const val REVIEW_THRESHOLD = 0.5f
    }
}

/**
 * A line of text composed of multiple text regions.
 *
 * @property regions Text regions that make up this line
 * @property lineNumber The line number in the document
 */
data class TextLine(
    val regions: List<TextRegion>,
    val lineNumber: Int
) {
    /**
     * Combined text of all regions in this line.
     */
    val text: String get() = regions.joinToString(" ") { it.text }

    /**
     * Average confidence across all regions.
     */
    val confidence: Float get() = if (regions.isEmpty()) 0f else
        regions.map { it.confidence }.average().toFloat()

    /**
     * Bounding box encompassing all regions.
     */
    val boundingBox: BoundingBox get() {
        if (regions.isEmpty()) return BoundingBox.EMPTY
        val minX = regions.minOf { it.boundingBox.x }
        val minY = regions.minOf { it.boundingBox.y }
        val maxRight = regions.maxOf { it.boundingBox.right }
        val maxBottom = regions.maxOf { it.boundingBox.bottom }
        return BoundingBox(minX, minY, maxRight - minX, maxBottom - minY)
    }
}

/**
 * A block of text (paragraph or section) composed of multiple lines.
 *
 * @property lines Text lines in this block
 * @property blockType Optional semantic type (header, body, footer, etc.)
 */
data class TextBlock(
    val lines: List<TextLine>,
    val blockType: BlockType = BlockType.BODY
) {
    val text: String get() = lines.joinToString("\n") { it.text }
    val confidence: Float get() = if (lines.isEmpty()) 0f else
        lines.map { it.confidence }.average().toFloat()
}

/**
 * Semantic type of a text block.
 */
enum class BlockType {
    HEADER,
    BODY,
    FOOTER,
    TABLE,
    TOTAL,
    DATE,
    MERCHANT,
    ADDRESS,
    UNKNOWN
}
