package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.MigrationActionIcon
import eu.kanade.presentation.browse.components.MigrationItem
import eu.kanade.presentation.browse.components.MigrationItemResult
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowForward
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.CopyAll
import mihon.icons.materialsymbols.rounded.DoneAll
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun MigrationListScreen(
    items: List<MigratingManga>,
    migrationDone: Boolean,
    unfinishedCount: Int,
    getManga: suspend (MigratingManga.SearchResult.Result) -> Manga?,
    getChapterInfo: suspend (MigratingManga.SearchResult.Result) -> MigratingManga.ChapterInfo,
    getSourceName: (Manga) -> String,
    onMigrationItemClick: (Manga) -> Unit,
    openMigrationDialog: (Boolean) -> Unit,
    skipManga: (Long) -> Unit,
    searchManually: (MigratingManga) -> Unit,
    migrateNow: (Long) -> Unit,
    copyNow: (Long) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            val titleString = stringResource(SYMR.strings.migration)
            val title by produceState(initialValue = titleString, items, unfinishedCount, titleString) {
                withIOContext {
                    value = "$titleString ($unfinishedCount/${items.size})"
                }
            }
            AppBar(
                title = title,
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.copy),
                                icon = if (items.size == 1) MaterialSymbols.Rounded.ContentCopy else MaterialSymbols.Rounded.CopyAll,
                                onClick = { openMigrationDialog(false) },
                                enabled = migrationDone,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.migrate),
                                icon = if (items.size == 1) MaterialSymbols.Rounded.Check else MaterialSymbols.Rounded.DoneAll,
                                onClick = { openMigrationDialog(false) },
                                enabled = migrationDone,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        ScrollbarLazyColumn(
            contentPadding = contentPadding + topSmallPaddingValues,
        ) {
            items(items, key = { it.manga.id }) { migrationItem ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(horizontal = 16.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val result by migrationItem.searchResult.collectAsState()
                    MigrationItem(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        manga = migrationItem.manga,
                        sourcesString = migrationItem.sourcesString,
                        chapterInfo = migrationItem.chapterInfo,
                        onClick = { onMigrationItemClick(migrationItem.manga) },
                    )

                    Icon(
                        MaterialSymbols.AutoMirroredRounded.ArrowForward,
                        contentDescription = stringResource(SYMR.strings.migrating_to),
                        modifier = Modifier.weight(0.2f),
                    )

                    MigrationItemResult(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        migrationItem = migrationItem,
                        result = result,
                        getManga = getManga,
                        getChapterInfo = getChapterInfo,
                        getSourceName = getSourceName,
                        onMigrationItemClick = onMigrationItemClick,
                    )

                    MigrationActionIcon(
                        modifier = Modifier
                            .weight(0.2f),
                        result = result,
                        skipManga = { skipManga(migrationItem.manga.id) },
                        searchManually = { searchManually(migrationItem) },
                        migrateNow = {
                            migrateNow(migrationItem.manga.id)
                        },
                        copyNow = {
                            copyNow(migrationItem.manga.id)
                        },
                    )
                }
            }
        }
    }
}
