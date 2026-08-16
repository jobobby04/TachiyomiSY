package exh.md.follows

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
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.ExhPreferences
import exh.source.getMainSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.source.BaseSourcePagingSource
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

@AssistedInject
class MangaDexFollowsViewModel(
    @Assisted sourceId: Long,
    private val networkToLocalManga: NetworkToLocalManga,
    sourceManager: SourceManager,
    sourcePreferences: SourcePreferences,
    libraryPreferences: LibraryPreferences,
    coverCache: CoverCache,
    getRemoteManga: GetRemoteManga,
    getDuplicateLibraryManga: GetDuplicateLibraryManga,
    getCategories: GetCategories,
    setMangaCategories: SetMangaCategories,
    setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    getManga: GetManga,
    updateManga: UpdateManga,
    addTracks: AddTracks,
    getIncognitoState: GetIncognitoState,
    exhPreferences: ExhPreferences,
    uiPreferences: UiPreferences,
    getFlatMetadataById: GetFlatMetadataById,
    deleteSavedSearchById: DeleteSavedSearchById,
    insertSavedSearch: InsertSavedSearch,
    getExhSavedSearch: GetExhSavedSearch,
) : BrowseSourceViewModel(
    sourceId = sourceId,
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
    getFlatMetadataById = getFlatMetadataById,
    deleteSavedSearchById = deleteSavedSearchById,
    insertSavedSearch = insertSavedSearch,
    getExhSavedSearch = getExhSavedSearch,
) {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): MangaDexFollowsViewModel
    }

    override fun createSourcePagingSource(query: String, filters: FilterList): BaseSourcePagingSource {
        return MangaDexFollowsPagingSource(source.getMainSource() as MangaDex, networkToLocalManga)
    }

    override fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        return map { it to metadata }
    }

    init {
        update { it.copy(filterable = false) }
    }
}
