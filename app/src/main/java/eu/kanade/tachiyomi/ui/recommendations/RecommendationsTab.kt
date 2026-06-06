package eu.kanade.tachiyomi.ui.recommendations

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.recs.BrowseRecommendsScreen
import exh.recs.RecommendsScreen
import exh.recs.RecommendsScreenModel
import exh.recs.batch.RankedSearchResults
import exh.recs.components.RecommendsContent
import exh.recs.sources.StaticResultPagingSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data object RecommendationsTab : Tab {

    private fun readResolve(): Any = RecommendationsTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            // Ícone Luck (Trevo de 4 folhas) com animação
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_luck_enter)
            return TabOptions(
                index = 5u,
                title = stringResource(SYMR.strings.label_para_voce),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { RecommendationsScreenModel(context) }
        val state by screenModel.state.collectAsState()

        // Memória local para evitar bolinha duplicada e forçar refresh do filtro
        var hasLoadedOnce by rememberSaveable { mutableStateOf(false) }
        var lastResults by remember { mutableStateOf<List<RankedSearchResults>?>(null) }

        val s = state
        if (s is RecommendationsState.Success) {
            hasLoadedOnce = true
            lastResults = s.results
        }

        Scaffold(
            topBar = {
                AppBar(title = stringResource(SYMR.strings.label_para_voce))
            },
        ) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                val isFirstLoad = s is RecommendationsState.Loading && !hasLoadedOnce
                val isRefreshing = s is RecommendationsState.Loading && hasLoadedOnce

                val displayResults = if (s is RecommendationsState.Success) s.results else lastResults

                PullRefresh(
                    refreshing = isRefreshing,
                    onRefresh = { screenModel.refresh() },
                    enabled = true,
                ) {
                    if (displayResults != null) {
                        // FIX: O 'tag' força a reconstrução completa do filtro de mangás favoritos
                        val recsArgs = RecommendsScreen.Args.MergedSourceMangas(displayResults)
                        val recsScreenModel = rememberScreenModel(tag = displayResults.hashCode().toString() + isRefreshing) {
                            RecommendsScreenModel(recsArgs)
                        }
                        val recsState by recsScreenModel.state.collectAsState()

                        val onClickItem = { manga: Manga ->
                            if (manga.source == -1L) {
                                navigator.push(GlobalSearchScreen(manga.ogTitle))
                            } else {
                                navigator.push(MangaScreen(manga.id, true))
                            }
                        }

                        RecommendsContent(
                            items = recsState.filteredItems,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            getManga = @Composable { manga: Manga -> recsScreenModel.getManga(manga) },
                            onClickSource = { pagingSource ->
                                navigator.push(
                                    BrowseRecommendsScreen(
                                        BrowseRecommendsScreen.Args.MergedSourceMangas(
                                            (pagingSource as StaticResultPagingSource).data,
                                        ),
                                        pagingSource.associatedSourceId == null,
                                    ),
                                )
                            },
                            onClickItem = onClickItem,
                            onLongClickItem = { manga ->
                                if (manga.source == -1L) {
                                    WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                                } else {
                                    onClickItem(manga)
                                }
                            },
                        )
                    } else {
                        // Só mostra a rodinha central se for a primeiríssima vez sem dados
                        if (isFirstLoad) {
                            LoadingScreen()
                        } else {
                            when (s) {
                                is RecommendationsState.Empty -> EmptyScreen(stringRes = SYMR.strings.rec_no_results)
                                is RecommendationsState.Error -> EmptyScreen(message = s.message)
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
