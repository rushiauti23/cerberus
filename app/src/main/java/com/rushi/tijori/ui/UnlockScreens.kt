package com.rushi.tijori.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

const val MIN_MASTER_PASSWORD_LENGTH = 10

@Composable
fun SetupScreen(
    snackbar: SnackbarHostState,
    onCreate: (String) -> Unit,
    onRestore: (Uri, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    val longEnough = password.length >= MIN_MASTER_PASSWORD_LENGTH
    val matches = password == repeat
    val ready = longEnough && matches

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Welcome to Tijori", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose a master password. It encrypts everything in the vault and is never stored. " +
                "If you forget it, nobody can recover your passwords, so pick something long " +
                "that you will remember, such as four or five unrelated words."
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            visible = visible,
            isError = password.isNotEmpty() && !longEnough,
        )
        if (password.isNotEmpty() && !longEnough) {
            Text(
                "Use at least $MIN_MASTER_PASSWORD_LENGTH characters.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PasswordField(
            value = repeat,
            onValueChange = { repeat = it },
            label = "Repeat master password",
            visible = visible,
            isError = repeat.isNotEmpty() && !matches,
            imeAction = ImeAction.Done,
            onDone = { if (ready) onCreate(password) },
        )
        if (repeat.isNotEmpty() && !matches) {
            Text(
                "The two passwords are different.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        CheckRow(label = "Show passwords", checked = visible, onChange = { visible = it })
        Button(onClick = { onCreate(password) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Create vault")
        }
        RestoreBackupButton(
            label = "Restore from a backup file",
            warning = "Enter the master password the backup was made with.",
            onRestore = onRestore,
        )
        SnackbarHost(snackbar)
    }
}

@Composable
fun UnlockScreen(
    error: String?,
    biometricEnabled: Boolean,
    onUnlock: (String) -> Unit,
    onBiometricUnlock: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Tijori is locked", style = MaterialTheme.typography.headlineMedium)
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            isError = error != null,
            imeAction = ImeAction.Done,
            onDone = { if (password.isNotEmpty()) onUnlock(password) },
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { onUnlock(password) },
            enabled = password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Unlock")
        }
        if (biometricEnabled) {
            OutlinedButton(onClick = onBiometricUnlock, modifier = Modifier.fillMaxWidth()) {
                Text("Unlock with fingerprint or face")
            }
        }
    }
}

/** Picks a backup file, asks for its master password, then calls [onRestore]. */
@Composable
fun RestoreBackupButton(
    label: String,
    warning: String,
    onRestore: (Uri, String) -> Unit,
) {
    var picked by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picked = uri
    }
    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(label) }

    val uri = picked
    if (uri != null) {
        PasswordPromptDialog(
            title = "Restore backup",
            text = warning,
            confirmLabel = "Restore",
            onConfirm = { password ->
                picked = null
                onRestore(uri, password)
            },
            onDismiss = { picked = null },
        )
    }
}
