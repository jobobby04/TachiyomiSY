package eu.kanade.presentation.browse.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.roundedfilled.ViewModule
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseSourceSimpleToolbar(
    navigateUp: () -> Unit,
    title: String,
    displayMode: LibraryDisplayMode?,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppBar(
        navigateUp = navigateUp,
        title = title,
        actions = {
            var selectingDisplayMode by remember { mutableStateOf(false) }
            AppBarActions(
                // SY -->
                actions = listOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_display_mode),
                        icon = MaterialSymbols.RoundedFilled.ViewModule,
                        onClick = { selectingDisplayMode = true },
                    ),
                ),
            )
            DropdownMenu(
                expanded = selectingDisplayMode,
                onDismissRequest = { selectingDisplayMode = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_comfortable_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.ComfortableGrid) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.ComfortableGrid) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.CompactGrid) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.CompactGrid) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(MR.strings.action_display_list)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.List) },
                    trailingIcon = {
                        if (displayMode == LibraryDisplayMode.List) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Check,
                                contentDescription = "",
                            )
                        }
                    },
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
