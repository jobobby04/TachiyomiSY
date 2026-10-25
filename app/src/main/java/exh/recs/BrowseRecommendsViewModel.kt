package exh.recs

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel
import exh.metadata.metadata.RaisedSearchMetadata
import exh.pref.DelegateSourcePreferences
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.StaticResultPagingSource
import exh.source.ExhPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks

/**
 * SY-specific file
 */
@AssistedInject
class BrowseRecommendsViewModel(
    @Assisted private val args: BrowseRecommendsScreen.Args,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val networkHelper: NetworkHelper,
    private val json: Json,
    private val delegateSourcePreferences: DelegateSourcePreferences,
    sourceManager: SourceManager,
    sourcePreferences: SourcePreferences,
    libraryPreferences: LibraryPreferences,
    coverCache: CoverCache,
    getRemoteManga: GetRemoteManga,
    getDuplicateLibraryManga: GetDuplicateLibraryManga,
    getCategories: GetCategories,
    setMangaCategories: SetMangaCategories,
    setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    updateManga: UpdateManga,
    addTracks: AddTracks,
    getIncognitoState: GetIncognitoState,
    exhPreferences: ExhPreferences,
    uiPreferences: UiPreferences,
    getFlatMetadataById: GetFlatMetadataById,
    deleteSavedSearchById: DeleteSavedSearchById,
    insertSavedSearch: InsertSavedSearch,
    getExhSavedSearch: GetExhSavedSearch,
    @AppCoroutineScope appScope: CoroutineScope,
) : BrowseSourceViewModel(
    sourceId = when (args) {
        is BrowseRecommendsScreen.Args.SingleSourceManga -> args.sourceId
        is BrowseRecommendsScreen.Args.MergedSourceMangas -> args.results.recAssociatedSourceId ?: -1
    },
    listingQuery = null,
    sourceManager = sourceManager,
    sourcePreferences = sourcePreferences,
    libraryPreferences = libraryPreferences,
    coverCache = coverCache,
    getRemoteManga = getRemoteManga,
    getDuplicateLibraryManga = getDuplicateLibraryManga,
    getCategories = getCategories,
    setMangaCategories = setMangaCategories,
    setMangaDefaultChapterFlags = setMangaDefaultChapterFlags,
    getManga = getManga,
    updateManga = updateManga,
    addTracks = addTracks,
    getIncognitoState = getIncognitoState,
    exhPreferences = exhPreferences,
    uiPreferences = uiPreferences,
    appScope = appScope,
    getFlatMetadataById = getFlatMetadataById,
    deleteSavedSearchById = deleteSavedSearchById,
    insertSavedSearch = insertSavedSearch,
    getExhSavedSearch = getExhSavedSearch,
) {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(args: BrowseRecommendsScreen.Args): BrowseRecommendsViewModel
    }

    val recommendationState: StateFlow<RecommendationPagingSource?>
        field = MutableStateFlow(null)

    override fun createSourcePagingSource(query: String, filters: FilterList): RecommendationPagingSource {
        val value = runBlocking { recommendationState.first { it != null }!! }
        return value
    }

    override fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        // Overridden to prevent our custom metadata from being replaced from a cache
        return flatMapLatest { manga -> flowOf(manga to metadata) }
    }

    init {
        update { it.copy(filterable = false) }

        viewModelScope.launch(Dispatchers.IO) {
            recommendationState.value = when (args) {
                is BrowseRecommendsScreen.Args.MergedSourceMangas -> StaticResultPagingSource(args.results, networkToLocalManga)
                is BrowseRecommendsScreen.Args.SingleSourceManga -> RecommendationPagingSource.createSources(
                    getManga.await(args.mangaId)!!,
                    sourceManager.getOrStub(args.sourceId),
                    networkToLocalManga,
                    getTracks,
                    trackerManager,
                    networkHelper.client,
                    json,
                    delegateSourcePreferences,
                ).first {
                    it::class.qualifiedName == args.recommendationSourceName
                }
            }
        }
    }
}
