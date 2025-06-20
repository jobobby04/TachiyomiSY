package eu.kanade.presentation.library.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.tracker.components.TrackStatusTabs
import eu.kanade.presentation.library.tracker.components.mangaListItem
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class TrackerMangaListScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val screenModel = rememberScreenModel { TrackerMangaListScreenModel() }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()
        val scrollStates = remember {
            mutableStateMapOf<Int, Pair<Int, Int>>()
        }

        Scaffold(
            topBar = { scrollBehaviour ->
                TrackerMangaListAppBar(screenModel.getTrackerName(), scrollBehaviour, navigator::pop, screenModel::toggleTrackerSelectDialog)
            },
        ) { contentPadding ->
            when {
                state.statusList.isEmpty() -> LoadingScreen(modifier = Modifier.padding(contentPadding))

                else -> {
                    val pagerState = rememberPagerState(
                        initialPage = state.currentTabIndex.coerceIn(0, state.statusList.lastIndex),
                        pageCount = { state.statusList.size },
                    )

                    LaunchedEffect(state.trackerId) {
                        pagerState.scrollToPage(0)
                    }

                    Column(modifier = Modifier.padding(contentPadding)) {
                        TrackStatusTabs(
                            statusList = state.statusList,
                            getStatusRes = state.getStatusRes,
                            pagerState = pagerState,
                        ) { index ->
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val currentTabState = state.tabs[page] ?: TabMangaList()

                            val currentScrollState = remember(page, state.trackerId) {
                                LazyListState(
                                    firstVisibleItemIndex = scrollStates[page]?.first ?: 0,
                                    firstVisibleItemScrollOffset = scrollStates[page]?.second ?: 0,
                                )
                            }

                            LaunchedEffect(page, currentScrollState, state.trackerId) {
                                snapshotFlow {
                                    val layoutInfo = currentScrollState.layoutInfo
                                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    lastVisible >= layoutInfo.totalItemsCount - 20
                                }.collect { shouldLoadMore ->
                                    if (shouldLoadMore) {
                                        try {
                                            screenModel.loadNextPage(page)
                                        } catch (e: Exception) {
                                            context.toast(
                                                context.stringResource(
                                                    MR.strings.track_error,
                                                    screenModel.getTrackerName(),
                                                    e.message ?: "",
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(page) {
                                snapshotFlow {
                                    currentScrollState.firstVisibleItemIndex to currentScrollState.firstVisibleItemScrollOffset
                                }
                                    .collect { (index, offset) ->
                                        scrollStates[page] = index to offset
                                    }
                                try {
                                    screenModel.changeTab(page)
                                } catch (e: Exception) {
                                    context.toast(
                                        context.stringResource(
                                            MR.strings.track_error,
                                            screenModel.getTrackerName(),
                                            e.message ?: "",
                                        ),
                                    )
                                }
                            }

                            val isFirstLoad = currentTabState.isLoading && currentTabState.items.isEmpty()
                            if (isFirstLoad) {
                                LoadingScreen(modifier = Modifier.fillMaxWidth())
                                return@HorizontalPager
                            }

                            if (currentTabState.items.isEmpty()) {
                                EmptyScreen(
                                    message = "All entries are in library.",
                                    modifier = Modifier.fillMaxSize(),
                                )
                                return@HorizontalPager
                            }

                            FastScrollLazyColumn(
                                state = currentScrollState,
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.Top,
                            ) {
                                mangaListItem(
                                    items = currentTabState.items,
                                    onClick = { item ->
                                        navigator.push(
                                            GlobalSearchScreen(searchQuery = item.title ?: ""),
                                        )
                                    },
                                )
                                if (currentTabState.isLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.trackerSelectDialog) {
            TrackerSelectDialog(
                screenModel.trackers,
                onDismissRequest = screenModel::toggleTrackerSelectDialog,
                onTrackerSelect = screenModel::changeTracker,
                currentTrackerId = state.trackerId!!,
            )
        }
    }
}

@Composable
fun TrackerMangaListAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigateUp: () -> Unit,
    onShowTrackerDialogClick: () -> Unit,
) {
    AppBar(
        navigateUp = navigateUp,
        titleContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    maxLines = 1,
                )
            }
        },
        actions = {
            IconButton(onClick = onShowTrackerDialogClick) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Select tracker",
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun TrackerSelectDialog(
    trackers: List<Tracker>,
    onDismissRequest: () -> Unit,
    onTrackerSelect: (Long) -> Unit,
    currentTrackerId: Long,
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
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                trackers.forEach { tracker ->
                    Box {
                        TrackLogoIcon(
                            tracker,
                            onClick = {
                                if (tracker.id != currentTrackerId) {
                                    onTrackerSelect(tracker.id)
                                }
                            },
                        )
                        if (tracker.id == currentTrackerId) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp),
                                tint = Color.Green,
                            )
                        }
                    }
                }
            }
        },
    )
}
