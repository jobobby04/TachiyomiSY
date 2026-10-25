package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.FeedAddDialog
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.FeedDeleteConfirmDialog
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * SY-specific file
 */
@Composable
fun Screen.feedTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<FeedViewModel>()
    val state by viewModel.state.collectAsState()

    DisposableEffect(navigator.lastEvent) {
        if (navigator.lastEvent == StackEvent.Push) {
            viewModel.pushed = true
        } else if (!viewModel.pushed) {
            viewModel.init()
        }

        onDispose {
            if (navigator.lastEvent == StackEvent.Idle && viewModel.pushed) {
                viewModel.pushed = false
            }
        }
    }

    return TabContent(
        titleRes = SYMR.strings.feed,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_add),
                icon = MaterialSymbols.Rounded.Add,
                onClick = {
                    viewModel.openAddDialog()
                },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            FeedScreen(
                state = state,
                contentPadding = contentPadding,
                onClickSavedSearch = { savedSearch, source ->
                    viewModel.sourcePreferences.lastUsedSource.set(savedSearch.source)
                    navigator.push(
                        BrowseSourceScreen(
                            source.id,
                            listingQuery = null,
                            savedSearch = savedSearch.id,
                        ),
                    )
                },
                onClickSource = { source ->
                    viewModel.sourcePreferences.lastUsedSource.set(source.id)
                    navigator.push(
                        BrowseSourceScreen(
                            source.id,
                            GetRemoteManga.QUERY_LATEST,
                        ),
                    )
                },
                onClickDelete = viewModel::openDeleteDialog,
                onClickManga = { manga ->
                    navigator.push(MangaScreen(manga.id, true))
                },
                onRefresh = viewModel::init,
                getMangaState = { manga -> viewModel.getManga(initialManga = manga) },
            )

            state.dialog?.let { dialog ->
                when (dialog) {
                    is FeedViewModel.Dialog.AddFeed -> {
                        FeedAddDialog(
                            sources = dialog.options,
                            onDismiss = viewModel::dismissDialog,
                            onClickAdd = {
                                if (it != null) {
                                    viewModel.openAddSearchDialog(it)
                                }
                                viewModel.dismissDialog()
                            },
                        )
                    }
                    is FeedViewModel.Dialog.AddFeedSearch -> {
                        FeedAddSearchDialog(
                            source = dialog.source,
                            savedSearches = dialog.options,
                            onDismiss = viewModel::dismissDialog,
                            onClickAdd = { source, savedSearch ->
                                viewModel.createFeed(source, savedSearch)
                                viewModel.dismissDialog()
                            },
                        )
                    }
                    is FeedViewModel.Dialog.DeleteFeed -> {
                        FeedDeleteConfirmDialog(
                            feed = dialog.feed,
                            onDismiss = viewModel::dismissDialog,
                            onClickDeleteConfirm = {
                                viewModel.deleteFeed(it)
                                viewModel.dismissDialog()
                            },
                        )
                    }
                }
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            val tooManyFeedsString = stringResource(SYMR.strings.too_many_in_feed)
            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        FeedViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                        FeedViewModel.Event.TooManyFeeds -> {
                            launch { snackbarHostState.showSnackbar(tooManyFeedsString) }
                        }
                    }
                }
            }
        },
    )
}
