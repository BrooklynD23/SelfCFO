package com.ledgerlens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * LedgerLens shape system for consistent corner radius.
 */
@Immutable
data class LedgerLensShapes(
    val none: Shape = RoundedCornerShape(0.dp),
    val extraSmall: Shape = RoundedCornerShape(4.dp),
    val small: Shape = RoundedCornerShape(8.dp),
    val medium: Shape = RoundedCornerShape(12.dp),
    val large: Shape = RoundedCornerShape(16.dp),
    val extraLarge: Shape = RoundedCornerShape(24.dp),
    val full: Shape = RoundedCornerShape(percent = 50)
) {
    companion object {
        val Default = LedgerLensShapes()
    }
}

/**
 * Common shape patterns for consistent UI elements.
 */
object ShapePatterns {
    val card: Shape = RoundedCornerShape(12.dp)
    val cardSmall: Shape = RoundedCornerShape(8.dp)
    
    val button: Shape = RoundedCornerShape(8.dp)
    val buttonLarge: Shape = RoundedCornerShape(12.dp)
    
    val chip: Shape = RoundedCornerShape(8.dp)
    val chipSmall: Shape = RoundedCornerShape(4.dp)
    
    val textField: Shape = RoundedCornerShape(8.dp)
    val searchBar: Shape = RoundedCornerShape(24.dp)
    
    val dialog: Shape = RoundedCornerShape(16.dp)
    val bottomSheet: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    
    val avatar: Shape = RoundedCornerShape(percent = 50)
    val badge: Shape = RoundedCornerShape(4.dp)
    
    val fab: Shape = RoundedCornerShape(16.dp)
    val fabSmall: Shape = RoundedCornerShape(12.dp)
}
