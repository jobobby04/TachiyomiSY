package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.util.fastForEach
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibrarySettingsViewModel
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import kotlinx.coroutines.flow.map
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.BaseSortItem
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.IconItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun LibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    viewModel: LibrarySettingsViewModel,
    category: Category?,
    // SY -->
    hasCategories: Boolean,
    // SY <--
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
            // SY -->
            stringResource(SYMR.strings.group),
            // SY <--
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(
                    viewModel = viewModel,
                )
                1 -> SortPage(
                    category = category,
                    viewModel = viewModel,
                )
                2 -> DisplayPage(
                    viewModel = viewModel,
                )
                // SY -->
                3 -> GroupPage(
                    viewModel = viewModel,
                    hasCategories = hasCategories,
                )
                // SY <--
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    viewModel: LibrarySettingsViewModel,
) {
    val filterDownloaded by viewModel.libraryPreferences.filterDownloaded.collectAsState()
    val downloadedOnly by viewModel.preferences.downloadedOnly.collectAsState()
    val autoUpdateMangaRestrictions by viewModel.libraryPreferences.autoUpdateMangaRestrictions.collectAsState()

    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = if (downloadedOnly) {
            TriState.ENABLED_IS
        } else {
            filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = { viewModel.toggleFilter(LibraryPreferences::filterDownloaded) },
    )
    val filterUnread by viewModel.libraryPreferences.filterUnread.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filterUnread,
        onClick = { viewModel.toggleFilter(LibraryPreferences::filterUnread) },
    )
    val filterStarted by viewModel.libraryPreferences.filterStarted.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filterStarted,
        onClick = { viewModel.toggleFilter(LibraryPreferences::filterStarted) },
    )
    val filterBookmarked by viewModel.libraryPreferences.filterBookmarked.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { viewModel.toggleFilter(LibraryPreferences::filterBookmarked) },
    )
    val filterCompleted by viewModel.libraryPreferences.filterCompleted.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.completed),
        state = filterCompleted,
        onClick = { viewModel.toggleFilter(LibraryPreferences::filterCompleted) },
    )
    // TODO: re-enable when custom intervals are ready for stable
    if (
        (isDebugBuildType || isPreviewBuildType) &&
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in autoUpdateMangaRestrictions
    ) {
        val filterIntervalCustom by viewModel.libraryPreferences.filterIntervalCustom.collectAsState()
        TriStateItem(
            label = stringResource(MR.strings.action_filter_interval_custom),
            state = filterIntervalCustom,
            onClick = { viewModel.toggleFilter(LibraryPreferences::filterIntervalCustom) },
        )
    }
    // SY -->
    val filterLewd by viewModel.libraryPreferences.filterLewd.collectAsState()
    TriStateItem(
        label = stringResource(SYMR.strings.lewd),
        state = filterLewd,
        onClick = { viewModel.toggleFilter(LibraryPreferences::filterLewd) },
    )
    // SY <--

    val trackers by viewModel.trackersFlow.collectAsState()
    when (trackers.size) {
        0 -> {
            // No trackers
        }
        1 -> {
            val service = trackers[0]
            val filterTracker by viewModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
            TriStateItem(
                label = stringResource(MR.strings.action_filter_tracked),
                state = filterTracker,
                onClick = { viewModel.toggleTracker(service.id.toInt()) },
            )
        }
        else -> {
            HeadingItem(MR.strings.action_filter_tracked)
            trackers.map { service ->
                val filterTracker by viewModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
                TriStateItem(
                    label = service.name,
                    state = filterTracker,
                    onClick = { viewModel.toggleTracker(service.id.toInt()) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SortPage(
    category: Category?,
    viewModel: LibrarySettingsViewModel,
) {
    val trackers by viewModel.trackersFlow.collectAsState()
    // SY -->
    val globalSortMode by viewModel.libraryPreferences.sortingMode.collectAsState()
    val sortingMode = if (viewModel.grouping == LibraryGroup.BY_DEFAULT) {
        category.sort.type
    } else {
        globalSortMode.type
    }
    val sortDescending = if (viewModel.grouping == LibraryGroup.BY_DEFAULT) {
        !category.sort.isAscending
    } else {
        !globalSortMode.isAscending
    }
    val hasSortTags by remember {
        viewModel.libraryPreferences.sortTagsForLibrary.changes()
            .map { it.isNotEmpty() }
    }.collectAsState(initial = viewModel.libraryPreferences.sortTagsForLibrary.get().isNotEmpty())
    // SY <--

    val options = remember(trackers.isEmpty()/* SY --> */, hasSortTags/* SY <-- */) {
        val trackerMeanPair = if (trackers.isNotEmpty()) {
            MR.strings.action_sort_tracker_score to LibrarySort.Type.TrackerMean
        } else {
            null
        }
        // SY -->
        val tagSortPair = if (hasSortTags) {
            SYMR.strings.tag_sorting to LibrarySort.Type.TagList
        } else {
            null
        }
        // SY <--
        listOfNotNull(
            MR.strings.action_sort_alpha to LibrarySort.Type.Alphabetical,
            MR.strings.action_sort_total to LibrarySort.Type.TotalChapters,
            MR.strings.action_sort_last_read to LibrarySort.Type.LastRead,
            MR.strings.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
            MR.strings.action_sort_unread_count to LibrarySort.Type.UnreadCount,
            MR.strings.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
            MR.strings.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
            MR.strings.action_sort_date_added to LibrarySort.Type.DateAdded,
            trackerMeanPair,
            // SY -->
            tagSortPair,
            // SY <--
            MR.strings.action_sort_random to LibrarySort.Type.Random,
        )
    }

    options.map { (titleRes, mode) ->
        if (mode == LibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(titleRes),
                icon = MaterialSymbols.Rounded.Refresh
                    .takeIf { sortingMode == LibrarySort.Type.Random },
                onClick = {
                    viewModel.setSort(category, mode, LibrarySort.Direction.Ascending)
                },
            )
            return@map
        }
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) {
                        LibrarySort.Direction.Ascending
                    } else {
                        LibrarySort.Direction.Descending
                    }
                    else -> if (sortDescending) {
                        LibrarySort.Direction.Descending
                    } else {
                        LibrarySort.Direction.Ascending
                    }
                }
                viewModel.setSort(category, mode, direction)
            },
        )
    }
}

private val displayModes = listOf(
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(
    viewModel: LibrarySettingsViewModel,
) {
    val displayMode by viewModel.libraryPreferences.displayMode.collectAsState()
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { viewModel.setDisplayMode(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    if (displayMode != LibraryDisplayMode.List) {
        val configuration = LocalConfiguration.current
        val columnPreference = remember {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewModel.libraryPreferences.landscapeColumns
            } else {
                viewModel.libraryPreferences.portraitColumns
            }
        }

        val columns by columnPreference.collectAsState()
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueString = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(MR.strings.overlay_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = viewModel.libraryPreferences.downloadBadge,
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = viewModel.libraryPreferences.unreadBadge,
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        pref = viewModel.libraryPreferences.localBadge,
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = viewModel.libraryPreferences.languageBadge,
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_continue_reading_button),
        pref = viewModel.libraryPreferences.showContinueReadingButton,
    )

    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_tabs),
        pref = viewModel.libraryPreferences.categoryTabs,
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_number_of_items),
        pref = viewModel.libraryPreferences.categoryNumberOfItems,
    )
}

// SY -->
data class GroupMode(
    val int: Int,
    val nameRes: StringResource,
    val drawableRes: Int,
)

private fun groupTypeDrawableRes(type: Int): Int {
    return when (type) {
        LibraryGroup.BY_STATUS -> R.drawable.ic_progress_clock_24dp
        LibraryGroup.BY_TRACK_STATUS -> R.drawable.ic_sync_24dp
        LibraryGroup.BY_SOURCE -> R.drawable.ic_browse_filled_24dp
        LibraryGroup.UNGROUPED -> R.drawable.ic_ungroup_24dp
        else -> R.drawable.ic_label_24dp
    }
}

fun groupTypeStringRes(type: Int, hasCategories: Boolean = true): StringResource {
    return when (type) {
        LibraryGroup.BY_STATUS -> MR.strings.status
        LibraryGroup.BY_SOURCE -> MR.strings.label_sources
        LibraryGroup.BY_TRACK_STATUS -> SYMR.strings.tracking_status
        LibraryGroup.UNGROUPED -> SYMR.strings.ungrouped
        else -> if (hasCategories) MR.strings.categories else SYMR.strings.ungrouped
    }
}

@Composable
private fun ColumnScope.GroupPage(
    viewModel: LibrarySettingsViewModel,
    hasCategories: Boolean,
) {
    val trackers by viewModel.trackersFlow.collectAsState()
    val groups = remember(hasCategories, trackers) {
        buildList {
            add(LibraryGroup.BY_DEFAULT)
            add(LibraryGroup.BY_SOURCE)
            add(LibraryGroup.BY_STATUS)
            if (trackers.isNotEmpty()) {
                add(LibraryGroup.BY_TRACK_STATUS)
            }
            if (hasCategories) {
                add(LibraryGroup.UNGROUPED)
            }
        }.map {
            GroupMode(
                it,
                groupTypeStringRes(it, hasCategories),
                groupTypeDrawableRes(it),
            )
        }
    }

    groups.fastForEach {
        IconItem(
            label = stringResource(it.nameRes),
            icon = painterResource(it.drawableRes),
            selected = it.int == viewModel.grouping,
            onClick = {
                viewModel.setGrouping(it.int)
            },
        )
    }
}

// SY <--
