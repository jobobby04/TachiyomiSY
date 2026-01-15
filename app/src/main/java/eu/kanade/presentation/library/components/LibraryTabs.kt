package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Renders a horizontal, scrollable tab row for library categories with optional lock indicators and per-category badge counts.
 *
 * @param categories The list of categories to display as tabs.
 * @param pagerState Pager state used to determine the currently selected tab.
 * @param getItemCountForCategory Function that returns the badge count for a given category, or `null` if none.
 * @param onTabItemClick Callback invoked with the tab index when a tab is clicked.
 * @param isCategoryLocked Function that returns `true` for categories that should display a lock icon; defaults to always `false`.
 */
@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getItemCountForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
    // SY -->
    isCategoryLocked: (Category) -> Boolean = { false },
    // SY <--
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    Column(modifier = Modifier.zIndex(2f)) {
        PrimaryScrollableTabRow(
            selectedTabIndex = currentPageIndex,
            edgePadding = 0.dp,
            // TODO: use default when width is fixed upstream
            // https://issuetracker.google.com/issues/242879624
            divider = {},
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = currentPageIndex == index,
                    onClick = { onTabItemClick(index) },
                    text = {
                        // SY -->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCategoryLocked(category)) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = stringResource(SYMR.strings.category_locked_icon),
                                    modifier = Modifier.width(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            TabText(
                                text = category.visualName,
                                badgeCount = getItemCountForCategory(category),
                            )
                        }
                        // SY <--
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider()
    }
}
