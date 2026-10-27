package eu.kanade.tachiyomi.ui.manga.merged

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.util.system.toast
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.FileDownloadOff
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.Sync
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.ArrowDropDown
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * SY-specific file
 */
@Composable
fun EditMergedSettingsDialog(
    viewModel: EditMergedSettingsViewModel,
    onDismissRequest: () -> Unit,
    onMangaClick: (MergedEntry) -> Unit,
) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reload only while stale (first show, or after a previous dismiss/save)
    LaunchedEffect(state) {
        if (state is EditMergedSettingsViewModel.State.Loading) {
            viewModel.reload()
        }
    }

    // Dismissing the sheet marks the data stale so the next show reloads it
    val dismiss = {
        viewModel.markStale()
        onDismissRequest()
    }

    val ready = state as? EditMergedSettingsViewModel.State.Ready
    if (ready == null) {
        LoadingScreen(Modifier.systemBarsPadding())
        return
    }

    LaunchedEffect(ready.isInvalid) {
        if (ready.isInvalid) {
            context.toast(SYMR.strings.merged_references_invalid)
            dismiss()
        }
    }

    val entries = ready.entries
    val sortMode = ready.sortMode
    val isReorderEnabled = ready.isReorderEnabled

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // The header is the first item, entries follow it
        if (from.index < 1 || to.index < 1) return@rememberReorderableLazyListState
        viewModel.reorder(from.index - 1, to.index - 1)
    }

    AdaptiveSheet(onDismissRequest = dismiss) {
        Column {
            Text(
                text = stringResource(SYMR.strings.merge_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            )
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                item(key = "header") {
                    MergedSettingsHeader(
                        sortMode = sortMode,
                        onSortModeChange = viewModel::onSortModeChange,
                        infoOptions = entries.map {
                            InfoOption(it.manga, it.source.toString())
                        },
                        infoIndex = ready.infoIndex,
                        onInfoIndexChange = viewModel::onInfoIndexChange,
                        canReorder = isReorderEnabled,
                    )
                }
                items(entries, key = { it.entry.id }) { entry ->
                    ReorderableItem(reorderableState, key = entry.entry.id) {
                        MergedEntryItem(
                            entry = entry,
                            canReorder = isReorderEnabled,
                            onMangaClick = { onMangaClick(entry.entry) },
                            onDeleteClick = { viewModel.requestDelete(entry.entry) },
                            onToggleDownloadsClick = { viewModel.requestToggleDownloads(entry.entry.id) },
                            onToggleUpdatesClick = { viewModel.requestToggleUpdates(entry.entry.id) },
                        )
                    }
                }
            }
            HorizontalDivider()
            Button(
                onClick = {
                    viewModel.save()
                    dismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        }
    }

    ready.pendingConfirm?.let { confirm ->
        ConfirmDialog(
            title = confirm.title,
            text = confirm.text,
            onDismissRequest = viewModel::dismissPending,
            onConfirm = {
                val isDelete = confirm is EditMergedSettingsViewModel.PendingConfirm.Delete
                viewModel.confirmPending()
                if (isDelete) dismiss()
            },
        )
    }
}

@Composable
private fun MergedSettingsHeader(
    sortMode: Int,
    onSortModeChange: (Int) -> Unit,
    infoOptions: List<InfoOption>,
    infoIndex: Int,
    onInfoIndexChange: (Int) -> Unit,
    canReorder: Boolean,
) {
    // "Keep all" covers both the dedupe-off state and the legacy "No dedupe" mode, which behave the same
    val selectedMode = DedupeModes.firstOrNull { sortMode in it.sortModes } ?: DedupeModes.first()
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(SYMR.strings.merge_duplicate_chapters))
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DedupeModes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == selectedMode,
                        onClick = { if (mode != selectedMode) onSortModeChange(mode.sortModes.first()) },
                        shape = SegmentedButtonDefaults.itemShape(index, DedupeModes.size),
                    ) {
                        Text(
                            text = stringResource(mode.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text = stringResource(selectedMode.summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        SectionHeader(
            text = stringResource(SYMR.strings.merge_info_source),
            modifier = Modifier.padding(top = 4.dp),
        )
        InfoSourceSelector(
            options = infoOptions,
            selectedIndex = infoIndex,
            onSelect = onInfoIndexChange,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.label_titles_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (canReorder) {
                Text(
                    text = stringResource(SYMR.strings.merge_drag_to_set_priority),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun InfoSourceSelector(
    options: List<InfoOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.getOrNull(selectedIndex)
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { expanded = true }
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MangaCover.Book(
                data = selected?.manga,
                modifier = Modifier.size(width = 40.dp, height = 56.dp),
                shape = RoundedCornerShape(4.dp),
            )
            EntryTitle(
                title = selected?.manga?.title.orEmpty(),
                sourceName = selected?.sourceName.orEmpty(),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = SYMaterialSymbols.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        EntryTitle(
                            title = option.manga?.title.orEmpty(),
                            sourceName = option.sourceName,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryTitle(
    title: String,
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sourceName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.MergedEntryItem(
    entry: EditMergedSettingsViewModel.Entry,
    canReorder: Boolean,
    onMangaClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleDownloadsClick: () -> Unit,
    onToggleUpdatesClick: () -> Unit,
) {
    // Matches the sheet so rows stay opaque while dragged
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .draggableHandle(enabled = canReorder)
                    .alpha(if (canReorder) 1f else DISABLED_ALPHA)
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            )
            MangaCover.Book(
                data = entry.manga,
                modifier = Modifier.size(width = 40.dp, height = 56.dp),
                shape = RoundedCornerShape(4.dp),
                onClick = { onMangaClick() },
            )
            EntryTitle(
                title = entry.manga?.title.orEmpty(),
                sourceName = entry.source.toString(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            val downloads = entry.entry.downloadChapters
            EntryToggleButton(
                icon = if (downloads) MaterialSymbols.Rounded.Download else MaterialSymbols.Rounded.FileDownloadOff,
                contentDescription = stringResource(MR.strings.pref_download_new),
                active = downloads,
                onClick = onToggleDownloadsClick,
            )
            EntryToggleButton(
                icon = MaterialSymbols.Rounded.Sync,
                contentDescription = stringResource(SYMR.strings.fetch_chapter_updates),
                active = entry.entry.fetchChapterUpdates,
                onClick = onToggleUpdatesClick,
            )
            Box {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.MoreVert,
                        contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_remove)) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryToggleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: StringResource,
    text: StringResource,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
    )
}

private data class InfoOption(val manga: Manga?, val sourceName: String)

private class DedupeMode(
    // The first mode is the one written when this option is picked
    val sortModes: List<Int>,
    val label: StringResource,
    val summary: StringResource,
)

private val DedupeModes = listOf(
    DedupeMode(
        sortModes = listOf(MergeSettings.CHAPTER_SORT_NONE, MergeSettings.CHAPTER_SORT_NO_DEDUPE),
        label = SYMR.strings.merge_dedupe_keep_all,
        summary = SYMR.strings.merge_dedupe_keep_all_summary,
    ),
    DedupeMode(
        sortModes = listOf(MergeSettings.CHAPTER_SORT_PRIORITY),
        label = SYMR.strings.merge_dedupe_priority,
        summary = SYMR.strings.merge_dedupe_priority_summary,
    ),
    DedupeMode(
        sortModes = listOf(MergeSettings.CHAPTER_SORT_MOST_CHAPTERS),
        label = SYMR.strings.merge_dedupe_most,
        summary = SYMR.strings.merge_dedupe_most_summary,
    ),
    DedupeMode(
        sortModes = listOf(MergeSettings.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER),
        label = SYMR.strings.merge_dedupe_highest,
        summary = SYMR.strings.merge_dedupe_highest_summary,
    ),
)

private const val DISABLED_ALPHA = 0.5f
