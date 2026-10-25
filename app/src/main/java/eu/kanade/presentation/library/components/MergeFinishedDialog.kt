package eu.kanade.presentation.library.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

/**
 * SY-specific file
 */
@Composable
fun MergeFinishedDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (removeEntries: Boolean) -> Unit,
) {
    var removeEntries by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onConfirm(removeEntries) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(stringResource(SYMR.strings.entry_merged))
        },
        text = {
            LabeledCheckbox(
                label = stringResource(SYMR.strings.remove_merged_entries),
                checked = removeEntries,
                onCheckedChange = { removeEntries = it },
            )
        },
    )
}
