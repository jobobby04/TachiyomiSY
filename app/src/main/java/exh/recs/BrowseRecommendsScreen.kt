package exh.recs

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.ui.ifSourcesLoaded
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class BrowseRecommendsScreen(
    private val mangaId: Long,
    private val sourceId: Long,
    private val apiIndex: Int,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { BrowseRecommendsScreenModel(mangaId, sourceId, apiIndex) }
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                BrowseSourceSimpleToolbar(
                    navigateUp = navigator::pop,
                    title = "${screenModel.api.name} (${stringResource(screenModel.api.category)})",
                    displayMode = screenModel.displayMode,
                    onDisplayModeChange = { screenModel.displayMode = it },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = false,
                // SY <--
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = null,
                onHelpClick = null,
                onLocalSourceHelpClick = null,
                onMangaClick = { manga ->
                    openSmartSearch(navigator, manga.ogTitle)
                },
                onMangaLongClick = { manga ->
                    // Open entry on the tracker site
                    WebViewActivity
                        .newIntent(context, manga.url, 0, manga.title)
                        .let(context::startActivity)
                },
            )
        }
    }

    private fun openSmartSearch(navigator: Navigator, title: String) {
        val smartSearchConfig = SourcesScreen.SmartSearchConfig(title)
        navigator.push(SourcesScreen(smartSearchConfig))
    }
}
