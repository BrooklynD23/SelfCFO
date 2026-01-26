package com.ledgerlens.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.SpacingPatterns

/**
 * Loading indicator size variants.
 */
enum class LoadingSize {
    SMALL,
    MEDIUM,
    LARGE
}

/**
 * Loading indicator style variants.
 */
enum class LoadingStyle {
    CIRCULAR,
    LINEAR,
    DOTS,
    PULSING
}

/**
 * Consistent loading state indicator.
 *
 * @param modifier Modifier for the indicator.
 * @param style Loading animation style.
 * @param size Size variant.
 * @param color Color for the indicator.
 * @param message Optional loading message.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    style: LoadingStyle = LoadingStyle.CIRCULAR,
    size: LoadingSize = LoadingSize.MEDIUM,
    color: Color = MaterialTheme.colorScheme.primary,
    message: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (style) {
            LoadingStyle.CIRCULAR -> CircularLoading(size = size, color = color)
            LoadingStyle.LINEAR -> LinearLoading(color = color)
            LoadingStyle.DOTS -> DotsLoading(size = size, color = color)
            LoadingStyle.PULSING -> PulsingLoading(size = size, color = color)
        }

        if (message != null) {
            Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.medium))
            Text(
                text = message,
                style = LedgerLensTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Full-screen loading overlay.
 */
@Composable
fun LoadingOverlay(
    modifier: Modifier = Modifier,
    message: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            style = LoadingStyle.CIRCULAR,
            size = LoadingSize.LARGE,
            message = message
        )
    }
}

/**
 * Inline loading indicator for buttons and small spaces.
 */
@Composable
fun LoadingIndicatorInline(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onPrimary) {
    CircularProgressIndicator(
        modifier = modifier.size(16.dp),
        color = color,
        strokeWidth = 2.dp
    )
}

/**
 * Loading placeholder for content (skeleton).
 */
@Composable
fun LoadingPlaceholder(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
private fun CircularLoading(size: LoadingSize, color: Color) {
    val (indicatorSize, strokeWidth) = when (size) {
        LoadingSize.SMALL -> 20.dp to 2.dp
        LoadingSize.MEDIUM -> 36.dp to 3.dp
        LoadingSize.LARGE -> 48.dp to 4.dp
    }

    CircularProgressIndicator(
        modifier = Modifier.size(indicatorSize),
        color = color,
        strokeWidth = strokeWidth
    )
}

@Composable
private fun LinearLoading(color: Color) {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpacingPatterns.screenPaddingHorizontal),
        color = color
    )
}

@Composable
private fun DotsLoading(size: LoadingSize, color: Color) {
    val dotSize = when (size) {
        LoadingSize.SMALL -> 6.dp
        LoadingSize.MEDIUM -> 8.dp
        LoadingSize.LARGE -> 12.dp
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val delays = listOf(0, 150, 300)
    val alphas = delays.map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    delayMillis = delay,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$delay"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(dotSize / 2)
    ) {
        alphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(alpha.value)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun PulsingLoading(size: LoadingSize, color: Color) {
    val circleSize = when (size) {
        LoadingSize.SMALL -> 24.dp
        LoadingSize.MEDIUM -> 40.dp
        LoadingSize.LARGE -> 56.dp
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(circleSize)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}
