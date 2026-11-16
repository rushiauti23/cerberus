package com.rushi.tijori.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.rushi.tijori.core.Entry
import com.rushi.tijori.core.Policies
import com.rushi.tijori.core.RotationStatus
import com.rushi.tijori.core.TagPolicy
import com.rushi.tijori.core.Violation
import java.text.DateFormat
import java.util.Date

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for one master password, for example the password of a backup file. */
@Composable
fun PasswordPromptDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(text)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Master password",
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

fun policySummary(policy: TagPolicy): String {
    val parts = mutableListOf<String>()
    parts += if (policy.rotationDays > 0) "Rotate every ${policy.rotationDays} days" else "Never expires"
    if (policy.minLength > 0) parts += "at least ${policy.minLength} characters"
    val classes = listOfNotNull(
        "uppercase".takeIf { policy.requireUpper },
        "lowercase".takeIf { policy.requireLower },
        "digit".takeIf { policy.requireDigit },
        "symbol".takeIf { policy.requireSymbol },
    )
    if (classes.isNotEmpty()) parts += "needs " + classes.joinToString(", ")
    return parts.joinToString(" · ")
}

fun violationText(violations: List<Violation>, policy: TagPolicy): String =
    "Breaks the tag rules: " + violations.joinToString(", ") {
        when (it) {
            Violation.TOO_SHORT -> "shorter than ${policy.minLength} characters"
            Violation.NEEDS_UPPER -> "no uppercase letter"
            Violation.NEEDS_LOWER -> "no lowercase letter"
            Violation.NEEDS_DIGIT -> "no digit"
            Violation.NEEDS_SYMBOL -> "no symbol"
        }
    }

/** A short line such as "Overdue by 3 days", or null when the entry never expires. */
fun rotationText(entry: Entry, policy: TagPolicy, now: Long): String? {
    val days = Policies.daysLeft(entry, policy, now) ?: return null
    return when (Policies.status(entry, policy, now)) {
        RotationStatus.OVERDUE -> when (val late = -days - 1) {
            0L, -1L -> "Rotation due today"
            1L -> "Rotation overdue by 1 day"
            else -> "Rotation overdue by $late days"
        }
        RotationStatus.DUE_SOON, RotationStatus.OK -> when (days) {
            0L -> "Rotation due within a day"
            1L -> "Rotation due in 1 day"
            else -> "Rotation due in $days days"
        }
        RotationStatus.NONE -> null
    }
}
