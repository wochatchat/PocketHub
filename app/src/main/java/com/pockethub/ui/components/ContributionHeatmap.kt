package com.pockethub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockethub.R
import com.pockethub.data.remote.ContributionCalendar
import com.pockethub.data.remote.ContributionDay

/**
 * GitHub-style contributions heatmap (53 week columns × 7 rows, newest on the
 * right) + a totals row for commits / PRs / issues / reviews. Scrollable
 * horizontally like the web profile. Rendered inside a PhCard by the caller.
 */
@Composable
fun ContributionHeatmap(
    calendar: ContributionCalendar,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    // 5 activity levels, low → high; level 0 = empty cell. Alpha-based ramp so
    // it tracks the theme in both light and dark without a bespoke palette.
    val levelColors = remember(surface) {
        listOf(
            surface.copy(alpha = 0.55f),
            surface,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.primary,
        )
    }
    fun colorFor(count: Int) = when {
        count <= 0 -> levelColors[0]
        count < 3 -> levelColors[1]
        count < 6 -> levelColors[2]
        count < 10 -> levelColors[3]
        else -> levelColors[4]
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Totals row — the "data card" half.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell(stringResource(R.string.contrib_total), calendar.total)
            StatCell(stringResource(R.string.contrib_commits), calendar.commits)
            StatCell(stringResource(R.string.contrib_prs), calendar.pullRequests)
            StatCell(stringResource(R.string.contrib_issues), calendar.issues)
            StatCell(stringResource(R.string.contrib_reviews), calendar.reviews)
        }

        // Heatmap grid — 7 rows tall, one column per week, oldest → newest left
        // to right. Horizontal scroll mirrors github.com when 53 columns overflow.
        val cell = 9.dp
        val gap = 2.dp
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            calendar.weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    // GitHub pads the first/last week to full 7-day columns; some
                    // API payloads trim the trailing empties — pad defensively.
                    for (dayIdx in 0 until 7) {
                        val day = week.getOrNull(dayIdx) ?: ContributionDay("", 0)
                        Box(
                            Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colorFor(day.count)),
                        )
                    }
                }
            }
        }

        // Legend: Less ▢▢▢▢▢ More
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.contrib_less),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            levelColors.forEach { c ->
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .size(cell)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.contrib_more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatCount(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCount(n: Int): String = when {
    n >= 10_000 -> "%.1fk".format(n / 1000f)
    n >= 1_000 -> "%.1fk".format(n / 1000f)
    else -> n.toString()
}
