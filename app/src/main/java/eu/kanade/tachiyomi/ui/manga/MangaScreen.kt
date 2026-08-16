package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.manga.ChapterSettingsDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.MangaScreen
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.MangaCoverDialog
import eu.kanade.presentation.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.merged.EditMergedSettingsDialog
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import exh.pagepreview.PagePreviewScreen
import exh.recs.RecommendsScreen
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.ui.ifSourcesLoaded
import exh.ui.metadata.MetadataViewScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val viewModel =
            assistedMetroViewModel<MangaViewModel, MangaViewModel.Factory> {
                create(
                    mangaId = mangaId,
                    isFromSource = fromSource,
                    // SY -->
                    smartSearched = smartSearchConfig != null,
                    // SY <--
                )
            }

        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is MangaViewModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaViewModel.State.Success
        val isHttpSource = remember { successState.source is HttpSource }

        LaunchedEffect(successState.manga, viewModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(viewModel.manga, viewModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        // SY -->
        LaunchedEffect(Unit) {
            viewModel.redirectFlow
                .take(1)
                .onEach {
                    navigator.replace(
                        MangaScreen(it.mangaId),
                    )
                }
                .launchIn(this)
        }
        // SY <--

        MangaScreen(
            state = successState,
            snackbarHostState = viewModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = viewModel.chapterSwipeStartAction,
            chapterSwipeEndAction = viewModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = { openChapter(context, it) },
            onDownloadChapter = viewModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                viewModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            // SY -->
            onWebViewClicked = {
                if (successState.mergedData == null) {
                    openMangaInWebView(
                        navigator,
                        viewModel.manga,
                        viewModel.source,
                    )
                } else {
                    openMergedMangaWebview(
                        context,
                        navigator,
                        successState.mergedData,
                    )
                }
            }.takeIf { isHttpSource },
            // SY <--
            onWebViewLongClicked = {
                copyMangaUrl(
                    context,
                    viewModel.manga,
                    viewModel.source,
                )
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    viewModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, viewModel.source!!) } },
            onFilterButtonClicked = viewModel::showSettingsDialog,
            onRefresh = viewModel::fetchAllFromSource,
            onContinueReading = { continueReading(context, viewModel.getNextUnreadChapter()) },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = viewModel::showCoverDialog,
            onShareClicked = { shareManga(context, viewModel.manga, viewModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = viewModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = viewModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
            onEditFetchIntervalClicked = viewModel::showSetFetchIntervalDialog.takeIf {
                successState.manga.favorite
            },
            previewsRowCount = successState.previewsRowCount,
            onMigrateClicked = {
                navigator.push(MigrationConfigScreen(successState.manga.id))
            }.takeIf {
                successState.manga.favorite /* SY --> */ && successState.manga.source != MERGED_SOURCE_ID /* SY <-- */
            },
            onEditNotesClicked = { navigator.push(MangaNotesScreen(manga = successState.manga)) },
            // SY -->
            onMetadataViewerClicked = { openMetadataViewer(navigator, successState.manga) },
            onEditInfoClicked = viewModel::showEditMangaInfoDialog,
            onRecommendClicked = {
                openRecommends(navigator, viewModel.source?.getMainSource(), successState.manga)
            },
            onMergedSettingsClicked = viewModel::showEditMergedSettingsDialog,
            onMergeClicked = { openSmartSearch(navigator, successState.manga) },
            onMergeWithAnotherClicked = {
                mergeWithAnother(navigator, context, successState.manga, viewModel::smartSearchMerge)
            },
            onOpenPagePreview = {
                openPagePreview(context, successState.chapters.minByOrNull { it.chapter.sourceOrder }?.chapter, it)
            },
            onMorePreviewsClicked = { openMorePagePreviews(navigator, successState.manga) },
            // SY <--
            onMultiBookmarkClicked = viewModel::bookmarkChapters,
            onMultiMarkAsReadClicked = viewModel::markChaptersRead,
            onMarkPreviousAsReadClicked = viewModel::markPreviousChapterRead,
            onMultiDeleteClicked = viewModel::showDeleteChapterDialog,
            onChapterSwipe = viewModel::chapterSwipe,
            onChapterSelected = viewModel::toggleSelection,
            onAllChapterSelected = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = { viewModel.dismissDialog() }
        when (val dialog = successState.dialog) {
            null -> {}
            is MangaViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaViewModel.Dialog.DeleteChapters -> {
                DeleteChaptersDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        viewModel.toggleAllSelection(false)
                        viewModel.deleteChapters(dialog.chapters)
                    },
                )
            }

            is MangaViewModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { viewModel.showMigrateDialog(it) },
                )
            }

            is MangaViewModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaViewModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                onDownloadFilterChanged = viewModel::setDownloadedFilter,
                onUnreadFilterChanged = viewModel::setUnreadFilter,
                onBookmarkedFilterChanged = viewModel::setBookmarkedFilter,
                onSortModeChanged = viewModel::setSorting,
                onDisplayModeChanged = viewModel::setDisplayMode,
                onSetAsDefault = viewModel::setCurrentSettingsAsDefault,
                onResetToDefault = viewModel::resetToDefaultSettings,
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )
            MangaViewModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaViewModel.Dialog.FullCover -> {
                val vm =
                    assistedMetroViewModel<MangaCoverViewModel, MangaCoverViewModel.Factory> {
                        create(mangaId = mangaId)
                    }
                val manga by vm.state.collectAsStateWithLifecycle()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                        if (it == null) return@rememberLauncherForActivityResult
                        vm.editCover(context, it)
                    }
                    MangaCoverDialog(
                        manga = manga!!,
                        snackbarHostState = vm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover() },
                        onShareClick = { vm.shareCover(context) },
                        onSaveClick = { vm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> vm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is MangaViewModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> viewModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { viewModel.isUpdateIntervalEnabled },
                )
            }
            // SY -->
            is MangaViewModel.Dialog.EditMangaInfo -> {
                EditMangaDialog(
                    manga = dialog.manga,
                    onDismissRequest = viewModel::dismissDialog,
                    onPositiveClick = viewModel::updateMangaInfo,
                )
            }
            is MangaViewModel.Dialog.EditMergedSettings -> {
                EditMergedSettingsDialog(
                    mergedData = dialog.mergedData,
                    onDismissRequest = viewModel::dismissDialog,
                    onDeleteClick = viewModel::deleteMerge,
                    onPositiveClick = viewModel::updateMergeSettings,
                )
            }
            // SY <--
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = viewModel::setExcludedScanlators,
            )
        }
    }

    private fun continueReading(context: Context, unreadChapter: Chapter?) {
        if (unreadChapter != null) openChapter(context, unreadChapter)
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }

    private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: Source?) {
        getMangaUrl(manga_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareManga(context: Context, manga_: Manga?, source_: Source?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                previousController.search(query)
            }
            is BrowseSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
            // SY -->
            is SourceFeedScreen -> {
                navigator.pop()
                navigator.replace(BrowseSourceScreen(previousController.sourceId, query))
            }
            // SY <--
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: Source) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseSourceScreen && source is HttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Manga URL to Clipboard
     */
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
        context.copyToClipboard(url, url)
    }

    // SY -->

    private fun openMetadataViewer(navigator: Navigator, manga: Manga) {
        navigator.push(MetadataViewScreen(manga.id, manga.source))
    }

    private fun openMergedMangaWebview(context: Context, navigator: Navigator, mergedMangaData: MergedMangaData) {
        val sourceManager = context.appGraph.sourceManager
        val mergedManga = mergedMangaData.manga.values.filterNot { it.source == MERGED_SOURCE_ID }
        val sources = mergedManga.map { sourceManager.getOrStub(it.source) }
        MaterialAlertDialogBuilder(context)
            .setTitle(MR.strings.action_open_in_web_view.getString(context))
            .setSingleChoiceItems(
                Array(mergedManga.size) { index -> sources[index].toString() },
                -1,
            ) { dialog, index ->
                dialog.dismiss()
                openMangaInWebView(navigator, mergedManga[index], sources[index] as? HttpSource)
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    private fun openMorePagePreviews(navigator: Navigator, manga: Manga) {
        navigator.push(PagePreviewScreen(manga.id))
    }

    private fun openPagePreview(context: Context, chapter: Chapter?, page: Int) {
        chapter ?: return
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id, page))
    }
    // SY <--

    // EXH -->
    private fun openSmartSearch(navigator: Navigator, manga: Manga) {
        val smartSearchConfig = SourcesScreen.SmartSearchConfig(manga.title, manga.id)

        navigator.push(SourcesScreen(smartSearchConfig))
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun mergeWithAnother(
        navigator: Navigator,
        context: Context,
        manga: Manga,
        smartSearchMerge: suspend (Manga, Long) -> Manga,
    ) {
        launchUI {
            try {
                val mergedManga = withNonCancellableContext {
                    smartSearchMerge(manga, smartSearchConfig?.origMangaId!!)
                }

                navigator.popUntil { it is SourcesScreen }
                navigator.pop()
                navigator replace MangaScreen(mergedManga.id, true)
                context.toast(SYMR.strings.entry_merged)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                context.toast(context.stringResource(SYMR.strings.failed_merge, e.message.orEmpty()))
            }
        }
    }
    // EXH <--

    // AZ -->
    private fun openRecommends(navigator: Navigator, source: Source?, manga: Manga) {
        source ?: return
        RecommendsScreen.Args.SingleSourceManga(manga.id, source.id)
            .let(::RecommendsScreen)
            .let(navigator::push)
    }
    // AZ <--
}
