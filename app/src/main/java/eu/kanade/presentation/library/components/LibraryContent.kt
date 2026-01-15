package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

/**
 * Renders the library UI: optional category tabs, a paged category layout, selection handling, and pull-to-refresh.
 *
 * Supports navigating between category pages, displaying items per category, toggling item selection and range
 * selection, invoking a continue-reading action, and requesting or checking category locks before navigation.
 *
 * @param categories List of library categories to display in tabs and pages.
 * @param searchQuery Current search query to filter visible items.
 * @param selection Set of selected manga IDs; selection state affects click behavior and refresh availability.
 * @param contentPadding Padding to apply around the composable's content.
 * @param currentPage Index of the currently selected category page; coerced to the valid range of categories.
 * @param hasActiveFilters True when filters are active and the UI may reflect filtered state.
 * @param showPageTabs Whether to show the category tabs above the pager.
 * @param onChangeCurrentPage Called when the visible page changes with the new page index.
 * @param onClickManga Called when a manga is activated (click) while no selection is active; receives the manga id.
 * @param onContinueReadingClicked Optional callback invoked when the continue-reading action is triggered for a manga.
 * @param onToggleSelection Toggle the selection state for a single manga within a category.
 * @param onToggleRangeSelection Toggle a range selection starting/ending at the provided manga within a category.
 * @param onRefresh Invoked to start a refresh; should return `true` if a refresh was started, `false` to cancel.
 * @param onGlobalSearchClicked Callback invoked when the global search control is activated.
 * @param getItemCountForCategory Returns the number of items for a given category (may be null if unknown).
 * @param getDisplayMode Returns the display mode preference for a category index.
 * @param getColumnsForOrientation Returns the column count preference for the current orientation (isLandscape).
 * @param getItemsForCategory Returns the list of items to display for a given category.
 * @param isCategoryLocked Predicate used to check whether a category is locked; locked categories prevent navigation.
 * @param onRequestUnlock Invoked when a locked category is requested (e.g., tab click) to trigger an unlock flow.
 */
@Composable
fun LibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    // SY -->
    isCategoryLocked: (Category) -> Boolean = { false },
    onRequestUnlock: (Category) -> Unit = {},
    // SY <--
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val coercedCurrentPage = remember(categories, currentPage) { currentPage.coerceIn(0, categories.lastIndex) }
        // SY <--
        val pagerState = rememberPagerState(coercedCurrentPage) { categories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showPageTabs && categories.isNotEmpty() && (categories.size > 1 || !categories.first().isSystemCategory)) {
            LaunchedEffect(categories) {
                if (categories.size <= pagerState.currentPage) {
                    pagerState.scrollToPage(categories.size - 1)
                }
            }
            LibraryTabs(
                categories = categories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = { index ->
                    val category = categories[index]
                    // SY -->
                    if (isCategoryLocked(category)) {
                        onRequestUnlock(category)
                    } else {
                        // SY <--
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                        // SY -->
                    }
                    // SY <--
                },
                // SY -->
                isCategoryLocked = isCategoryLocked,
                // SY <--
            )
        }

        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
            onRefresh = {
                val started = onRefresh()
                if (!started) return@PullRefresh
                scope.launch {
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
        ) {
            LibraryPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                hasActiveFilters = hasActiveFilters,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getCategoryForPage = { page -> categories[page] },
                getDisplayMode = getDisplayMode,
                getColumnsForOrientation = getColumnsForOrientation,
                getItemsForCategory = getItemsForCategory,
                onClickManga = { category, manga ->
                    if (selection.isNotEmpty()) {
                        onToggleSelection(category, manga)
                    } else {
                        onClickManga(manga.manga.id)
                    }
                },
                onLongClickManga = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
                // SY -->
                isCategoryLocked = isCategoryLocked,
                onUnlockRequest = onRequestUnlock,
                // SY <--
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
