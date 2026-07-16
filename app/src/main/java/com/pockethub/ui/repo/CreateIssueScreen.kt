package com.pockethub.ui.repo

import com.pockethub.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIssueScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onIssueCreated: (Int) -> Unit,
    vm: CreateIssueViewModel = hiltViewModel(),
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val isSending by vm.isSending.collectAsState()
    val result by vm.result.collectAsState()
    val actionError by vm.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.loading_failed)

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearActionError()
        }
    }

    LaunchedEffect(result) {
        result?.onSuccess { issue ->
            vm.clearResult()
            onIssueCreated(issue.number)
        }?.onFailure { e ->
            snackbarHostState.showSnackbar(e.localizedMessage ?: genericError)
            vm.clearResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_issue_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.createIssue(owner, repo, title, body) },
                        enabled = title.isNotBlank() && !isSending,
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.action_create_issue))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.hint_issue_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSending,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.hint_issue_body)) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 8,
                enabled = !isSending,
            )
            Button(
                onClick = { vm.createIssue(owner, repo, title, body) },
                enabled = title.isNotBlank() && !isSending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSending) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(0.dp))
                } else {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.height(0.dp))
                }
                Text(stringResource(R.string.action_create_issue))
            }
        }
    }
}
