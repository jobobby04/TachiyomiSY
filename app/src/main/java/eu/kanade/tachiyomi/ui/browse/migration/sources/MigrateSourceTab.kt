package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import kotlinx.coroutines.DelicateCoroutinesApi
import mihon.app.di.appGraph
import mihon.feature.migration.config.MigrationConfigScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.migrateSourceTab(): TabContent {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<MigrateSourceViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = MR.strings.label_migration,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.migration_help_guide),
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                onClick = {
                    uriHandler.openUri("https://mihon.app/docs/guides/source-migration")
                },
            ),
        ),
        content = { contentPadding, _ ->
            MigrateSourceScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source ->
                    navigator.push(MigrateMangaScreen(source.id))
                },
                onToggleSortingDirection = viewModel::toggleSortingDirection,
                onToggleSortingMode = viewModel::toggleSortingMode,
                // SY -->
                onClickAll = { source ->
                    // TODO: Jay wtf, need to clean this up sometime
                    @OptIn(DelicateCoroutinesApi::class)
                    launchIO {
                        val manga = context.appGraph.getFavorites.await()
                        val sourceMangas =
                            manga.asSequence().filter { it.source == source.id }.map { it.id }.toList()
                        withUIContext {
                            navigator.push(
                                MigrationConfigScreen(
                                    sourceMangas,
                                ),
                            )
                        }
                    }
                },
                // SY <--
            )
        },
    )
}
