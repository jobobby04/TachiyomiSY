package exh.recs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
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

        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            RecommendsScreenModel(mangaId = mangaId, sourceId = sourceId)
        }
        val state by screenModel.state.collectAsState()

        RecommendsScreen(
            manga = screenModel.manga,
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga ->
                screenModel.getManga(manga)
            },
            onClickSource = { pagingSource ->
                // Pass class name of paging source as screens need to be serializable
                navigator.push(BrowseRecommendsScreen(mangaId, sourceId, pagingSource::class.qualifiedName!!))
            },
            onClickItem = { pagingSource, manga ->
                pagingSource.onMangaClick(navigator, manga)
            },
            onLongClickItem = { pagingSource, manga ->
                pagingSource.onMangaLongClick(navigator, manga)
            },
        )
    }
}
