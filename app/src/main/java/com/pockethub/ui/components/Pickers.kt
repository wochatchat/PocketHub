package com.pockethub.ui.components

// Preset pickers for issue metadata: repo labels and assignable users.
// Both render the full option list inline (GitHub-web-like) with a filter
// box on top — mobile-friendly, no popups, and every option is visible.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockethub.R
import com.pockethub.data.model.Issue
import com.pockethub.data.model.User

/**
 * Multi-select picker over the repo's preset [labels]. Unknown label names
 * are impossible to add, so issues can never auto-create labels in the
 * repository. Selected labels stay visible even when filtered out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelPicker(
    labels: List<Issue.Label>,
    selected: List<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerSection(title = stringResource(R.string.labels_section_title), modifier = modifier) { filter ->
        val query = filter.trim()
        val visible = labels.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
        // Selected-but-filtered-out labels stay shown as bare chips below.
        val hiddenSelected = selected.filter { s -> visible.none { it.name.equals(s, ignoreCase = true) } }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visible.forEach { label ->
                val isOn = selected.any { it.equals(label.name, ignoreCase = true) }
                LabelChip(
                    name = label.name,
                    color = label.color,
                    selected = isOn,
                    enabled = enabled,
                    onClick = { onToggle(label.name) },
                )
            }
            hiddenSelected.forEach { name ->
                LabelChip(name = name, color = null, selected = true, enabled = enabled, onClick = { onToggle(name) })
            }
        }
        if (visible.isEmpty() && hiddenSelected.isEmpty()) {
            PickerEmptyText(
                if (query.isEmpty()) stringResource(R.string.no_labels)
                else stringResource(R.string.picker_no_match)
            )
        }
    }
}

/**
 * One repo label as a tinted GitHub-style chip: filled with the label's own
 * color when selected, outlined neutral when not.
 */
@Composable
private fun LabelChip(
    name: String,
    color: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = remember(color) {
        color?.let { runCatching { Color(("FF" + it).toLong(16)) }.getOrNull() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    selected && bg != null -> bg
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .then(
                if (!selected) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(999.dp),
                    )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = bg?.let { rememberContrastColor(it) } ?: MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = when {
                selected && bg != null -> rememberContrastColor(bg)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Multi-select picker over the [users] that can be assigned to issues in
 * this repo. Rows of avatar + login; a query filters them locally.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssigneePicker(
    users: List<User>,
    selected: List<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerSection(title = stringResource(R.string.assignees_section_title), modifier = modifier) { filter ->
        val query = filter.trim()
        val visible = users.filter { query.isEmpty() || it.login.contains(query, ignoreCase = true) }
        // Selected-but-filtered-out logins stay shown as bare text chips.
        val hiddenSelected = selected.filter { s -> visible.none { it.login.equals(s, ignoreCase = true) } }
        if (visible.isEmpty() && hiddenSelected.isEmpty()) {
            PickerEmptyText(
                if (query.isEmpty()) stringResource(R.string.no_assignees)
                else stringResource(R.string.picker_no_match)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            visible.forEach { user ->
                val isOn = selected.any { it.equals(user.login, ignoreCase = true) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isOn) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable(enabled = enabled) { onToggle(user.login) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    PhAsyncImage(
                        model = user.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        user.login,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (isOn) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (hiddenSelected.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    hiddenSelected.forEach { login ->
                        FilterChip(
                            selected = true,
                            onClick = { onToggle(login) },
                            enabled = enabled,
                            label = { Text(login) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared section scaffold: bold title, filter box, then content built by
 * [content] with the current filter text. The filter row collapses when
 * there is nothing to filter.
 */
@Composable
private fun PickerSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable (filter: String) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        var filter by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text(stringResource(R.string.picker_filter_hint), style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        )
        Spacer(Modifier.height(8.dp))
        content(filter)
    }
}

@Composable
private fun PickerEmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
