package com.rushi.tijori.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rushi.tijori.core.Category
import com.rushi.tijori.core.Tag
import com.rushi.tijori.core.TagPolicy
import com.rushi.tijori.core.Vault
import com.rushi.tijori.core.VaultOps

@Composable
fun OrganizeScreen(
    vault: Vault,
    snackbar: SnackbarHostState,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveTag: (Tag) -> Unit,
    onDeleteTag: (String) -> Unit,
    onBack: () -> Unit,
) {
    // null = closed. A category or tag with a fresh id means "add".
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories and tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "A password sits in one category, such as Work or GitHub.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            vault.categories.forEach { category ->
                val count = vault.entries.count { it.categoryId == category.id }
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = { Text(if (count == 1) "1 password" else "$count passwords") },
                    modifier = Modifier.clickable { editingCategory = category },
                )
            }
            OutlinedButton(
                onClick = { editingCategory = Category(VaultOps.newId(), "") },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Add category")
            }

            Text(
                "Tags and expiry rules",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            )
            Text(
                "A password can carry several tags. Each tag can set how often the password must be " +
                    "rotated and what it must contain. With several tags, the strictest rules win.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            vault.tags.forEach { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    supportingContent = { Text(policySummary(tag.policy)) },
                    modifier = Modifier.clickable { editingTag = tag },
                )
            }
            OutlinedButton(
                onClick = { editingTag = Tag(VaultOps.newId(), "") },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Add tag")
            }
        }
    }

    editingCategory?.let { category ->
        val exists = vault.categories.any { it.id == category.id }
        CategoryDialog(
            category = category,
            exists = exists,
            onSave = {
                onSaveCategory(it)
                editingCategory = null
            },
            onDelete = {
                onDeleteCategory(category.id)
                editingCategory = null
            },
            onDismiss = { editingCategory = null },
        )
    }

    editingTag?.let { tag ->
        val exists = vault.tags.any { it.id == tag.id }
        TagDialog(
            tag = tag,
            exists = exists,
            onSave = {
                onSaveTag(it)
                editingTag = null
            },
            onDelete = {
                onDeleteTag(tag.id)
                editingTag = null
            },
            onDismiss = { editingTag = null },
        )
    }
}

@Composable
private fun CategoryDialog(
    category: Category,
    exists: Boolean,
    onSave: (Category) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Edit category" else "New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (exists) {
                    TextButton(onClick = onDelete) {
                        Text("Delete category (its passwords are kept)", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(category.copy(name = name.trim())) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagDialog(
    tag: Tag,
    exists: Boolean,
    onSave: (Tag) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(tag.name) }
    var rotation by remember { mutableStateOf(if (tag.policy.rotationDays > 0) tag.policy.rotationDays.toString() else "") }
    var minLength by remember { mutableStateOf(if (tag.policy.minLength > 0) tag.policy.minLength.toString() else "") }
    var upper by remember { mutableStateOf(tag.policy.requireUpper) }
    var lower by remember { mutableStateOf(tag.policy.requireLower) }
    var digit by remember { mutableStateOf(tag.policy.requireDigit) }
    var symbol by remember { mutableStateOf(tag.policy.requireSymbol) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Edit tag" else "New tag") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rotation,
                    onValueChange = { rotation = it.filter(Char::isDigit).take(4) },
                    label = { Text("Rotate every … days (empty = never)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = minLength,
                    onValueChange = { minLength = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minimum length (empty = none)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("The password must contain", style = MaterialTheme.typography.titleSmall)
                CheckRow("An uppercase letter", upper) { upper = it }
                CheckRow("A lowercase letter", lower) { lower = it }
                CheckRow("A digit", digit) { digit = it }
                CheckRow("A symbol", symbol) { symbol = it }
                if (exists) {
                    TextButton(onClick = onDelete) {
                        Text("Delete tag (removed from its passwords)", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        tag.copy(
                            name = name.trim(),
                            policy = TagPolicy(
                                rotationDays = rotation.toIntOrNull() ?: 0,
                                minLength = (minLength.toIntOrNull() ?: 0).coerceAtMost(128),
                                requireUpper = upper,
                                requireLower = lower,
                                requireDigit = digit,
                                requireSymbol = symbol,
                            ),
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
