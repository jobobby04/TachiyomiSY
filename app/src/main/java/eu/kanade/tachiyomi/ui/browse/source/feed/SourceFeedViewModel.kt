package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.source.getMainSource
import exh.source.mangaDexSourceIds
import exh.util.nullIfBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.core.metro.AppCoroutineScope
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.SubscriberGate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.State as ComposeState
import tachiyomi.domain.manga.model.Manga as DomainManga

@AssistedInject
open class SourceFeedViewModel(
    @Assisted val sourceId: Long,
    uiPreferences: UiPreferences,
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateManga: UpdateManga,
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId,
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed,
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId,
    private val insertFeedSavedSearch: InsertFeedSavedSearch,
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById,
    private val getExhSavedSearch: GetExhSavedSearch,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val subscriberGate = SubscriberGate()

    private val mutableState = MutableStateFlow(State())

    // Keeps the source feed while it's hidden, but holds back database updates until it's shown again
    val state: StateFlow<State> = mutableState
        .stateIn(viewModelScope + subscriberGate, subscriberGate.whileSubscribed(5.seconds), State())

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): SourceFeedViewModel
    }

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    val startExpanded by uiPreferences.expandFilters.asState(viewModelScope)

    init {
        addCloseable(coroutineDispatcher)

        viewModelScope.launch(Dispatchers.IO) {
            val source = sourceManager.getOrStub(sourceId)

            setFilters(source.getFilterList())

            val searches = loadSearches(source)
            mutableState.update {
                it.copy(
                    source = source,
                    savedSearches = searches,
                )
            }

            getFeedSavedSearchBySourceId.subscribe(sourceId)
                .onEach {
                    val items = getSourcesToGetFeed(source, it)
                    mutableState.update { state ->
                        state.copy(
                            items = items,
                        )
                    }
                    getFeed(source, items)
                }
                .launchIn(viewModelScope + subscriberGate)
        }

        viewModelScope.launch(Dispatchers.IO) {
        }
    }

    fun setFilters(filters: FilterList) {
        mutableState.update { it.copy(filters = filters) }
    }

    private suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchBySourceId.await(sourceId) > 10
    }

    fun createFeed(savedSearchId: Long) {
        appScope.launch {
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = sourceId,
                    savedSearch = savedSearchId,
                    global = false,
                ),
            )
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        appScope.launch {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    private suspend fun getSourcesToGetFeed(source: Source, feedSavedSearch: List<FeedSavedSearch>): List<SourceFeedUI> {
        val savedSearches = getSavedSearchBySourceIdFeed.await(source.id)
            .associateBy { it.id }

        return (
            listOfNotNull(
                if (source.supportsLatest) {
                    SourceFeedUI.Latest(null)
                } else {
                    null
                },
                SourceFeedUI.Browse(null),
            ) + feedSavedSearch
                .map { SourceFeedUI.SourceSavedSearch(it, savedSearches[it.savedSearch]!!, null) }
            )
    }

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(source: Source, feedSavedSearch: List<SourceFeedUI>) {
        viewModelScope.launch {
            feedSavedSearch.map { sourceFeed ->
                async {
                    val page = try {
                        withContext(coroutineDispatcher) {
                            when (sourceFeed) {
                                is SourceFeedUI.Browse -> source.getPopularManga(1)
                                is SourceFeedUI.Latest -> source.getLatestUpdates(1)
                                is SourceFeedUI.SourceSavedSearch -> source.getSearchManga(
                                    page = 1,
                                    query = sourceFeed.savedSearch.query.orEmpty(),
                                    filters = getFilterList(sourceFeed.savedSearch, source),
                                )
                            }
                        }.mangas
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val titles = withContext(Dispatchers.IO) {
                        networkToLocalManga(page.map { it.toDomainManga(source.id) })
                    }

                    mutableState.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == sourceFeed.id) sourceFeed.withResults(titles) else item
                            },
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private val filterSerializer = FilterSerializer()

    private fun getFilterList(savedSearch: SavedSearch, source: Source): FilterList {
        val filters = savedSearch.filtersJson ?: return FilterList()
        return runCatching {
            val originalFilters = source.getFilterList()
            filterSerializer.deserialize(
                filters = originalFilters,
                json = Json.decodeFromString(filters),
            )
            originalFilters
        }.getOrElse { FilterList() }
    }

    @Composable
    fun getManga(initialManga: DomainManga): ComposeState<DomainManga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    if (manga == null) return@collectLatest
                    value = manga
                }
        }
    }
    private suspend fun loadSearches(source: Source) =
        getExhSavedSearch.await(sourceId, source::getFilterList)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name))

    fun onFilter(onBrowseClick: (query: String?, filters: String?) -> Unit) {
        val source = mutableState.value.source ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val allDefault = mutableState.value.filters == source.getFilterList()
            dismissDialog()
            if (allDefault) {
                onBrowseClick(
                    mutableState.value.searchQuery?.nullIfBlank(),
                    null,
                )
            } else {
                onBrowseClick(
                    mutableState.value.searchQuery?.nullIfBlank(),
                    Json.encodeToString(filterSerializer.serialize(mutableState.value.filters)),
                )
            }
        }
    }

    fun onSavedSearch(
        search: EXHSavedSearch,
        onBrowseClick: (query: String?, searchId: Long) -> Unit,
        onToast: (StringResource) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = mutableState.value.source ?: return@launch
            if (search.filterList == null && mutableState.value.filters.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    onToast(SYMR.strings.save_search_invalid)
                }
                return@launch
            }

            val allDefault = search.filterList != null && search.filterList == source.getFilterList()
            dismissDialog()

            if (!allDefault) {
                onBrowseClick(
                    mutableState.value.searchQuery?.nullIfBlank(),
                    search.id,
                )
            }
        }
    }

    fun onSavedSearchAddToFeed(
        search: EXHSavedSearch,
        onToast: (StringResource) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (hasTooManyFeeds()) {
                withContext(Dispatchers.Main) {
                    onToast(SYMR.strings.too_many_in_feed)
                }
                return@launch
            }
            openAddFeed(search.id, search.name)
        }
    }

    fun onMangaDexRandom(onRandomFound: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = mutableState.value.source ?: return@launch
            val random = source.getMainSource<MangaDex>()?.fetchRandomMangaUrl()
                ?: return@launch
            onRandomFound(random)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openFilterSheet() {
        mutableState.update { it.copy(dialog = Dialog.Filter) }
    }

    fun openDeleteFeed(feed: FeedSavedSearch) {
        mutableState.update { it.copy(dialog = Dialog.DeleteFeed(feed)) }
    }

    fun openAddFeed(feedId: Long, name: String) {
        mutableState.update { it.copy(dialog = Dialog.AddFeed(feedId, name)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data object Filter : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()
        data class AddFeed(val feedId: Long, val name: String) : Dialog()
    }

    @Immutable
    data class State(
        val source: Source? = null,
        val searchQuery: String? = null,
        val items: List<SourceFeedUI> = emptyList(),
        val filters: FilterList = FilterList(),
        val savedSearches: List<EXHSavedSearch> = emptyList(),
        val dialog: Dialog? = null,
    ) {
        val isLoading
            get() = items.isEmpty()

        val sourceIsMangaDex
            get() = source != null && source.id in mangaDexSourceIds
    }
}
