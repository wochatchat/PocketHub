package com.pockethub.ui.repo

import com.pockethub.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Article
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    LaunchedEffect(owner, repo) { vm.loadTemplates(owner, repo) }
    val templates by vm.templates.collectAsState()
    val isLoadingTemplates by vm.isLoadingTemplates.collectAsState()
    val selectedTemplate by vm.selectedTemplate.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTemplate != null) selectedTemplate!!.name
                        else stringResource(R.string.create_issue_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTemplate != null) {
                            vm.selectTemplate(null)
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            // Loading templates — show progress
            isLoadingTemplates && templates.isEmpty() && selectedTemplate == null -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            // Template chooser — when templates exist and none selected yet
            templates.isNotEmpty() && selectedTemplate == null -> {
                TemplateChooser(
                    modifier = Modifier.padding(padding),
                    templates = templates,
                    onTemplateSelected = { vm.selectTemplate(it) },
                    onBlankSelected = { vm.selectTemplate(null) },
                )
            }
            // Editor — either a template was selected, or no templates exist
            else -> {
                IssueEditor(
                    modifier = Modifier.padding(padding),
                    owner = owner,
                    repo = repo,
                    vm = vm,
                    initialTitle = selectedTemplate?.title ?: "",
                    initialBody = selectedTemplate?.body ?: "",
                    onIssueCreated = onIssueCreated,
                )
            }
        }
    }
}

@Composable
private fun TemplateChooser(
    modifier: Modifier,
    templates: List<IssueTemplate>,
    onTemplateSelected: (IssueTemplate) -> Unit,
    onBlankSelected: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.issue_template_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        // Blank issue option
        item {
            TemplateCard(
                name = stringResource(R.string.issue_template_blank),
                about = stringResource(R.string.issue_template_blank_desc),
                icon = Icons.Outlined.Add,
                onClick = onBlankSelected,
            )
        }
        items(templates, key = { it.fileName }) { t ->
            TemplateCard(
                name = t.name,
                about = t.about,
                icon = Icons.Outlined.Article,
                onClick = { onTemplateSelected(t) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TemplateCard(
    name: String,
    about: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (about.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    about,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueEditor(
    modifier: Modifier,
    owner: String,
    repo: String,
    vm: CreateIssueViewModel,
    initialTitle: String,
    initialBody: String,
    onIssueCreated: (Int) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
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

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                modifier = Modifier.fillMaxWidth(),
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
