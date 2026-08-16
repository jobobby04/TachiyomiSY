package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.SourceFeedScreen
import eu.kanade.presentation.browse.components.SourceFeedAddDialog
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.md.follows.MangaDexFollowsScreen
import exh.ui.ifSourcesLoaded
import exh.util.nullIfBlank
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.presentation.core.screens.LoadingScreen

class SourceFeedScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val viewModel = assistedMetroViewModel<SourceFeedViewModel, SourceFeedViewModel.Factory> {
            create(sourceId = sourceId)
        }
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        SourceFeedScreen(
            name = viewModel.source.name,
            isLoading = state.isLoading,
            items = state.items,
            hasFilters = state.filters.isNotEmpty(),
            onFabClick = viewModel::openFilterSheet,
            onClickBrowse = { onBrowseClick(navigator, viewModel.source) },
            onClickLatest = { onLatestClick(navigator, viewModel.source) },
            onClickSavedSearch = { onSavedSearchClick(navigator, viewModel.source, it) },
            onClickDelete = viewModel::openDeleteFeed,
            onClickManga = { onMangaClick(navigator, it) },
            onClickSearch = { onSearchClick(navigator, viewModel.source, it) },
            searchQuery = state.searchQuery,
            onSearchQueryChange = viewModel::search,
            getMangaState = { viewModel.getManga(initialManga = it) },
        )

        val onDismissRequest = viewModel::dismissDialog
        when (val dialog = state.dialog) {
            is SourceFeedViewModel.Dialog.AddFeed -> {
                SourceFeedAddDialog(
                    onDismissRequest = onDismissRequest,
                    name = dialog.name,
                    addFeed = {
                        viewModel.createFeed(dialog.feedId)
                        onDismissRequest()
                    },
                )
            }
            is SourceFeedViewModel.Dialog.DeleteFeed -> {
                SourceFeedDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    deleteFeed = {
                        viewModel.deleteFeed(dialog.feed)
                        onDismissRequest()
                    },
                )
            }
            SourceFeedViewModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = {},
                    onFilter = {
                        viewModel.onFilter { query, filters ->
                            onBrowseClick(
                                navigator = navigator,
                                sourceId = sourceId,
                                search = query,
                                filters = filters,
                            )
                        }
                    },
                    onUpdate = viewModel::setFilters,
                    startExpanded = viewModel.startExpanded,
                    onSave = {},
                    savedSearches = state.savedSearches,
                    onSavedSearch = { search ->
                        viewModel.onSavedSearch(
                            search,
                            onBrowseClick = { query, searchId ->
                                onBrowseClick(
                                    navigator = navigator,
                                    sourceId = sourceId,
                                    search = query,
                                    savedSearch = searchId,
                                )
                            },
                            onToast = {
                                context.toast(it)
                            },
                        )
                    },
                    onSavedSearchPress = { search ->
                        viewModel.onSavedSearchAddToFeed(search) {
                            context.toast(it)
                        }
                    },
                    openMangaDexRandom = if (viewModel.sourceIsMangaDex) {
                        {
                            viewModel.onMangaDexRandom {
                                navigator.replace(
                                    BrowseSourceScreen(
                                        sourceId,
                                        "id:$it",
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    openMangaDexFollows = if (viewModel.sourceIsMangaDex) {
                        {
                            navigator.replace(MangaDexFollowsScreen(sourceId))
                        }
                    } else {
                        null
                    },
                )
            }
            null -> Unit
        }

        BackHandler(state.searchQuery != null) {
            viewModel.search(null)
        }
    }

    private fun onMangaClick(navigator: Navigator, manga: Manga) {
        navigator.push(MangaScreen(manga.id, true))
    }

    fun onBrowseClick(navigator: Navigator, sourceId: Long, search: String? = null, savedSearch: Long? = null, filters: String? = null) {
        navigator.replace(BrowseSourceScreen(sourceId, search, savedSearch = savedSearch, filtersJson = filters))
    }

    private fun onLatestClick(navigator: Navigator, source: Source) {
        navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
    }

    fun onBrowseClick(navigator: Navigator, source: Source) {
        navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR))
    }

    private fun onSavedSearchClick(navigator: Navigator, source: Source, savedSearch: SavedSearch) {
        navigator.replace(BrowseSourceScreen(source.id, listingQuery = null, savedSearch = savedSearch.id))
    }

    private fun onSearchClick(navigator: Navigator, source: Source, query: String) {
        onBrowseClick(navigator, source.id, query.nullIfBlank())
    }
}
