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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import eu.kanade.tachiyomi.util.system.toast
import exh.source.MERGED_SOURCE_ID
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
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * SY-specific file
 */
@Composable
fun EditMergedSettingsDialog(
    onDismissRequest: () -> Unit,
    mergedData: MergedMangaData,
    onDeleteClick: (MergedMangaReference) -> Unit,
    onPositiveClick: (List<MergedMangaReference>) -> Unit,
) {
    val context = LocalContext.current
    val references = mergedData.references

    LaunchedEffect(Unit) {
        if (references.size <= 1) {
            context.toast(SYMR.strings.merged_references_invalid)
            onDismissRequest()
        }
    }

    var mergeReference by remember {
        mutableStateOf(references.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID })
    }
    val entries = remember {
        val mangas = mergedData.manga.values
        references
            .filter { it.mangaSourceId != MERGED_SOURCE_ID }
            .sortedBy { it.chapterPriority }
            .let { sorted ->
                // The info manga defaults to the first entry when none is set yet
                if (sorted.none { it.isInfoManga }) {
                    sorted.mapIndexed { index, reference -> reference.copy(isInfoManga = index == 0) }
                } else {
                    sorted
                }
            }
            .map { reference ->
                val manga = mangas.firstOrNull { it.id == reference.mangaId }
                MergedEntry(
                    manga,
                    reference,
                    mergedData.sources
                        .firstOrNull { it.id == manga?.source }
                        ?: StubSource(manga?.source ?: -1, lang = "", name = ""),
                )
            }
            .toMutableStateList()
    }
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }

    val sortMode = mergeReference?.chapterSortMode ?: MergedMangaReference.CHAPTER_SORT_NONE
    val isPriorityOrder = sortMode == MergedMangaReference.CHAPTER_SORT_PRIORITY

    fun updateEntry(id: Long, transform: (MergedMangaReference) -> MergedMangaReference) {
        val index = entries.indexOfFirst { it.reference.id == id }
        if (index != -1) entries[index] = entries[index].copy(reference = transform(entries[index].reference))
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // The header is the first item, entries follow it
        if (from.index < 1 || to.index < 1) return@rememberReorderableLazyListState
        entries.add(to.index - 1, entries.removeAt(from.index - 1))
        entries.forEachIndexed { index, entry ->
            entries[index] = entry.copy(reference = entry.reference.copy(chapterPriority = index))
        }
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
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
                        onSortModeChange = { mode -> mergeReference = mergeReference?.copy(chapterSortMode = mode) },
                        infoOptions = entries.map {
                            InfoOption(it.manga, it.source.toString())
                        },
                        infoIndex = entries.indexOfFirst { it.reference.isInfoManga }.coerceAtLeast(0),
                        onInfoIndexChange = { selected ->
                            val infoId = entries.getOrNull(selected)?.reference?.id
                            entries.toList().forEach { entry ->
                                updateEntry(entry.reference.id) { it.copy(isInfoManga = it.id == infoId) }
                            }
                        },
                        canReorder = isPriorityOrder,
                    )
                }
                items(entries, key = { it.reference.id }) { entry ->
                    ReorderableItem(reorderableState, key = entry.reference.id) {
                        MergedEntryItem(
                            entry = entry,
                            canReorder = isPriorityOrder,
                            onDeleteClick = { pendingConfirm = PendingConfirm.Delete(entry.reference) },
                            onToggleDownloadsClick = { pendingConfirm = PendingConfirm.Downloads(entry.reference.id) },
                            onToggleUpdatesClick = { pendingConfirm = PendingConfirm.Updates(entry.reference.id) },
                        )
                    }
                }
            }
            HorizontalDivider()
            Button(
                onClick = {
                    onPositiveClick(listOfNotNull(mergeReference) + entries.map { it.reference })
                    onDismissRequest()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        }
    }

    pendingConfirm?.let { confirm ->
        ConfirmDialog(
            title = confirm.title,
            text = confirm.text,
            onDismissRequest = { pendingConfirm = null },
            onConfirm = {
                when (confirm) {
                    is PendingConfirm.Delete -> {
                        onDeleteClick(confirm.reference)
                        onDismissRequest()
                    }
                    is PendingConfirm.Downloads -> updateEntry(confirm.id) {
                        it.copy(downloadChapters = !it.downloadChapters)
                    }
                    is PendingConfirm.Updates -> updateEntry(confirm.id) {
                        it.copy(getChapterUpdates = !it.getChapterUpdates)
                    }
                }
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
    entry: MergedEntry,
    canReorder: Boolean,
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
            )
            EntryTitle(
                title = entry.manga?.title.orEmpty(),
                sourceName = entry.source.toString(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            val downloads = entry.reference.downloadChapters
            EntryToggleButton(
                icon = if (downloads) MaterialSymbols.Rounded.Download else MaterialSymbols.Rounded.FileDownloadOff,
                contentDescription = stringResource(MR.strings.pref_download_new),
                active = downloads,
                onClick = onToggleDownloadsClick,
            )
            EntryToggleButton(
                icon = MaterialSymbols.Rounded.Sync,
                contentDescription = stringResource(SYMR.strings.fetch_chapter_updates),
                active = entry.reference.getChapterUpdates,
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

private data class MergedEntry(val manga: Manga?, val reference: MergedMangaReference, val source: Source)

private sealed class PendingConfirm(val title: StringResource, val text: StringResource) {
    data class Delete(val reference: MergedMangaReference) :
        PendingConfirm(SYMR.strings.delete_merged_entry, SYMR.strings.delete_merged_entry_desc)
    data class Downloads(val id: Long) :
        PendingConfirm(SYMR.strings.download_merged_entry, SYMR.strings.download_merged_entry_desc)
    data class Updates(val id: Long) :
        PendingConfirm(SYMR.strings.chapter_updates_merged_entry, SYMR.strings.chapter_updates_merged_entry_desc)
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
        sortModes = listOf(MergedMangaReference.CHAPTER_SORT_NONE, MergedMangaReference.CHAPTER_SORT_NO_DEDUPE),
        label = SYMR.strings.merge_dedupe_keep_all,
        summary = SYMR.strings.merge_dedupe_keep_all_summary,
    ),
    DedupeMode(
        sortModes = listOf(MergedMangaReference.CHAPTER_SORT_PRIORITY),
        label = SYMR.strings.merge_dedupe_priority,
        summary = SYMR.strings.merge_dedupe_priority_summary,
    ),
    DedupeMode(
        sortModes = listOf(MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS),
        label = SYMR.strings.merge_dedupe_most,
        summary = SYMR.strings.merge_dedupe_most_summary,
    ),
    DedupeMode(
        sortModes = listOf(MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER),
        label = SYMR.strings.merge_dedupe_highest,
        summary = SYMR.strings.merge_dedupe_highest_summary,
    ),
)

private const val DISABLED_ALPHA = 0.5f
