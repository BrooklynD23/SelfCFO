package com.ledgerlens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.SpacingPatterns

/**
 * Error severity levels.
 */
enum class ErrorSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Error display component.
 * Shows an error message with optional retry action.
 *
 * @param title Error title.
 * @param message Error description.
 * @param modifier Modifier for the component.
 * @param icon Optional error icon.
 * @param severity Error severity level.
 * @param onRetry Optional retry action.
 * @param onDismiss Optional dismiss action.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    severity: ErrorSeverity = ErrorSeverity.ERROR,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val iconTint = when (severity) {
        ErrorSeverity.INFO -> MaterialTheme.colorScheme.primary
        ErrorSeverity.WARNING -> LedgerLensTheme.colors.warning
        ErrorSeverity.ERROR -> LedgerLensTheme.colors.error
        ErrorSeverity.CRITICAL -> LedgerLensTheme.colors.error
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpacingPatterns.screenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = iconTint
            )
            Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.medium))
        }

        Text(
            text = title,
            style = LedgerLensTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.small))

        Text(
            text = message,
            style = LedgerLensTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (onRetry != null || onDismiss != null) {
            Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.large))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LedgerLensTheme.spacing.small)
            ) {
                if (onDismiss != null) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(text = "Dismiss")
                    }
                }
                
                if (onRetry != null) {
                    Button(onClick = onRetry) {
                        Text(text = "Try Again")
                    }
                }
            }
        }
    }
}

/**
 * Network error state.
 */
@Composable
fun NetworkErrorState(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onRetry: (() -> Unit)? = null
) {
    ErrorState(
        title = "Connection Error",
        message = "Unable to connect. Please check your internet connection and try again.",
        modifier = modifier,
        icon = icon,
        severity = ErrorSeverity.ERROR,
        onRetry = onRetry
    )
}

/**
 * Generic error state with exception.
 */
@Composable
fun GenericErrorState(
    error: Throwable,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onRetry: (() -> Unit)? = null,
    showDetails: Boolean = false
) {
    val message = if (showDetails && error.message != null) {
        error.message!!
    } else {
        "An unexpected error occurred. Please try again."
    }

    ErrorState(
        title = "Something Went Wrong",
        message = message,
        modifier = modifier,
        icon = icon,
        severity = ErrorSeverity.ERROR,
        onRetry = onRetry
    )
}

/**
 * Import error state.
 */
@Composable
fun ImportErrorState(
    fileName: String,
    errorMessage: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onRetry: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    ErrorState(
        title = "Import Failed",
        message = "Unable to import \"$fileName\": $errorMessage",
        modifier = modifier,
        icon = icon,
        severity = ErrorSeverity.ERROR,
        onRetry = onRetry,
        onDismiss = onCancel
    )
}

/**
 * Inline error message for forms and inputs.
 */
@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier.padding(vertical = LedgerLensTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LedgerLensTheme.colors.error
            )
            Spacer(modifier = Modifier.width(LedgerLensTheme.spacing.extraSmall))
        }

        Text(
            text = message,
            style = LedgerLensTheme.typography.bodySmall,
            color = LedgerLensTheme.colors.error
        )
    }
}

/**
 * Error banner for top-of-screen messages.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    severity: ErrorSeverity = ErrorSeverity.ERROR,
    onDismiss: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    val backgroundColor = when (severity) {
        ErrorSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
        ErrorSeverity.WARNING -> LedgerLensTheme.colors.warning.copy(alpha = 0.15f)
        ErrorSeverity.ERROR -> LedgerLensTheme.colors.error.copy(alpha = 0.15f)
        ErrorSeverity.CRITICAL -> LedgerLensTheme.colors.error.copy(alpha = 0.25f)
    }

    val contentColor = when (severity) {
        ErrorSeverity.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        ErrorSeverity.WARNING -> LedgerLensTheme.colors.warning
        ErrorSeverity.ERROR -> LedgerLensTheme.colors.error
        ErrorSeverity.CRITICAL -> LedgerLensTheme.colors.error
    }

    LedgerLensCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = backgroundColor,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = LedgerLensTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            Row {
                if (onAction != null && actionLabel != null) {
                    TextButton(onClick = onAction) {
                        Text(
                            text = actionLabel,
                            color = contentColor
                        )
                    }
                }

                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Dismiss",
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
