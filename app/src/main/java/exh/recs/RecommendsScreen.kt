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
import exh.recs.components.RecommendsScreen
import exh.ui.ifSourcesLoaded
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.screens.LoadingScreen

class RecommendsScreen(val mangaId: Long, val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            RecommendsScreenModel(mangaId = mangaId, sourceId = sourceId)
        }
        val state by screenModel.state.collectAsState()

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (manga.source) {
                    -1L -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    else -> MangaScreen(manga.id, true)
                }
            )
        }

        val onLongClickItem = { manga: Manga ->
            when (manga.source) {
                -1L -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                else -> onClickItem(manga)
            }
        }

        RecommendsScreen(
            manga = screenModel.manga,
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga -> screenModel.getManga(manga) },
            onClickSource = { pagingSource ->
                // Pass class name of paging source as screens need to be serializable
                navigator.push(BrowseRecommendsScreen(
                    mangaId,
                    sourceId,
                    pagingSource::class.qualifiedName!!,
                    pagingSource.associatedSourceId == null
                ))
            },
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}
