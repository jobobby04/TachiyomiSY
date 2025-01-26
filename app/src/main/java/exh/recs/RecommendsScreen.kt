package exh.recs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.recs.RecommendsScreen.Args.MergedSourceMangas
import exh.recs.RecommendsScreen.Args.SingleSourceManga
import exh.recs.batch.RankedSearchResults
import exh.recs.components.RecommendsScreen
import exh.recs.sources.StaticResultPagingSource
import exh.ui.ifSourcesLoaded
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.io.Serializable

class RecommendsScreen(private val args: Args) : Screen() {

    sealed interface Args : Serializable {
        data class SingleSourceManga(val mangaId: Long, val sourceId: Long) : Args
        data class MergedSourceMangas(val mergedResults: List<RankedSearchResults>) : Args
    }

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { RecommendsScreenModel(args) }
        val state by screenModel.state.collectAsState()

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (manga.source) {
                    -1L -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    else -> MangaScreen(manga.id, true)
                },
            )
        }

        val onLongClickItem = { manga: Manga ->
            when (manga.source) {
                -1L -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                else -> onClickItem(manga)
            }
        }

        RecommendsScreen(
            title = if (args is SingleSourceManga) {
                stringResource(SYMR.strings.similar, state.title.orEmpty())
            } else {
                stringResource(SYMR.strings.rec_common_recommendations)
            },
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga -> screenModel.getManga(manga) },
            onClickSource = { pagingSource ->
                // Pass class name of paging source as screens need to be serializable
                navigator.push(
                    BrowseRecommendsScreen(
                        when (args) {
                            is SingleSourceManga ->
                                BrowseRecommendsScreen.Args.SingleSourceManga(
                                    args.mangaId,
                                    args.sourceId,
                                    pagingSource::class.qualifiedName!!,
                                )
                            is MergedSourceMangas ->
                                BrowseRecommendsScreen.Args.MergedSourceMangas(
                                    (pagingSource as StaticResultPagingSource).data,
                                )
                        },
                        pagingSource.associatedSourceId == null,
                    ),
                )
            },
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}
