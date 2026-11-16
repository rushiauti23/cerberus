package com.rushi.tijori.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    snackbar: SnackbarHostState,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onSetBiometric: (Boolean) -> Unit,
    onChangePassword: (String, String) -> Unit,
    onExport: (Uri) -> Unit,
    onRestore: (Uri, String) -> Unit,
    onBack: () -> Unit,
) {
    var changingPassword by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) onExport(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings and backup") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Unlocking", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Unlock with fingerprint or face")
                    Text(
                        if (biometricAvailable) {
                            "Your master password still works and is still needed after the phone's biometrics change."
                        } else {
                            "Set up a fingerprint or face unlock in the phone's settings first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = onSetBiometric,
                    enabled = biometricAvailable || biometricEnabled,
                )
            }
            OutlinedButton(onClick = { changingPassword = true }) { Text("Change master password") }
            Text(
                "The vault locks itself one minute after you leave the app.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()

            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "A backup is one encrypted file that opens only with your master password. " +
                    "Keep a copy off this phone: if the phone is lost and there is no backup, the passwords are gone.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exporter.launch("tijori-backup-$stamp.tjr")
                },
            ) {
                Text("Export backup")
            }
            RestoreBackupButton(
                label = "Restore from a backup file",
                warning = "The backup replaces everything in the vault on this phone. " +
                    "Enter the master password the backup was made with; it becomes the master password here.",
                onRestore = onRestore,
            )

            HorizontalDivider()

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "The vault is encrypted with AES-256-GCM. The key is protected by your master password " +
                    "through Argon2id. The app has no internet permission.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (changingPassword) {
        ChangePasswordDialog(
            onConfirm = { current, new ->
                changingPassword = false
                onChangePassword(current, new)
            },
            onDismiss = { changingPassword = false },
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }

    val longEnough = new.length >= MIN_MASTER_PASSWORD_LENGTH
    val matches = new == repeat

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change master password") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                PasswordField(value = current, onValueChange = { current = it }, label = "Current password")
                PasswordField(
                    value = new,
                    onValueChange = { new = it },
                    label = "New password",
                    isError = new.isNotEmpty() && !longEnough,
                )
                PasswordField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = "Repeat new password",
                    isError = repeat.isNotEmpty() && !matches,
                    imeAction = ImeAction.Done,
                )
                Text(
                    "At least $MIN_MASTER_PASSWORD_LENGTH characters. Backups made earlier keep the old password.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current, new) },
                enabled = current.isNotEmpty() && longEnough && matches,
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
