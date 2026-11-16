package com.rushi.tijori.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rushi.tijori.data.SecureClipboard

@Composable
fun AppRoot(
    vm: VaultViewModel,
    biometricAvailable: Boolean,
    onBiometricUnlock: () -> Unit,
    onSetBiometric: (Boolean) -> Unit,
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notice = state.notice
    LaunchedEffect(notice?.id) {
        if (notice != null) snackbar.showSnackbar(notice.text)
    }

    val copySecret: (String) -> Unit = { text ->
        SecureClipboard.copy(context, text)
        vm.notify("Copied. The clipboard is cleared in 30 seconds.")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val vault = state.vault
        when {
            !state.hasVault -> SetupScreen(
                snackbar = snackbar,
                onCreate = vm::createVault,
                onRestore = vm::restoreFrom,
            )

            vault == null -> UnlockScreen(
                error = state.unlockError,
                biometricEnabled = state.biometricEnabled,
                onUnlock = vm::unlock,
                onBiometricUnlock = onBiometricUnlock,
            )

            else -> when (val screen = vm.screen) {
                Screen.Home -> HomeScreen(
                    vm = vm,
                    vault = vault,
                    snackbar = snackbar,
                    onCopy = copySecret,
                )

                is Screen.EditEntry -> key(screen.entryId) {
                    EntryScreen(
                        vault = vault,
                        original = vault.entries.firstOrNull { it.id == screen.entryId },
                        initialCategoryId = vm.categoryFilter,
                        snackbar = snackbar,
                        onSave = vm::saveEntry,
                        onDelete = vm::deleteEntry,
                        onBack = { vm.navigate(Screen.Home) },
                        onCopy = copySecret,
                    )
                }

                Screen.Organize -> {
                    BackHandler { vm.navigate(Screen.Home) }
                    OrganizeScreen(
                        vault = vault,
                        snackbar = snackbar,
                        onSaveCategory = vm::saveCategory,
                        onDeleteCategory = vm::deleteCategory,
                        onSaveTag = vm::saveTag,
                        onDeleteTag = vm::deleteTag,
                        onBack = { vm.navigate(Screen.Home) },
                    )
                }

                Screen.Settings -> {
                    BackHandler { vm.navigate(Screen.Home) }
                    SettingsScreen(
                        snackbar = snackbar,
                        biometricAvailable = biometricAvailable,
                        biometricEnabled = state.biometricEnabled,
                        onSetBiometric = onSetBiometric,
                        onChangePassword = vm::changePassword,
                        onExport = vm::exportTo,
                        onRestore = vm::restoreFrom,
                        onBack = { vm.navigate(Screen.Home) },
                    )
                }
            }
        }
    }

    if (state.busy) {
        Dialog(onDismissRequest = {}) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(24.dp)) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Working on the vault…")
                }
            }
        }
    }
}
