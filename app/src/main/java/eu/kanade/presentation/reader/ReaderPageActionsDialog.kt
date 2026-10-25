package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    // SY -->
    onSetAsCover: (useExtraPage: Boolean) -> Unit,
    onShare: (copy: Boolean, useExtraPage: Boolean) -> Unit,
    onSave: (useExtraPage: Boolean) -> Unit,
    onShareCombined: (copy: Boolean) -> Unit,
    onSaveCombined: () -> Unit,
    hasExtraPage: Boolean,
    // SY <--
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    // SY -->
    var useExtraPage by remember { mutableStateOf(false) }
    // SY <--

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(
                        // SY -->
                        if (hasExtraPage) {
                            SYMR.strings.action_set_first_page_cover
                        } else {
                            MR.strings.set_as_cover
                        },
                        // SY <--
                    ),
                    icon = MaterialSymbols.Rounded.Photo,
                    onClick = { showSetCoverDialog = true },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(
                        // SY -->
                        if (hasExtraPage) {
                            SYMR.strings.action_copy_first_page
                        } else {
                            MR.strings.action_copy_to_clipboard
                        },
                        // SY <--
                    ),
                    icon = MaterialSymbols.Rounded.ContentCopy,
                    onClick = {
                        // SY -->
                        onShare(true, false)
                        // SY <--
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(
                        // SY -->
                        if (hasExtraPage) {
                            SYMR.strings.action_share_first_page
                        } else {
                            MR.strings.action_share
                        },
                        // SY <--
                    ),
                    icon = MaterialSymbols.Rounded.Share,
                    onClick = {
                        // SY -->
                        onShare(false, false)
                        // SY <--
                        onDismissRequest()
                    },
                )

                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(
                        // SY -->
                        if (hasExtraPage) {
                            SYMR.strings.action_save_first_page
                        } else {
                            MR.strings.action_save
                        },
                        // SY <--
                    ),
                    icon = MaterialSymbols.Rounded.Save,
                    onClick = {
                        // SY -->
                        onSave(false)
                        // SY <--
                        onDismissRequest()
                    },
                )
            }
            if (hasExtraPage) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(SYMR.strings.action_set_second_page_cover),
                        icon = MaterialSymbols.Rounded.Photo,
                        onClick = {
                            useExtraPage = true
                            showSetCoverDialog = true
                        },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(SYMR.strings.action_copy_second_page),
                        icon = MaterialSymbols.Rounded.ContentCopy,
                        onClick = {
                            onShare(true, true)
                            onDismissRequest()
                        },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(SYMR.strings.action_share_second_page),
                        icon = MaterialSymbols.Rounded.Share,
                        onClick = {
                            onShare(false, true)
                            onDismissRequest()
                        },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(SYMR.strings.action_save_second_page),
                        icon = MaterialSymbols.Rounded.Save,
                        onClick = {
                            onSave(true)
                            onDismissRequest()
                        },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(SYMR.strings.action_copy_combined_page),
                        icon = MaterialSymbols.Rounded.ContentCopy,
                        onClick = {
                            onShareCombined(true)
                            onDismissRequest()
                        },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(SYMR.strings.action_share_combined_page),
                        icon = MaterialSymbols.Rounded.Share,
                        onClick = {
                            onShareCombined(false)
                            onDismissRequest()
                        },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(SYMR.strings.action_save_combined_page),
                        icon = MaterialSymbols.Rounded.Save,
                        onClick = {
                            onSaveCombined()
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }

    if (showSetCoverDialog) {
        SetCoverDialog(
            onConfirm = {
                // SY -->
                onSetAsCover(useExtraPage)
                showSetCoverDialog = false
                useExtraPage = false
                // SY <--
            },
            onDismiss = {
                showSetCoverDialog = false
                // SY -->
                useExtraPage = false
                // SY <--
            },
        )
    }
}

@Composable
private fun SetCoverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(stringResource(MR.strings.confirm_set_image_as_cover))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}
