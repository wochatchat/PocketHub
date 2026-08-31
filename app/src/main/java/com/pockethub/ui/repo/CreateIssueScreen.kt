package com.pockethub.ui.repo

import com.pockethub.R
import com.pockethub.util.userMessage
import com.pockethub.ui.components.AssigneePicker
import com.pockethub.ui.components.LabelPicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
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
    LaunchedEffect(owner, repo) {
        vm.loadTemplates(owner, repo)
        vm.loadMetadata(owner, repo)
    }
    val templates by vm.templates.collectAsState()
    val isLoadingTemplates by vm.isLoadingTemplates.collectAsState()
    val selectedTemplate by vm.selectedTemplate.collectAsState()
    val forms by vm.forms.collectAsState()
    val selectedForm by vm.selectedForm.collectAsState()
    val formAnswers by vm.formAnswers.collectAsState()
    val contactLinks by vm.contactLinks.collectAsState()
    val blankSelected by vm.blankSelected.collectAsState()
    val validationError by vm.validationError.collectAsState()
    val templatesFailed by vm.templatesFailed.collectAsState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            selectedForm != null -> selectedForm!!.name ?: stringResource(R.string.create_issue_title)
                            selectedTemplate != null -> selectedTemplate!!.name
                            else -> stringResource(R.string.create_issue_title)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedForm != null || selectedTemplate != null || blankSelected -> vm.backToChooser()
                            else -> onBack()
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
            isLoadingTemplates && templates.isEmpty() && forms.isEmpty() && selectedTemplate == null && selectedForm == null -> {
                com.pockethub.ui.components.SkeletonList(Modifier.padding(padding).fillMaxSize(), rows = 5, topPadding = 8.dp)
            }
            // Native form renderer for YAML issue-form templates
            selectedForm != null -> {
                FormIssueEditor(
                    modifier = Modifier.padding(padding),
                    owner = owner,
                    repo = repo,
                    vm = vm,
                    form = selectedForm!!,
                    answers = formAnswers,
                    validationError = validationError,
                    onIssueCreated = onIssueCreated,
                )
            }
            // Template chooser — when templates exist and none selected yet
            (templates.isNotEmpty() || forms.isNotEmpty()) && selectedTemplate == null && !blankSelected -> {
                TemplateChooser(
                    modifier = Modifier.padding(padding),
                    templates = templates,
                    forms = forms,
                    contactLinks = contactLinks,
                    onFormSelected = { vm.selectForm(it) },
                    onTemplateSelected = { vm.selectTemplate(it) },
                    onBlankSelected = { vm.chooseBlank() },
                    onContactLink = { url -> uriHandler.openUri(url) },
                )
            }
            // Load failed — offer retry instead of silently dropping to the plain editor
            templatesFailed && selectedTemplate == null && !blankSelected -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.issue_templates_load_failed), color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.retryTemplates(owner, repo) }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
            // Editor — a legacy template was selected, blank was chosen, or no templates exist
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
    forms: List<IssueForm>,
    contactLinks: List<IssueContactLink>,
    onFormSelected: (IssueForm) -> Unit,
    onTemplateSelected: (IssueTemplate) -> Unit,
    onBlankSelected: () -> Unit,
    onContactLink: (String) -> Unit,
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
        // YAML issue forms first
        items(forms) { f ->
            TemplateCard(
                name = f.name ?: f.description ?: stringResource(R.string.issue_form_fallback_name),
                about = f.description ?: "",
                icon = Icons.Outlined.Article,
                onClick = { onFormSelected(f) },
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
        // External contact links from config.yml
        if (contactLinks.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.issue_contact_links_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(contactLinks, key = { it.url }) { link ->
                TemplateCard(
                    name = link.name,
                    about = link.about,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    onClick = { onContactLink(link.url) },
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** Template title, falling back to the first filled single-line answer (GitHub-like behavior). */
private fun effectiveFormTitle(form: IssueForm, answers: Map<Int, IssueFormAnswer>): String =
    form.title.ifBlank {
        answers.entries.sortedBy { it.key }.firstOrNull { (i, a) ->
            (form.fields.firstOrNull { it.index == i } as? IssueFormField.TextInput)?.multiline == false &&
                a.text.isNotBlank()
        }?.value?.text.orEmpty().take(80)
    }

/**
 * Editor for YAML issue-form templates — native controls driven entirely by the parsed form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormIssueEditor(
    modifier: Modifier,
    owner: String,
    repo: String,
    vm: CreateIssueViewModel,
    form: IssueForm,
    answers: Map<Int, IssueFormAnswer>,
    validationError: String?,
    onIssueCreated: (Int) -> Unit,
) {
    val isSending by vm.isSending.collectAsState()
    val labels by vm.labels.collectAsState()
    val assignees by vm.assignees.collectAsState()
    val repoLabels by vm.repoLabels.collectAsState()
    val assignableUsers by vm.assignableUsers.collectAsState()
    val canSetMetadata by vm.canSetMetadata.collectAsState()
    val result by vm.result.collectAsState()
    val actionError by vm.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.loading_failed)
    val ctx = LocalContext.current

    LaunchedEffect(validationError) {
        validationError?.let {
            val label = if (it.length > 50) it.take(50) + "…" else it
            snackbarHostState.showSnackbar(ctx.getString(R.string.issue_form_required_prompt, label))
            vm.clearValidationError()
        }
    }

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
            snackbarHostState.showSnackbar(e.userMessage(genericError))
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
            var title by remember(form) { mutableStateOf(effectiveFormTitle(form, answers)) }
            // Keep the fallback title in sync as the user fills single-line inputs (never clobber edits)
            LaunchedEffect(answers) {
                if (form.title.isBlank() && title.isBlank()) title = effectiveFormTitle(form, answers)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.hint_issue_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSending,
            )

            IssueFormView(
                form = form,
                answers = answers,
                enabled = !isSending,
                onAnswerChange = vm::updateAnswer,
            )

            // GitHub ignores labels/assignees from users without push
            // access, so the pickers only render for maintainers.
            if (canSetMetadata) {
                LabelPicker(
                    labels = repoLabels,
                    selected = labels,
                    enabled = !isSending,
                    onToggle = vm::toggleLabel,
                )
                AssigneePicker(
                    users = assignableUsers,
                    selected = assignees,
                    enabled = !isSending,
                    onToggle = vm::toggleAssignee,
                )
            }

            Button(
                onClick = { vm.submitForm(owner, repo, title) },
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSending) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
    val labels by vm.labels.collectAsState()
    val assignees by vm.assignees.collectAsState()
    val repoLabels by vm.repoLabels.collectAsState()
    val assignableUsers by vm.assignableUsers.collectAsState()
    val canSetMetadata by vm.canSetMetadata.collectAsState()
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
            snackbarHostState.showSnackbar(e.userMessage(genericError))
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

            // GitHub ignores labels/assignees from users without push
            // access, so the pickers only render for maintainers.
            if (canSetMetadata) {
                LabelPicker(
                    labels = repoLabels,
                    selected = labels,
                    enabled = !isSending,
                    onToggle = vm::toggleLabel,
                )
                AssigneePicker(
                    users = assignableUsers,
                    selected = assignees,
                    enabled = !isSending,
                    onToggle = vm::toggleAssignee,
                )
            }

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

