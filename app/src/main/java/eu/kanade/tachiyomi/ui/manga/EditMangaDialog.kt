package eu.kanade.tachiyomi.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.toast
import exh.util.dropBlank
import exh.util.trimOrNull
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.Sync
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.History
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

@Composable
fun EditMangaDialog(
    manga: Manga,
    sourceName: String,
    onDismissRequest: () -> Unit,
    onPositiveClick: (
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLocal = remember(manga) { manga.isLocal() }

    val title = rememberTextFieldState(
        if (isLocal) {
            manga.title.takeIf { it != manga.url }
        } else {
            manga.title.takeIf { it != manga.ogTitle }
        }.orEmpty(),
    )
    val author = rememberTextFieldState(manga.author.takeIf { isLocal || it != manga.ogAuthor }.orEmpty())
    val artist = rememberTextFieldState(manga.artist.takeIf { isLocal || it != manga.ogArtist }.orEmpty())
    val thumbnailUrl = rememberTextFieldState(
        manga.thumbnailUrl.takeIf { isLocal || it != manga.ogThumbnailUrl }.orEmpty(),
    )
    val description = rememberTextFieldState(
        manga.description.takeIf { isLocal || it != manga.ogDescription }.orEmpty(),
    )
    val tags = remember { manga.genre.orEmpty().dropBlank().toMutableStateList() }
    var statusIndex by remember {
        mutableStateOf(if (manga.status != manga.ogStatus) manga.status.toStatusIndex() else 0)
    }

    var showAddTagsDialog by remember { mutableStateOf(false) }
    var trackerChoices by remember { mutableStateOf<List<Pair<Track, Tracker>>?>(null) }

    val ogTitle = if (isLocal) manga.url else manga.ogTitle

    fun resetTags() {
        tags.clear()
        if (!manga.genre.isNullOrEmpty() && !isLocal) {
            tags.addAll(manga.ogGenre.orEmpty())
        }
    }

    suspend fun autofillFromTracker(track: Track, tracker: Tracker) {
        try {
            val metadata = tracker.getMangaMetadata(track)
            metadata?.title?.takeIf { it.isNotBlank() }?.let { title.setTextAndPlaceCursorAtEnd(it) }
            metadata?.authors?.takeIf { it.isNotBlank() }?.let { author.setTextAndPlaceCursorAtEnd(it) }
            metadata?.artists?.takeIf { it.isNotBlank() }?.let { artist.setTextAndPlaceCursorAtEnd(it) }
            metadata?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { thumbnailUrl.setTextAndPlaceCursorAtEnd(it) }
            metadata?.description?.takeIf { it.isNotBlank() }?.let { description.setTextAndPlaceCursorAtEnd(it) }
        } catch (e: Throwable) {
            tracker.logcat(LogPriority.ERROR, e)
            context.toast(context.stringResource(MR.strings.track_error, tracker.name, e.message ?: ""))
        }
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column {
            EditMangaHeader(
                onFillFromTrackerClick = {
                    scope.launch {
                        val tracks = context.appGraph.getTracks.await(manga.id)
                            .mapNotNull { track ->
                                track to (context.appGraph.trackerManager.get(track.trackerId) ?: return@mapNotNull null)
                            }
                            .filterNot { (_, tracker) -> tracker is EnhancedTracker }
                        when {
                            tracks.isEmpty() -> context.toast(context.stringResource(SYMR.strings.entry_not_tracked))
                            tracks.size > 1 -> trackerChoices = tracks
                            else -> autofillFromTracker(tracks.first().first, tracks.first().second)
                        }
                    }
                },
                onResetInfoClick = {
                    title.clearText()
                    author.clearText()
                    artist.clearText()
                    thumbnailUrl.clearText()
                    description.clearText()
                    resetTags()
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MangaCover.Book(
                        data = manga,
                        modifier = Modifier.width(76.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = listOfNotNull(sourceName, ogTitle.takeIf { it.isNotBlank() }).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        StatusField(
                            selectedIndex = statusIndex,
                            onSelect = { statusIndex = it },
                        )
                    }
                }

                EditMangaTextField(
                    state = title,
                    label = stringResource(MR.strings.title),
                    original = ogTitle,
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditMangaTextField(
                        state = author,
                        label = stringResource(MR.strings.author),
                        original = manga.ogAuthor.takeUnless { isLocal },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    EditMangaTextField(
                        state = artist,
                        label = stringResource(MR.strings.artist),
                        original = manga.ogArtist.takeUnless { isLocal },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                EditMangaTextField(
                    state = thumbnailUrl,
                    label = stringResource(SYMR.strings.thumbnail_url),
                    original = manga.ogThumbnailUrl.takeUnless { isLocal },
                )
                EditMangaTextField(
                    state = description,
                    label = stringResource(SYMR.strings.entry_description),
                    original = manga.ogDescription.takeUnless { isLocal },
                    minLines = 3,
                )

                TagsSection(
                    tags = tags,
                    onAddTagsClick = { showAddTagsDialog = true },
                    onResetTagsClick = ::resetTags,
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Button(
                    onClick = {
                        onPositiveClick(
                            title.text.toString(),
                            author.text.toString(),
                            artist.text.toString(),
                            thumbnailUrl.text.toString(),
                            description.text.toString(),
                            tags.toList(),
                            statusIndex.toStatus()?.toLong(),
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            }
        }
    }

    if (showAddTagsDialog) {
        AddTagsDialog(
            onDismissRequest = { showAddTagsDialog = false },
            onConfirm = { newTags -> tags.addAll(newTags) },
        )
    }

    trackerChoices?.let { choices ->
        TrackerSelectDialog(
            tracks = choices,
            onDismissRequest = { trackerChoices = null },
            onTrackerSelect = { tracker, track ->
                scope.launch { autofillFromTracker(track, tracker) }
            },
        )
    }
}

@Composable
private fun EditMangaHeader(
    onFillFromTrackerClick: () -> Unit,
    onResetInfoClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(SYMR.strings.action_edit_info),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.MoreVert,
                    contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(SYMR.strings.fill_from_tracker)) },
                    onClick = {
                        expanded = false
                        onFillFromTrackerClick()
                    },
                    leadingIcon = { Icon(MaterialSymbols.Rounded.Sync, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(SYMR.strings.reset_info)) },
                    onClick = {
                        expanded = false
                        onResetInfoClick()
                    },
                    leadingIcon = { Icon(SYMaterialSymbols.Rounded.History, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun StatusField(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(StatusLabels[selectedIndex]),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(MR.strings.status)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            StatusLabels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun EditMangaTextField(
    state: TextFieldState,
    label: String,
    original: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    val originalValue = original?.takeIf { it.isNotBlank() }
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        // Keep the label up so the source value shows as the placeholder
        labelPosition = TextFieldLabelPosition.Cutout(isAlwaysMinimized = originalValue != null),
        placeholder = originalValue?.let {
            {
                Text(
                    text = it,
                    maxLines = minLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingIcon = if (state.text.isNotEmpty()) {
            {
                IconButton(onClick = { state.clearText() }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(MR.strings.action_reset),
                    )
                }
            }
        } else {
            null
        },
        supportingText = originalValue?.takeIf { state.text.isNotEmpty() }?.let {
            {
                Text(
                    text = stringResource(SYMR.strings.source_value_hint, it.replace("\n", " ")),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = minLines)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TagsSection(
    tags: SnapshotStateList<String>,
    onAddTagsClick: () -> Unit,
    onResetTagsClick: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(SYMR.strings.entry_tags),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResetTagsClick) {
                Text(stringResource(SYMR.strings.reset_tags))
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEachIndexed { index, tag ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(MR.strings.action_remove),
                            modifier = Modifier
                                .size(InputChipDefaults.IconSize)
                                .clickable { tags.removeAt(index) },
                        )
                    },
                )
            }
            AssistChip(
                onClick = onAddTagsClick,
                label = { Text(stringResource(SYMR.strings.add_tag)) },
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun AddTagsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(input.split(",").mapNotNull { it.trimOrNull() })
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
        title = { Text(stringResource(SYMR.strings.add_tags)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(SYMR.strings.multi_tags_comma_separated))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun TrackerSelectDialog(
    tracks: List<Pair<Track, Tracker>>,
    onDismissRequest: () -> Unit,
    onTrackerSelect: (
        tracker: Tracker,
        track: Track,
    ) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(stringResource(SYMR.strings.select_tracker))
        },
        text = {
            FlowRow(
                modifier = Modifier
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                tracks.forEach { (track, tracker) ->
                    TrackLogoIcon(
                        tracker,
                        onClick = {
                            onTrackerSelect(tracker, track)
                            onDismissRequest()
                        },
                    )
                }
            }
        },
    )
}

private val StatusLabels = listOf(
    MR.strings.label_default,
    MR.strings.ongoing,
    MR.strings.completed,
    MR.strings.licensed,
    MR.strings.publishing_finished,
    MR.strings.cancelled,
    MR.strings.on_hiatus,
)

private fun Long.toStatusIndex(): Int = when (toInt()) {
    SManga.UNKNOWN -> 0
    SManga.ONGOING -> 1
    SManga.COMPLETED -> 2
    SManga.LICENSED -> 3
    SManga.PUBLISHING_FINISHED, 61 -> 4
    SManga.CANCELLED, 62 -> 5
    SManga.ON_HIATUS, 63 -> 6
    else -> 0
}

private fun Int.toStatus(): Int? = when (this) {
    1 -> SManga.ONGOING
    2 -> SManga.COMPLETED
    3 -> SManga.LICENSED
    4 -> SManga.PUBLISHING_FINISHED
    5 -> SManga.CANCELLED
    6 -> SManga.ON_HIATUS
    else -> null
}
