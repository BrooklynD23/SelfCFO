package com.ledgerlens.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ledgerlens.domain.CurrencyMetadata
import com.ledgerlens.domain.Money
import com.ledgerlens.ui.theme.LedgerLensTheme

/**
 * Size variants for money display.
 */
enum class MoneyTextSize {
    LARGE,
    MEDIUM,
    SMALL
}

/**
 * Style variants for money display.
 */
enum class MoneyTextStyle {
    DEFAULT,
    COLORED,
    MUTED
}

/**
 * Formatted money display component.
 * Automatically colors positive/negative amounts and formats with currency symbol.
 *
 * @param money The Money value to display.
 * @param modifier Modifier for the text.
 * @param size Size variant (LARGE, MEDIUM, SMALL).
 * @param style Style variant (DEFAULT, COLORED, MUTED).
 * @param showSign Whether to show +/- sign.
 * @param showCurrencySymbol Whether to show currency symbol.
 * @param textAlign Text alignment.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    size: MoneyTextSize = MoneyTextSize.MEDIUM,
    style: MoneyTextStyle = MoneyTextStyle.COLORED,
    showSign: Boolean = false,
    showCurrencySymbol: Boolean = true,
    textAlign: TextAlign = TextAlign.End
) {
    val textStyle = when (size) {
        MoneyTextSize.LARGE -> LedgerLensTheme.typography.moneyLarge
        MoneyTextSize.MEDIUM -> LedgerLensTheme.typography.moneyMedium
        MoneyTextSize.SMALL -> LedgerLensTheme.typography.moneySmall
    }

    val color = when (style) {
        MoneyTextStyle.COLORED -> getMoneyColor(money)
        MoneyTextStyle.DEFAULT -> LocalContentColor.current
        MoneyTextStyle.MUTED -> LedgerLensTheme.colors.moneyNeutral
    }

    val formattedText = formatMoney(money, showSign, showCurrencySymbol)

    Text(
        text = formattedText,
        modifier = modifier,
        style = textStyle,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Simplified money text for lists and compact displays.
 */
@Composable
fun MoneyTextCompact(money: Money, modifier: Modifier = Modifier, colored: Boolean = true) {
    MoneyText(
        money = money,
        modifier = modifier,
        size = MoneyTextSize.SMALL,
        style = if (colored) MoneyTextStyle.COLORED else MoneyTextStyle.DEFAULT,
        showSign = false,
        showCurrencySymbol = true
    )
}

/**
 * Large money display for headers and summaries.
 */
@Composable
fun MoneyTextLarge(money: Money, modifier: Modifier = Modifier, colored: Boolean = true, showSign: Boolean = true) {
    MoneyText(
        money = money,
        modifier = modifier,
        size = MoneyTextSize.LARGE,
        style = if (colored) MoneyTextStyle.COLORED else MoneyTextStyle.DEFAULT,
        showSign = showSign,
        showCurrencySymbol = true
    )
}

/**
 * Money text showing change/delta (always shows sign).
 */
@Composable
fun MoneyDeltaText(money: Money, modifier: Modifier = Modifier, size: MoneyTextSize = MoneyTextSize.MEDIUM) {
    MoneyText(
        money = money,
        modifier = modifier,
        size = size,
        style = MoneyTextStyle.COLORED,
        showSign = true,
        showCurrencySymbol = true
    )
}

@Composable
private fun getMoneyColor(money: Money): Color {
    return when {
        money.isPositive -> LedgerLensTheme.colors.moneyPositive
        money.isNegative -> LedgerLensTheme.colors.moneyNegative
        else -> LedgerLensTheme.colors.moneyNeutral
    }
}

private fun formatMoney(money: Money, showSign: Boolean, showCurrencySymbol: Boolean): String {
    val symbol = if (showCurrencySymbol) {
        CurrencyMetadata.getSymbol(money.currencyCode)
    } else {
        ""
    }

    val sign = when {
        !showSign -> ""
        money.isPositive -> "+"
        money.isNegative -> ""
        else -> ""
    }

    val majorString = money.abs().toMajorString()

    return if (money.isNegative) {
        "-$symbol$majorString"
    } else {
        "$sign$symbol$majorString"
    }
}
