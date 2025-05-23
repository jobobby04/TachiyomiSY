package eu.kanade.presentation.library.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.tracker.components.TrackStatusTabs
import eu.kanade.presentation.library.tracker.components.mangaListItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class TrackerMangaListScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { TrackerMangaListScreenModel() }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()
        val scrollStates = remember {
            mutableStateMapOf<Int, Pair<Int, Int>>()
        }

        Scaffold(
            topBar = { scrollBehaviour ->
                TrackerMangaListAppBar(scrollBehaviour, navigator::pop)
            },
        ) { contentPadding ->
            when {
                state.statusList.isEmpty() -> LoadingScreen(modifier = Modifier.padding(contentPadding))

                else -> {
                    val pagerState = rememberPagerState(
                        initialPage = state.currentTabIndex.coerceIn(0, state.statusList.lastIndex),
                        pageCount = { state.statusList.size },
                    )

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
                        ) {
                            val currentTabIndex = pagerState.currentPage
                            val currentTabState = state.tabs[currentTabIndex] ?: TabMangaList()

                            val currentScrollState = remember {
                                LazyListState(
                                    firstVisibleItemIndex = scrollStates[pagerState.currentPage]?.first ?: 0,
                                    firstVisibleItemScrollOffset = scrollStates[pagerState.currentPage]?.second ?: 0,
                                )
                            }

                            LaunchedEffect(currentTabIndex, currentScrollState) {
                                snapshotFlow {
                                    val layoutInfo = currentScrollState.layoutInfo
                                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    lastVisible >= layoutInfo.totalItemsCount - 15
                                }.collect { shouldLoadMore ->
                                    if (shouldLoadMore) {
                                        screenModel.loadNextPage(currentTabIndex)
                                    }
                                }
                            }

                            LaunchedEffect(pagerState.currentPage) {
                                snapshotFlow {
                                    currentScrollState.firstVisibleItemIndex to currentScrollState.firstVisibleItemScrollOffset
                                }
                                    .collect { (index, offset) ->
                                        scrollStates[pagerState.currentPage] = index to offset
                                    }
                                screenModel.changeTab(pagerState.currentPage)
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
    }
}

@Composable
fun TrackerMangaListAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigateUp: () -> Unit,
) {
    AppBar(
        navigateUp = navigateUp,
        titleContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tracker Manga",
                    maxLines = 1,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
