package com.ledgerlens.ocr

data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    val area: Int get() = width * height
    fun intersects(other: BoundingBox): Boolean = x < other.right && right > other.x && y < other.bottom && bottom > other.y
    fun contains(px: Int, py: Int): Boolean = px >= x && px < right && py >= y && py < bottom
    fun expand(margin: Int): BoundingBox = BoundingBox(x - margin, y - margin, width + margin * 2, height + margin * 2)
    companion object { val EMPTY = BoundingBox(0, 0, 0, 0) }
}

data class TextRegion(val text: String, val boundingBox: BoundingBox, val confidence: Float, val lineNumber: Int = 0) {
    val isHighConfidence: Boolean get() = confidence >= 0.85f
    val needsReview: Boolean get() = confidence < 0.5f
}

data class TextLine(val regions: List<TextRegion>, val lineNumber: Int) {
    val text: String get() = regions.joinToString(" ") { it.text }
    val confidence: Float get() = if (regions.isEmpty()) 0f else regions.map { it.confidence }.average().toFloat()
    val boundingBox: BoundingBox get() {
        if (regions.isEmpty()) return BoundingBox.EMPTY
        return BoundingBox(regions.minOf { it.boundingBox.x }, regions.minOf { it.boundingBox.y },
            regions.maxOf { it.boundingBox.right } - regions.minOf { it.boundingBox.x },
            regions.maxOf { it.boundingBox.bottom } - regions.minOf { it.boundingBox.y })
    }
}

data class TextBlock(val lines: List<TextLine>, val blockType: BlockType = BlockType.BODY) {
    val text: String get() = lines.joinToString("\n") { it.text }
    val confidence: Float get() = if (lines.isEmpty()) 0f else lines.map { it.confidence }.average().toFloat()
}

enum class BlockType { HEADER, BODY, FOOTER, TABLE, TOTAL, DATE, MERCHANT, ADDRESS, UNKNOWN }
