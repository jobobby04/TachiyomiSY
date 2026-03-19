package exh.ui.smartsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class SmartSearchScreen(
    private val sourceId: Long,
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SmartSearchScreenModel(sourceId, smartSearchConfig) }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val state by screenModel.state.collectAsState()
        val searchQuery = state.searchQuery

        LaunchedEffect(state.searchResults) {
            val results = state.searchResults
            if (results != null) {
                if (results is SmartSearchScreenModel.SearchResults.Found) {
                    navigator.replace(MangaScreen(results.manga.id, true, smartSearchConfig))
                } else {
                    if (results is SmartSearchScreenModel.SearchResults.NotFound) {
                        context.toast(SYMR.strings.could_not_find_entry)
                    } else {
                        context.toast(SYMR.strings.automatic_search_error)
                    }
                    navigator.replace(
                        BrowseSourceScreen(
                            sourceId = screenModel.source.id,
                            listingQuery = state.searchQuery,
                            smartSearchConfig = smartSearchConfig,
                        ),
                    )
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = screenModel.source.name,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { screenModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { screenModel.performSearch() },
                    ),
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { screenModel.updateSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(MR.strings.action_cancel),
                                    )
                                }
                            }
                            IconButton(onClick = { screenModel.performSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(MR.strings.action_search),
                                )
                            }
                        }
                    },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(SYMR.strings.searching_source),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                }
            }
        }
    }
}
