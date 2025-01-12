package exh.recs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
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
            RecommendsScreenModel(
                mangaId = mangaId,
                sourceId = sourceId
            )
        }

        val state by screenModel.state.collectAsState()

        RecommendsScreen(
            manga = screenModel.manga,
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga ->
                screenModel.getManga(manga)
            },
            onClickSource = { source ->
                // Pass index of recommendation API as screens need to be serializable
                RecommendationApi
                    .apis
                    .indexOfFirst { it::class == source.api::class }
                    .let { navigator.push(BrowseRecommendsScreen(mangaId, sourceId, it)) }
            },
            onClickItem = { manga ->
                openSmartSearch(navigator, manga.title)
            },
            onLongClickItem = { manga ->
                // Open entry on the tracker site
                WebViewActivity
                    .newIntent(context, manga.url, 0, manga.title)
                    .let(context::startActivity)
            }
        )
    }

    private fun openSmartSearch(navigator: Navigator, title: String) {
        val smartSearchConfig = SourcesScreen.SmartSearchConfig(title)
        navigator.push(SourcesScreen(smartSearchConfig))
    }
}
