package com.pockethub.ui.repos

import androidx.compose.foundation.background
import com.pockethub.util.formatCount
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethub.data.model.Repository
import com.pockethub.ui.components.PhCard
import com.pockethub.ui.components.languageColorHex
import com.pockethub.ui.components.parseColorHex
import com.pockethub.ui.components.PhAsyncImage

@Composable
internal fun RepositoryRow(
    repo: Repository,
    onOpen: () -> Unit,
    onOpenOwner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PhCard(
        modifier = modifier
            .fillMaxWidth(),
        onClick = onOpen,
        
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        // Single-line header: avatar + owner — quiet, part of the card's
        // "eyebrow"; the repo name below is the one loud element.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onOpenOwner)
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
            ) {
                PhAsyncImage(
                    model = repo.owner.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = repo.owner.login,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Repo name + tags
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = repo.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (repo.private) RepositoryTag(text = "PRIVATE", isPrivate = true)
            if (repo.fork) RepositoryTag(text = "FORK")
        }

        // Description
        repo.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Meta info
        RepositoryMeta(repo)
        }
    }
}

@Composable
private fun RepositoryTag(text: String, isPrivate: Boolean = false) {
    Spacer(Modifier.width(8.dp))
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isPrivate) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isPrivate) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun RepositoryMeta(repo: Repository) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Language with colored dot
        repo.language?.let { language ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = parseColorHex(languageColorHex(language)) ?: MaterialTheme.colorScheme.outline
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        
        // Stars with icon
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                formatCount(repo.stars),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        
        // Forks with icon
        if (repo.forks > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    formatCount(repo.forks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

