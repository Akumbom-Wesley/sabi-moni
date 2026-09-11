package com.sabimoni.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A row of chips that wraps onto the next line instead of running off the edge.
 *
 * A plain `Row` silently clips: the five group types and the four lead-time choices both
 * overflowed the dialog, and the last option was cut with nothing to suggest it was there.
 *
 * Wrapping rather than horizontal scrolling, deliberately. These are short lists of
 * options in a form, and every option should be visible at once — a scrollable row hides
 * choices behind a gesture the user has to guess is available, which is how the clipped
 * version failed in the first place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlowRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
