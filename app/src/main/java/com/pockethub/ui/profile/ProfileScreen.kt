package com.pockethub.ui.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.local.AccountEntity

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    val allAccounts by vm.allAccounts.collectAsState()
    val activeAccount by vm.activeAccount.collectAsState()

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = user?.avatarUrl ?: activeAccount?.avatarUrl, contentDescription = null, modifier = Modifier.size(72.dp).clip(CircleShape))
                    Spacer(Modifier.height(12.dp))
                    Text(user?.name ?: activeAccount?.name ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("@${user?.login ?: activeAccount?.login ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!user?.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(user!!.bio!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 3)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Text("${user?.followers ?: 0} followers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text("${user?.following ?: 0} following", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (user?.publicRepos != null) {
                            Spacer(Modifier.width(16.dp))
                            Text("${user!!.publicRepos} repos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Multi-account
        if (allAccounts.size > 1) {
            item {
                Text("Accounts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(allAccounts) { account ->
                AccountRow(
                    account = account,
                    isActive = account.isActive,
                    onSwitch = { vm.switchAccount(account.id) },
                    onRemove = { vm.removeAccount(account.id) },
                )
            }
        }

        // Settings entry
        item {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onNavigateToSettings) { Text("Settings") }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = account.avatarUrl, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${account.login}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        if (isActive) {
            AssistChip(onClick = {}, label = { Text("Active") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer))
        } else {
            IconButton(onClick = onSwitch) { Icon(Icons.Outlined.SwapHoriz, contentDescription = "Switch") }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Remove") }
    }
}
