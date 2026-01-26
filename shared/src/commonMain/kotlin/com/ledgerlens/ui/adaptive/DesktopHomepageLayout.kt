package com.ledgerlens.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Two-column desktop layout for the homepage.
 * Left column (60%): Main content (greeting, insight, chart)
 * Right column (40%): Side content (pillars, activity)
 */
@Composable
fun DesktopHomepageLayout(
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left column - 60%
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            leftContent()
        }

        // Right column - 40%
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            rightContent()
        }
    }
}

/**
 * Three-column layout for expanded views.
 */
@Composable
fun ThreeColumnLayout(
    leftContent: @Composable () -> Unit,
    centerContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left column - 25%
        Column(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            leftContent()
        }

        // Center column - 50%
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            centerContent()
        }

        // Right column - 25%
        Column(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            rightContent()
        }
    }
}

/**
 * Master-detail layout for list/detail views.
 */
@Composable
fun MasterDetailLayout(
    masterContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    masterWeight: Float = 0.35f
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Master/list column
        Box(
            modifier = Modifier
                .weight(masterWeight)
                .fillMaxHeight()
        ) {
            masterContent()
        }

        // Detail column
        Box(
            modifier = Modifier
                .weight(1f - masterWeight)
                .fillMaxHeight()
        ) {
            detailContent()
        }
    }
}

/**
 * Responsive container that centers content with max width.
 */
@Composable
fun ResponsiveContainer(
    layoutConfig: LayoutConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layoutConfig.horizontalPadding)
        ) {
            content()
        }
    }
}
