package com.rushi.tijori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rushi.tijori.core.GeneratorSpec
import com.rushi.tijori.core.PasswordGenerator
import com.rushi.tijori.core.Policies
import com.rushi.tijori.core.TagPolicy
import kotlin.math.roundToInt

/**
 * Password generator. [policy] is the combined rule of the entry's tags: its
 * minimum length and required character kinds cannot be switched off here.
 */
@Composable
fun GeneratorDialog(
    policy: TagPolicy,
    confirmLabel: String,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val minLength = maxOf(8, policy.minLength).coerceAtMost(PasswordGenerator.MAX_LENGTH)
    val maxLength = maxOf(64, minLength)

    var spec by remember {
        mutableStateOf(Policies.generatorSpec(policy, GeneratorSpec(length = maxOf(20, minLength))))
    }
    var password by remember { mutableStateOf(PasswordGenerator.generate(spec)) }

    fun update(next: GeneratorSpec) {
        // Keep whatever the tag rules demand, and never allow an empty alphabet.
        val enforced = Policies.generatorSpec(policy, next.copy(length = next.length.coerceIn(minLength, maxLength)))
        if (!enforced.upper && !enforced.lower && !enforced.digits && !enforced.symbols) return
        spec = enforced
        password = PasswordGenerator.generate(enforced)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate a password") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(password, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { password = PasswordGenerator.generate(spec) }) { Text("Another one") }

                Text("Length: ${spec.length}")
                if (maxLength > minLength) {
                    Slider(
                        value = spec.length.toFloat(),
                        onValueChange = { update(spec.copy(length = it.roundToInt())) },
                        valueRange = minLength.toFloat()..maxLength.toFloat(),
                    )
                }
                CheckRow("Uppercase letters", spec.upper, enabled = !policy.requireUpper) {
                    update(spec.copy(upper = it))
                }
                CheckRow("Lowercase letters", spec.lower, enabled = !policy.requireLower) {
                    update(spec.copy(lower = it))
                }
                CheckRow("Digits", spec.digits, enabled = !policy.requireDigit) {
                    update(spec.copy(digits = it))
                }
                CheckRow("Symbols", spec.symbols, enabled = !policy.requireSymbol) {
                    update(spec.copy(symbols = it))
                }
                CheckRow("Avoid look-alikes (O 0 l 1)", spec.avoidAmbiguous) {
                    update(spec.copy(avoidAmbiguous = it))
                }
                if (policy != TagPolicy()) {
                    Text(
                        "Tag rules applied: " + policySummary(policy),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onUse(password) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
