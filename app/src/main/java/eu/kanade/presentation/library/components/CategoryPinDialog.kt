package eu.kanade.presentation.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

// SY -->
/**
 * Shows a modal dialog that lets the user enter or set a numeric PIN for a category.
 *
 * In unlock mode (isSettingPin = false) the dialog prompts for an existing PIN and invokes
 * [onPinEntered] when a PIN of length 4–10 is submitted; if the callback returns `true` the
 * dialog is dismissed, otherwise an error is shown and input is cleared with a shake animation.
 *
 * In setup mode (isSettingPin = true) the dialog first collects a PIN (4–10 digits), then asks
 * for confirmation. If the confirmation matches the first PIN, [onPinEntered] is invoked and the
 * dialog is dismissed when it returns `true`; mismatches or a failing callback show an error and
 * reset the flow. Visual feedback includes PIN dots, error text, and a shake animation on errors.
 *
 * @param categoryName Display name of the category shown in the dialog title.
 * @param onDismiss Called to dismiss the dialog (e.g., Cancel or successful completion).
 * @param onPinEntered Callback invoked with the entered PIN. Should return `true` on success to
 *   dismiss the dialog, or `false` to indicate failure (which displays an error and resets input).
 * @param isSettingPin When `true`, the dialog runs the two-step set-and-confirm flow; when `false`,
 *   the dialog runs the single-step unlock flow. Defaults to `false`.
 */
@Composable
fun CategoryPinDialog(
    categoryName: String,
    onDismiss: () -> Unit,
    onPinEntered: (String) -> Boolean,
    isSettingPin: Boolean = false,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val title = if (isSettingPin) {
        if (showConfirm) {
            stringResource(SYMR.strings.category_lock_confirm_pin)
        } else {
            stringResource(SYMR.strings.category_lock_set_pin, categoryName)
        }
    } else {
        stringResource(SYMR.strings.category_lock_enter_pin, categoryName)
    }

    val currentPin = if (showConfirm) confirmPin else pin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = shakeOffset.value
                    },
            ) {
                // PIN dots display
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                ) {
                    repeat(10) { index ->
                        PinDot(filled = index < currentPin.length)
                        if (index < 9) Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                // PIN requirement text
                Text(
                    text = stringResource(SYMR.strings.category_lock_pin_requirement),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                // Number pad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Rows 1-3 (1-9)
                    for (row in 0..2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            for (col in 1..3) {
                                val number = row * 3 + col
                                NumberButton(
                                    number = number.toString(),
                                    onClick = {
                                        if (currentPin.length < 10) {
                                            if (showConfirm) {
                                                confirmPin += number
                                            } else {
                                                pin += number
                                            }
                                            errorMessage = null
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Bottom row (0 and backspace)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        NumberButton(
                            number = "0",
                            onClick = {
                                if (currentPin.length < 10) {
                                    if (showConfirm) {
                                        confirmPin += "0"
                                    } else {
                                        pin += "0"
                                    }
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (showConfirm) {
                                    if (confirmPin.isNotEmpty()) {
                                        confirmPin = confirmPin.dropLast(1)
                                    }
                                    // Don't allow editing first PIN during confirmation
                                } else if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                }
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = stringResource(MR.strings.action_delete),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val wrongPinText = stringResource(SYMR.strings.category_lock_wrong_pin)
            val pinRequirementText = stringResource(SYMR.strings.category_lock_pin_requirement)
            val pinsMismatchText = stringResource(SYMR.strings.category_lock_pins_mismatch)
            val failedToSetText = stringResource(SYMR.strings.category_lock_failed_to_set)
            val nextText = stringResource(SYMR.strings.action_next)
            val okText = stringResource(MR.strings.action_ok)

            TextButton(
                onClick = {
                    if (isSettingPin) {
                        if (!showConfirm) {
                            // First PIN entry
                            if (pin.length in 4..10) {
                                showConfirm = true
                                errorMessage = null
                            } else {
                                errorMessage = pinRequirementText
                                coroutineScope.launch {
                                    shakeOffset.shake()
                                }
                            }
                        } else {
                            // Confirmation entry
                            if (confirmPin == pin) {
                                val success = onPinEntered(pin)
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = failedToSetText
                                    pin = ""
                                    confirmPin = ""
                                    showConfirm = false
                                }
                            } else {
                                errorMessage = pinsMismatchText
                                confirmPin = ""
                                coroutineScope.launch {
                                    shakeOffset.shake()
                                }
                            }
                        }
                    } else {
                        // Unlock mode
                        if (pin.length in 4..10) {
                            val success = onPinEntered(pin)
                            if (success) {
                                onDismiss()
                            } else {
                                errorMessage = wrongPinText
                                pin = ""
                                coroutineScope.launch {
                                    shakeOffset.shake()
                                }
                            }
                        }
                    }
                },
                enabled = currentPin.length in 4..10,
            ) {
                Text(
                    text = if (isSettingPin && !showConfirm) {
                        nextText
                    } else {
                        okText
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/**
 * Renders a circular dot used to indicate a PIN digit's filled state.
 *
 * @param filled `true` to display the dot as filled (primary color), `false` to display it as empty (surface variant).
 * @param modifier Optional layout [Modifier] applied to the dot.
 */
@Composable
private fun PinDot(
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = if (filled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = modifier.size(12.dp),
    ) {}
}

/**
 * A circular numeric keypad button that displays a single digit and invokes an action when pressed.
 *
 * @param number The label shown on the button (typically a single digit like "0"–"9").
 * @param onClick Callback invoked when the button is pressed.
 * @param modifier Optional layout modifier applied to the button.
 */
@Composable
private fun NumberButton(
    number: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

private suspend fun Animatable<Float, *>.shake() {
    animateTo(10f, spring(stiffness = Spring.StiffnessHigh))
    animateTo(-10f, spring(stiffness = Spring.StiffnessHigh))
    animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
}
// SY <--
