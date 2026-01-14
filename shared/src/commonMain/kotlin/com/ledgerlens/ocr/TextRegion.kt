package com.ledgerlens.ocr

/**
 * Represents a rectangular bounding box for text regions.
 *
 * Coordinates are in pixels relative to the original image.
 * Origin (0, 0) is top-left corner.
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

    fun intersects(other: BoundingBox): Boolean {
        return x < other.right && right > other.x &&
               y < other.bottom && bottom > other.y
    }

    fun contains(px: Int, py: Int): Boolean {
        return px >= x && px < right && py >= y && py < bottom
    }

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
 */
data class TextRegion(
    val text: String,
    val boundingBox: BoundingBox,
    val confidence: Float,
    val lineNumber: Int = 0
) {
    val isHighConfidence: Boolean get() = confidence >= HIGH_CONFIDENCE_THRESHOLD
    val needsReview: Boolean get() = confidence < REVIEW_THRESHOLD

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
        const val REVIEW_THRESHOLD = 0.5f
    }
}

/**
 * A line of text composed of multiple text regions.
 */
data class TextLine(
    val regions: List<TextRegion>,
    val lineNumber: Int
) {
    val text: String get() = regions.joinToString(" ") { it.text }
    val confidence: Float get() = if (regions.isEmpty()) 0f else
        regions.map { it.confidence }.average().toFloat()

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
 */
data class TextBlock(
    val lines: List<TextLine>,
    val blockType: BlockType = BlockType.BODY
) {
    val text: String get() = lines.joinToString("\n") { it.text }
    val confidence: Float get() = if (lines.isEmpty()) 0f else
        lines.map { it.confidence }.average().toFloat()
}

enum class BlockType {
    HEADER, BODY, FOOTER, TABLE, TOTAL, DATE, MERCHANT, ADDRESS, UNKNOWN
}
