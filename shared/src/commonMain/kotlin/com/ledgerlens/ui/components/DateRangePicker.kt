package com.ledgerlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ledgerlens.ui.theme.LedgerLensTheme
import com.ledgerlens.ui.theme.ShapePatterns
import com.ledgerlens.ui.theme.SpacingPatterns
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class DateRange(val start: LocalDate, val end: LocalDate) {
    fun contains(date: LocalDate): Boolean = date >= start && date <= end
    val displayString: String get() = "${start.formatShort()} - ${end.formatShort()}"

    companion object {
        fun today(): DateRange { val today = Clock.System.todayIn(TimeZone.currentSystemDefault()); return DateRange(today, today) }
        fun thisWeek(): DateRange { val today = Clock.System.todayIn(TimeZone.currentSystemDefault()); val start = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY); return DateRange(start, today) }
        fun thisMonth(): DateRange { val today = Clock.System.todayIn(TimeZone.currentSystemDefault()); val start = LocalDate(today.year, today.month, 1); return DateRange(start, today) }
        fun last30Days(): DateRange { val today = Clock.System.todayIn(TimeZone.currentSystemDefault()); return DateRange(today.minus(30, DateTimeUnit.DAY), today) }
        fun last90Days(): DateRange { val today = Clock.System.todayIn(TimeZone.currentSystemDefault()); return DateRange(today.minus(90, DateTimeUnit.DAY), today) }
        fun thisYear(): DateRange { val today = Clock.System.todayIn(TimeZone.currentSystemDefault()); return DateRange(LocalDate(today.year, 1, 1), today) }
    }
}

enum class DateRangePreset(val label: String, val range: () -> DateRange) {
    TODAY("Today", { DateRange.today() }), THIS_WEEK("This Week", { DateRange.thisWeek() }),
    THIS_MONTH("This Month", { DateRange.thisMonth() }), LAST_30_DAYS("Last 30 Days", { DateRange.last30Days() }),
    LAST_90_DAYS("Last 90 Days", { DateRange.last90Days() }), THIS_YEAR("This Year", { DateRange.thisYear() }),
    CUSTOM("Custom", { DateRange.thisMonth() })
}

@Composable
fun DateRangePicker(selectedRange: DateRange?, onRangeSelected: (DateRange) -> Unit, modifier: Modifier = Modifier, label: String = "Date Range", showPresets: Boolean = true) {
    var showDialog by remember { mutableStateOf(false) }
    DateRangeButton(selectedRange = selectedRange, label = label, onClick = { showDialog = true }, modifier = modifier)
    if (showDialog) DateRangeDialog(initialRange = selectedRange, showPresets = showPresets, onDismiss = { showDialog = false }, onConfirm = { range -> onRangeSelected(range); showDialog = false })
}

@Composable
fun DateRangeButton(selectedRange: DateRange?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = ShapePatterns.button) {
        Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(LedgerLensTheme.spacing.small))
        Text(text = selectedRange?.displayString ?: label)
        Spacer(modifier = Modifier.width(LedgerLensTheme.spacing.extraSmall))
        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun DateRangeChip(selectedRange: DateRange?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clip(ShapePatterns.chip).clickable(onClick = onClick),
        color = if (selectedRange != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = ShapePatterns.chip) {
        Row(modifier = Modifier.padding(horizontal = SpacingPatterns.chipPaddingHorizontal, vertical = SpacingPatterns.chipPaddingVertical), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = if (selectedRange != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = selectedRange?.displayString ?: "Date", style = LedgerLensTheme.typography.labelMedium,
                color = if (selectedRange != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DateRangeDialog(initialRange: DateRange?, showPresets: Boolean, onDismiss: () -> Unit, onConfirm: (DateRange) -> Unit) {
    var selectedPreset by remember { mutableStateOf<DateRangePreset?>(null) }
    var customRange by remember { mutableStateOf(initialRange ?: DateRange.thisMonth()) }
    var showCustomPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = ShapePatterns.dialog, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(SpacingPatterns.dialogPadding)) {
                Text(text = "Select Date Range", style = LedgerLensTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.medium))
                if (showPresets && !showCustomPicker) {
                    DateRangePreset.entries.forEach { preset ->
                        PresetOption(preset = preset, isSelected = selectedPreset == preset,
                            onClick = { if (preset == DateRangePreset.CUSTOM) showCustomPicker = true else { selectedPreset = preset; customRange = preset.range() } })
                    }
                } else SimpleDateRangePicker(range = customRange, onRangeChange = { customRange = it })
                Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.medium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (showCustomPicker) { TextButton(onClick = { showCustomPicker = false }) { Text("Back") }; Spacer(modifier = Modifier.width(LedgerLensTheme.spacing.small)) }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(LedgerLensTheme.spacing.small))
                    Button(onClick = { onConfirm(customRange) }) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun PresetOption(preset: DateRangePreset, isSelected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = ShapePatterns.cardSmall) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = LedgerLensTheme.spacing.medium, vertical = LedgerLensTheme.spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = preset.label, style = LedgerLensTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            if (preset != DateRangePreset.CUSTOM) Text(text = preset.range().displayString, style = LedgerLensTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SimpleDateRangePicker(range: DateRange, onRangeChange: (DateRange) -> Unit) {
    var viewingMonth by remember { mutableStateOf(range.start) }
    var selectingStart by remember { mutableStateOf(true) }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewingMonth = viewingMonth.minus(1, DateTimeUnit.MONTH) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month") }
            Text(text = "${viewingMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${viewingMonth.year}", style = LedgerLensTheme.typography.titleMedium)
            IconButton(onClick = { viewingMonth = viewingMonth.plus(1, DateTimeUnit.MONTH) }) { Icon(Icons.Default.ChevronRight, contentDescription = "Next month") }
        }
        Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.small))
        Row(modifier = Modifier.fillMaxWidth()) { listOf("S", "M", "T", "W", "T", "F", "S").forEach { day -> Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = LedgerLensTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.extraSmall))
        val daysInMonth = getDaysInMonth(viewingMonth)
        val firstDayOfWeek = LocalDate(viewingMonth.year, viewingMonth.month, 1).dayOfWeek.ordinal
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(240.dp)) {
            items(firstDayOfWeek) { Box(modifier = Modifier.size(40.dp)) }
            items(daysInMonth) { dayIndex ->
                val date = LocalDate(viewingMonth.year, viewingMonth.month, dayIndex + 1)
                val isInRange = range.contains(date); val isStartOrEnd = date == range.start || date == range.end
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(when { isStartOrEnd -> MaterialTheme.colorScheme.primary; isInRange -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent })
                    .clickable { if (selectingStart) { onRangeChange(DateRange(date, maxOf(date, range.end))); selectingStart = false } else { onRangeChange(DateRange(minOf(range.start, date), date)); selectingStart = true } },
                    contentAlignment = Alignment.Center) {
                    Text(text = (dayIndex + 1).toString(), style = LedgerLensTheme.typography.bodySmall,
                        color = when { isStartOrEnd -> MaterialTheme.colorScheme.onPrimary; isInRange -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurface })
                }
            }
        }
        Spacer(modifier = Modifier.height(LedgerLensTheme.spacing.small))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Start", style = LedgerLensTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = range.start.formatShort(), style = LedgerLensTheme.typography.bodyMedium, color = if (selectingStart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "End", style = LedgerLensTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = range.end.formatShort(), style = LedgerLensTheme.typography.bodyMedium, color = if (!selectingStart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun getDaysInMonth(date: LocalDate): Int = when (date.month.ordinal + 1) { 1, 3, 5, 7, 8, 10, 12 -> 31; 4, 6, 9, 11 -> 30; 2 -> if (isLeapYear(date.year)) 29 else 28; else -> 30 }
private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
private fun LocalDate.formatShort(): String = "${month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} $dayOfMonth, $year"
