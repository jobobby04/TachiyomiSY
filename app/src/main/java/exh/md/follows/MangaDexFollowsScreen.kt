package exh.md.follows

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * SY-specific file
 */
class MangaDexFollowsScreen(private val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val viewModel = assistedMetroViewModel<MangaDexFollowsViewModel, MangaDexFollowsViewModel.Factory> {
            create(sourceId = sourceId)
        }
        val state by viewModel.state.collectAsState()

        if (state.source == null) {
            LoadingScreen()
            return
        }

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                BrowseSourceSimpleToolbar(
                    title = stringResource(SYMR.strings.mangadex_follows),
                    displayMode = viewModel.displayMode,
                    onDisplayModeChange = { viewModel.displayMode = it },
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) { paddingValues ->
            BrowseSourceContent(
                source = state.source,
                mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
                columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = viewModel.ehentaiBrowseDisplayMode,
                // SY <--
                displayMode = viewModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = null,
                onHelpClick = null,
                onLocalSourceHelpClick = null,
                onMangaClick = { navigator.push(MangaScreen(it.id, true)) },
                onMangaLongClick = { manga ->
                    scope.launch(Dispatchers.IO) {
                        val duplicateManga = viewModel.getDuplicateLibraryManga(manga)
                        when {
                            manga.favorite -> viewModel.setDialog(BrowseSourceViewModel.Dialog.RemoveManga(manga))
                            duplicateManga.isNotEmpty() -> viewModel.setDialog(
                                BrowseSourceViewModel.Dialog.AddDuplicateManga(
                                    manga,
                                    duplicateManga,
                                ),
                            )
                            else -> viewModel.addFavorite(manga)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        }

        val onDismissRequest = { viewModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseSourceViewModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            is BrowseSourceViewModel.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { viewModel.setDialog(BrowseSourceViewModel.Dialog.Migrate(dialog.manga, it)) },
                )
            }
            is BrowseSourceViewModel.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        viewModel.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is BrowseSourceViewModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        navigator.push(CategoryScreen())
                    },
                    onConfirm = { include, _ ->
                        viewModel.changeMangaFavorite(dialog.manga)
                        viewModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            else -> {}
        }
    }
}
