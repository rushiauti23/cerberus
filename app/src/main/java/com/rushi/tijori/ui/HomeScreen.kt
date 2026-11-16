package com.rushi.tijori.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rushi.tijori.core.Entry
import com.rushi.tijori.core.Policies
import com.rushi.tijori.core.RotationStatus
import com.rushi.tijori.core.TagPolicy
import com.rushi.tijori.core.Vault
import com.rushi.tijori.core.VaultOps

@Composable
fun HomeScreen(
    vm: VaultViewModel,
    vault: Vault,
    snackbar: SnackbarHostState,
    onCopy: (String) -> Unit,
) {
    val now = remember(vault) { System.currentTimeMillis() }
    val reused = remember(vault) { VaultOps.reusedPasswordIds(vault) }
    val entries = remember(vault, vm.query, vm.categoryFilter, vm.onlyDue) {
        VaultOps.filter(vault, vm.query, vm.categoryFilter, vm.onlyDue, now)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }

    AskForNotificationPermission(needed = vault.tags.any { it.policy.rotationDays > 0 })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tijori") },
                actions = {
                    IconButton(onClick = { vm.lock() }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock the vault")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Generate a password") },
                                onClick = {
                                    menuOpen = false
                                    showGenerator = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Categories and tags") },
                                onClick = {
                                    menuOpen = false
                                    vm.navigate(Screen.Organize)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings and backup") },
                                onClick = {
                                    menuOpen = false
                                    vm.navigate(Screen.Settings)
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.navigate(Screen.EditEntry(null)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add a password")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = { Text("Search title, username, website or tag") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FilterChip(
                    selected = vm.categoryFilter == null,
                    onClick = { vm.categoryFilter = null },
                    label = { Text("All") },
                )
                vault.categories.forEach { category ->
                    FilterChip(
                        selected = vm.categoryFilter == category.id,
                        onClick = {
                            vm.categoryFilter = if (vm.categoryFilter == category.id) null else category.id
                        },
                        label = { Text(category.name) },
                    )
                }
                FilterChip(
                    selected = vm.onlyDue,
                    onClick = { vm.onlyDue = !vm.onlyDue },
                    label = { Text("Due for rotation") },
                )
            }

            if (entries.isEmpty()) {
                Text(
                    text = if (vault.entries.isEmpty()) {
                        "No passwords yet. Tap + to add the first one."
                    } else {
                        "Nothing matches this search or filter."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            policy = Policies.forEntry(entry, vault.tags),
                            reused = entry.id in reused,
                            now = now,
                            onOpen = { vm.navigate(Screen.EditEntry(entry.id)) },
                            onCopy = { onCopy(entry.password) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showGenerator) {
        GeneratorDialog(
            policy = TagPolicy(),
            confirmLabel = "Copy",
            onUse = { password ->
                showGenerator = false
                onCopy(password)
            },
            onDismiss = { showGenerator = false },
        )
    }
}

@Composable
private fun EntryRow(
    entry: Entry,
    policy: TagPolicy,
    reused: Boolean,
    now: Long,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val status = Policies.status(entry, policy, now)
    val warnings = listOfNotNull(
        rotationText(entry, policy, now).takeIf {
            status == RotationStatus.OVERDUE || status == RotationStatus.DUE_SOON
        },
        "Password reused elsewhere".takeIf { reused },
        "Breaks tag rules".takeIf { entry.password.isNotEmpty() && Policies.violations(entry.password, policy).isNotEmpty() },
    )
    ListItem(
        headlineContent = { Text((if (entry.favorite) "★ " else "") + entry.title) },
        supportingContent = {
            Column {
                if (entry.username.isNotEmpty()) Text(entry.username)
                if (warnings.isNotEmpty()) {
                    Text(
                        warnings.joinToString(" · "),
                        color = if (status == RotationStatus.DUE_SOON && warnings.size == 1) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        },
        trailingContent = {
            if (entry.password.isNotEmpty()) TextButton(onClick = onCopy) { Text("Copy") }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/** Asks once per app start for permission to show reminders (Android 13 and newer). */
@Composable
private fun AskForNotificationPermission(needed: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(needed) {
        if (needed &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
