package com.rushi.tijori.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rushi.tijori.core.Entry
import com.rushi.tijori.core.Policies
import com.rushi.tijori.core.Vault
import com.rushi.tijori.core.VaultOps

@Composable
fun EntryScreen(
    vault: Vault,
    original: Entry?,
    initialCategoryId: String?,
    snackbar: SnackbarHostState,
    onSave: (Entry) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
) {
    // What the form started from. A new entry starts in the category being viewed.
    val baseline = remember {
        original ?: Entry(id = VaultOps.newId(), title = "", categoryId = initialCategoryId)
    }
    val isNew = original == null

    var title by remember { mutableStateOf(baseline.title) }
    var username by remember { mutableStateOf(baseline.username) }
    var password by remember { mutableStateOf(baseline.password) }
    var url by remember { mutableStateOf(baseline.url) }
    var notes by remember { mutableStateOf(baseline.notes) }
    var categoryId by remember { mutableStateOf(baseline.categoryId) }
    var tagIds by remember { mutableStateOf(baseline.tagIds) }
    var favorite by remember { mutableStateOf(baseline.favorite) }

    var showPassword by remember { mutableStateOf(isNew) }
    var showGenerator by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val draft = baseline.copy(
        title = title.trim(),
        username = username.trim(),
        password = password,
        url = url.trim(),
        notes = notes,
        categoryId = categoryId,
        tagIds = tagIds,
        favorite = favorite,
    )
    val dirty = draft != baseline
    val canSave = draft.title.isNotEmpty()

    val policy = remember(tagIds, vault.tags) { Policies.forTags(tagIds, vault.tags) }
    val violations = if (password.isEmpty()) emptyList() else Policies.violations(password, policy)
    val now = remember { System.currentTimeMillis() }

    val leave: () -> Unit = {
        if (dirty) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New password" else "Edit password") },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(onClick = { onSave(draft) }, enabled = canSave) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                visible = showPassword,
                isError = violations.isNotEmpty(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide" else "Show")
                }
                TextButton(onClick = { onCopy(password) }, enabled = password.isNotEmpty()) { Text("Copy") }
                OutlinedButton(onClick = { showGenerator = true }) { Text("Generate") }
            }
            if (violations.isNotEmpty()) {
                Text(
                    violationText(violations, policy),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (policy.rotationDays > 0) {
                val line = if (isNew || password != baseline.password) {
                    "Tag rule: rotate every ${policy.rotationDays} days. The clock starts when you save."
                } else {
                    val due = Policies.dueAt(baseline, policy)
                    listOfNotNull(
                        rotationText(baseline, policy, now),
                        due?.let { "(${formatDate(it)})" },
                        "Last changed ${formatDate(baseline.passwordChangedAt)}.",
                    ).joinToString(" ")
                }
                Text(line, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(onClick = { categoryMenuOpen = true }) {
                    Text(vault.categories.firstOrNull { it.id == categoryId }?.name ?: "No category")
                }
                DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("No category") },
                        onClick = {
                            categoryId = null
                            categoryMenuOpen = false
                        },
                    )
                    vault.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                categoryId = category.id
                                categoryMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text("Tags", style = MaterialTheme.typography.titleSmall)
            if (vault.tags.isEmpty()) {
                Text(
                    "No tags yet. Create them under Categories and tags to set expiry rules.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vault.tags.forEach { tag ->
                        val selected = tag.id in tagIds
                        FilterChip(
                            selected = selected,
                            onClick = { tagIds = if (selected) tagIds - tag.id else tagIds + tag.id },
                            label = { Text(tag.name) },
                        )
                    }
                }
                if (tagIds.isNotEmpty()) {
                    Text(policySummary(policy), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = favorite, onCheckedChange = { favorite = it })
                Text("Favourite (shown first)", modifier = Modifier.padding(start = 12.dp))
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (baseline.history.isNotEmpty()) {
                TextButton(onClick = { showHistory = !showHistory }) {
                    Text(
                        if (showHistory) "Hide previous passwords"
                        else "Show previous passwords (${baseline.history.size})"
                    )
                }
                if (showHistory) {
                    baseline.history.forEach { old ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(old.password, fontFamily = FontFamily.Monospace)
                                Text(
                                    "Replaced on ${formatDate(old.retiredAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { onCopy(old.password) }) { Text("Copy") }
                        }
                    }
                }
            }
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            policy = policy,
            confirmLabel = "Use this",
            onUse = { generated ->
                password = generated
                showPassword = true
                showGenerator = false
            },
            onDismiss = { showGenerator = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this password?",
            text = "\"${baseline.title}\" and its history are removed from the vault. Backups you made earlier still contain it.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete(baseline.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your changes to this password have not been saved.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}
