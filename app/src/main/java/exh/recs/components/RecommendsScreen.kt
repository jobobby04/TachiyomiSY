package exh.recs.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.formattedMessage
import exh.recs.RecommendationItemResult
import exh.recs.RecommendsPagingSource
import exh.recs.RecommendsScreenModel
import kotlinx.collections.immutable.ImmutableMap
import nl.adaptivity.xmlutil.core.impl.multiplatform.name
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun RecommendsScreen(
    manga: Manga,
    state: RecommendsScreenModel.State,
    navigateUp: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (RecommendsPagingSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(SYMR.strings.similar, manga.title),
                scrollBehavior = scrollBehavior,
                navigateUp = navigateUp,
            )
        },
    ) { paddingValues ->
        RecommendsContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
internal fun RecommendsContent(
    items: ImmutableMap<RecommendsPagingSource, RecommendationItemResult>,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (RecommendsPagingSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items.forEach { (source, result) ->
            item(key = source.api::class.name) {
                GlobalSearchResultItem(
                    title = source.api.name,
                    subtitle = stringResource(source.api.category),
                    onClick = { onClickSource(source) },
                ) {
                    when (result) {
                        RecommendationItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is RecommendationItemResult.Success -> {
                            GlobalSearchCardRow(
                                titles = result.result.map {
                                    Manga.create().copy(
                                        ogTitle = it.title,
                                        url = it.url,
                                        ogThumbnailUrl = it.thumbnail_url,
                                    )
                                },
                                getManga = getManga,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }
                        is RecommendationItemResult.Error -> {
                            GlobalSearchErrorResultItem(
                                message = with(LocalContext.current) {
                                    result.throwable.formattedMessage
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
