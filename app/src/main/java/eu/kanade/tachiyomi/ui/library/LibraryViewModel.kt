package eu.kanade.tachiyomi.ui.library

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import exh.favorites.FavoritesSyncHelper
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.recs.batch.RecommendationSearchHelper
import exh.search.Namespace
import exh.search.QueryComponent
import exh.search.SearchEngine
import exh.search.Text
import exh.source.EH_SOURCE_ID
import exh.source.ExhPreferences
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import exh.source.isMetadataSource
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds
import exh.util.cancellable
import exh.util.isLewd
import exh.util.nullIfBlank
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import mihon.core.common.utils.mutate
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetSearchTags
import tachiyomi.domain.manga.interactor.GetSearchTitles
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibraryViewModel(
    private val getLibraryManga: GetLibraryManga,
    private val getCategories: GetCategories,
    private val getTracksPerManga: GetTracksPerManga,
    private val getNextChapters: GetNextChapters,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getBookmarkedChaptersByMangaId: GetBookmarkedChaptersByMangaId,
    private val setReadStatus: SetReadStatus,
    private val updateManga: UpdateManga,
    private val setMangaCategories: SetMangaCategories,
    private val preferences: BasePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val coverCache: CoverCache,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val trackerManager: TrackerManager,

    // SY -->
    private val exhPreferences: ExhPreferences,
    private val sourcePreferences: SourcePreferences,
    private val getMergedMangaById: GetMergedMangaById,
    private val getTracks: GetTracks,
    private val getIdsOfFavoriteMangaWithMetadata: GetIdsOfFavoriteMangaWithMetadata,
    private val getSearchTags: GetSearchTags,
    private val getSearchTitles: GetSearchTitles,
    private val searchEngine: SearchEngine,
    private val setCustomMangaInfo: SetCustomMangaInfo,
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,

    syncPreferences: SyncPreferences,
    val favoritesSync: FavoritesSyncHelper,
    val recommendationSearch: RecommendationSearchHelper,
    private val context: Context,
    // SY <--
) : ViewModel() {

    private val searchQuery = MutableStateFlow<String?>(null)

    private val selection = MutableStateFlow(emptySet</* Manga */ Long>())

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val activeCategoryIndex = MutableStateFlow(libraryPreferences.lastUsedCategory.get())

    private val displayPreferences = combine(
        libraryPreferences.categoryTabs.changes(),
        libraryPreferences.categoryNumberOfItems.changes(),
        libraryPreferences.showContinueReadingButton.changes(),
        ::DisplayPreferences,
    )

    private val hasActiveFilters = combine(
        getLibraryItemPreferencesFlow(),
        getTrackingFiltersFlow(),
    ) { prefs, trackFilters ->
        listOf(
            prefs.filterDownloaded,
            prefs.filterUnread,
            prefs.filterStarted,
            prefs.filterBookmarked,
            prefs.filterCompleted,
            prefs.filterIntervalCustom,
            // SY -->
            prefs.filterLewd,
            // SY <--
            *trackFilters.values.toTypedArray(),
        )
            .any { it != TriState.DISABLED }
    }
        .distinctUntilChanged()

    // SY -->
    val libraryPrefs = combine(
        libraryPreferences.groupLibraryBy.changes(),
        libraryPreferences.sortingMode.changes(),
        ::Pair,
    )
    // SY <--

    // Shared separately so search, selection and dialog changes still reach [state] before the
    // first query result, and so returning to the tab doesn't flash empty while it restarts.
    private val library = combine(
        searchQuery.debounce(0.25.seconds),
        getCategories.subscribe(),
        getFavoritesFlow(),
        combine(getTracksPerManga.subscribe(), getTrackingFiltersFlow(), ::Pair),
        // SY -->
        combine(getLibraryItemPreferencesFlow(), libraryPrefs, ::Pair),

    ) { searchQuery, categories, favorites, (tracksMap, trackingFilters), (itemPreferences, libraryPrefs) ->
        val (groupType, sortingMode) = libraryPrefs
        // SY <--
        val showSystemCategory = favorites.any { it.libraryManga.categories.contains(0) }
        val filteredFavorites = favorites
            .applyFilters(tracksMap, trackingFilters, itemPreferences)
            .let { libraryItems ->
                if (searchQuery.isNullOrEmpty()) {
                    libraryItems
                } else {
                    // SY -->
                    // val queryNode = QueryNode.from(searchQuery)
                    // libraryItems.filter { queryNode.matches(it) }
                    filterLibrary(libraryItems, searchQuery, trackingFilters)
                    // SY <--
                }
            }

        LibraryData(
            isInitialized = true,
            showSystemCategory = showSystemCategory,
            categories = categories,
            favorites = filteredFavorites,
            tracksMap = tracksMap,
            loggedInTrackerIds = trackingFilters.keys,
            // SY -->
            groupType = groupType,
            sortingMode = sortingMode,
            // SY <--
        )
    }
        .distinctUntilChanged()
        .map { data ->
            Library(
                data = data,
                // SY -->
                groupedFavorites = data.favorites
                    .applyGrouping(
                        data.categories,
                        data.showSystemCategory,
                        // SY -->
                        data.groupType,
                        // SY <--
                    )
                    .applySort(
                        data.favoritesById,
                        data.tracksMap,
                        data.loggedInTrackerIds,
                        // SY -->
                        data.sortingMode.takeIf { data.groupType != LibraryGroup.BY_DEFAULT },
                        // SY <--
                    ),
                // SY <--
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    // SY -->
    private var recommendationSearchJob: Job? = null

    val isHentaiEnabled = combine(
        exhPreferences.isHentaiEnabled.changes(),
        sourcePreferences.disabledSources.changes(),
        exhPreferences.enableExhentai.changes(),
    ) { isHentaiEnabled, disabledSources, enableExhentai ->
        isHentaiEnabled && (EH_SOURCE_ID.toString() !in disabledSources || enableExhentai)
    }.distinctUntilChanged()
    val syncService = syncPreferences.syncService.changes()
        .map { it != 0 }
        .distinctUntilChanged()
    // SY <--

    val state: StateFlow<State> = combine(
        library,
        combine(searchQuery, selection, dialog, ::Triple),
        combine(activeCategoryIndex, displayPreferences, hasActiveFilters, ::Triple),
        // SY -->
        combine(isHentaiEnabled, syncService, ::Pair),
        // SY <--
    ) { library, (searchQuery, selection, dialog), (activeCategoryIndex, display, hasActiveFilters), (isHentaiEnabled, isSyncEnabled) ->
        State(
            isLoading = library == null,
            searchQuery = searchQuery,
            selection = selection,
            hasActiveFilters = hasActiveFilters,
            showCategoryTabs = display.showCategoryTabs,
            showMangaCount = display.showMangaCount,
            showMangaContinueButton = display.showMangaContinueButton,
            dialog = dialog,
            libraryData = library?.data ?: LibraryData(),
            activeCategoryIndex = activeCategoryIndex,
            groupedFavorites = library?.groupedFavorites.orEmpty(),
            // SY -->
            groupType = library?.data?.groupType ?: LibraryGroup.BY_DEFAULT,
            showSyncExh = isHentaiEnabled,
            isSyncEnabled = isSyncEnabled,
            // SY <--
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private data class DisplayPreferences(
        val showCategoryTabs: Boolean,
        val showMangaCount: Boolean,
        val showMangaContinueButton: Boolean,
    )

    private data class Library(
        val data: LibraryData,
        val groupedFavorites: Map<Category, List</* LibraryItem */ Long>>,
    )

    private suspend fun List<LibraryItem>.applyFilters(
        trackMap: Map<Long, List<Track>>,
        trackingFilter: Map<Long, TriState>,
        preferences: ItemPreferences,
    ): List<LibraryItem> {
        val downloadedOnly = preferences.globalFilterDownloaded
        val skipOutsideReleasePeriod = preferences.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterDownloaded
        val filterUnread = preferences.filterUnread
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterCompleted = preferences.filterCompleted
        val filterIntervalCustom = preferences.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        // SY -->
        val filterLewd = preferences.filterLewd
        // SY <--

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) { it.isLocal || it.downloadCount > 0 }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryManga.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
            } else {
                true
            }
        }

        // SY -->
        val filterFnLewd: suspend (LibraryItem) -> Boolean = {
            applyFilter(filterLewd) { it.libraryManga.manga.isLewd() }
        }
        // SY <--

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap[item.id].orEmpty().map { it.trackerId }

            val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        return fastFilter {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it) &&
                // SY -->
                filterFnLewd(it)
            // SY <--
        }
    }

    private suspend fun List<LibraryItem>.applyGrouping(
        categories: List<Category>,
        showSystemCategory: Boolean,
        // SY -->
        groupType: Int,
        // <-- SY
    ): Map<Category, List</* LibraryItem */ Long>> {
        // SY -->
        when (groupType) {
            LibraryGroup.BY_DEFAULT -> {
                // SY <--
                val groupCache = mutableMapOf</* Category.id */ Long, MutableList</* LibraryItem */ Long>>()
                forEach { item ->
                    item.libraryManga.categories.forEach { categoryId ->
                        groupCache.getOrPut(categoryId) { mutableListOf() }.add(item.id)
                    }
                }

                return categories.filter { showSystemCategory || !it.isSystemCategory }
                    .associateWith { groupCache[it.id]?.toList().orEmpty() }
            }
            // SY -->
            LibraryGroup.UNGROUPED -> {
                return mapOf(
                    Category(
                        0,
                        preferences.context.stringResource(SYMR.strings.ungrouped),
                        0,
                        0,
                    ) to
                        map { it.id },
                )
            }

            else -> {
                return getGroupedMangaItems(
                    groupType = groupType,
                )
            }
        }
        // SY <--
    }

    private fun Map<Category, List</* LibraryItem */ Long>>.applySort(
        favoritesById: Map<Long, LibraryItem>,
        trackMap: Map<Long, List<Track>>,
        loggedInTrackerIds: Set<Long>,
        // SY -->
        groupSort: LibrarySort? = null,
        // SY <--
    ): Map<Category, List</* LibraryItem */ Long>> {
        // SY -->
        val listOfTags by lazy {
            libraryPreferences.sortTagsForLibrary.get()
                .asSequence()
                .mapNotNull {
                    val list = it.split("|")
                    (list.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) to
                        (list.getOrNull(1) ?: return@mapNotNull null)
                }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
        }
        // SY <--

        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { manga1, manga2 ->
            val title1 = manga1.libraryManga.manga.title.lowercase()
            val title2 = manga2.libraryManga.manga.title.lowercase()
            title1.compareToWithCollator(title2)
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { manga1, manga2 ->
            // SY -->
            val sort = groupSort ?: this
            // SY <--
            when (sort.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(manga1, manga2)
                }

                LibrarySort.Type.LastRead -> {
                    manga1.libraryManga.lastRead.compareTo(manga2.libraryManga.lastRead)
                }

                LibrarySort.Type.LastUpdate -> {
                    manga1.libraryManga.manga.lastUpdate.compareTo(manga2.libraryManga.manga.lastUpdate)
                }

                LibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    manga1.libraryManga.unreadCount == manga2.libraryManga.unreadCount -> 0
                    manga1.libraryManga.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                    manga2.libraryManga.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                    else -> manga1.libraryManga.unreadCount.compareTo(manga2.libraryManga.unreadCount)
                }

                LibrarySort.Type.TotalChapters -> {
                    manga1.libraryManga.totalChapters.compareTo(manga2.libraryManga.totalChapters)
                }

                LibrarySort.Type.LatestChapter -> {
                    manga1.libraryManga.latestUpload.compareTo(manga2.libraryManga.latestUpload)
                }

                LibrarySort.Type.ChapterFetchDate -> {
                    manga1.libraryManga.chapterFetchedAt.compareTo(manga2.libraryManga.chapterFetchedAt)
                }

                LibrarySort.Type.DateAdded -> {
                    manga1.libraryManga.manga.dateAdded.compareTo(manga2.libraryManga.manga.dateAdded)
                }

                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[manga1.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[manga2.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }

                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
                // SY -->
                LibrarySort.Type.TagList -> {
                    val manga1IndexOfTag = listOfTags.indexOfFirst {
                        manga1.libraryManga.manga.genre?.contains(it) ?: false
                    }
                    val manga2IndexOfTag = listOfTags.indexOfFirst {
                        manga2.libraryManga.manga.genre?.contains(it) ?: false
                    }
                    manga1IndexOfTag.compareTo(manga2IndexOfTag)
                }
                // SY <--
            }
        }

        return mapValues { (key, value) ->
            // SY -->
            val sort = groupSort ?: key.sort
            if (sort.type == LibrarySort.Type.Random) {
                // SY <--
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed.get()))
            }

            val manga = value.mapNotNull { favoritesById[it] }

            // SY -->
            val comparator = sort.comparator()
                // SY <--
                .let { if (/* SY --> */ sort.isAscending /* SY <-- */) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            manga.sortedWith(comparator).map { it.id }
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge.changes(),
            libraryPreferences.unreadBadge.changes(),
            libraryPreferences.localBadge.changes(),
            libraryPreferences.languageBadge.changes(),
            libraryPreferences.autoUpdateMangaRestrictions.changes(),

            preferences.downloadedOnly.changes(),
            libraryPreferences.filterDownloaded.changes(),
            libraryPreferences.filterUnread.changes(),
            libraryPreferences.filterStarted.changes(),
            libraryPreferences.filterBookmarked.changes(),
            libraryPreferences.filterCompleted.changes(),
            libraryPreferences.filterIntervalCustom.changes(),
            // SY -->
            libraryPreferences.filterLewd.changes(),
            // SY <--
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unreadBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                globalFilterDownloaded = it[5] as Boolean,
                filterDownloaded = it[6] as TriState,
                filterUnread = it[7] as TriState,
                filterStarted = it[8] as TriState,
                filterBookmarked = it[9] as TriState,
                filterCompleted = it[10] as TriState,
                filterIntervalCustom = it[11] as TriState,
                // SY -->
                filterLewd = it[12] as TriState,
                // SY <--
            )
        }
    }

    private fun getFavoritesFlow(): Flow<List<LibraryItem>> {
        return combine(
            getLibraryManga.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryManga, preferences, _ ->
            libraryManga.map { manga ->
                // SY -->
                val downloadCount = if (manga.manga.source == MERGED_SOURCE_ID) {
                    getMergedMangaById.await(manga.manga.id).sumOf { downloadManager.getDownloadCount(it) }
                } else {
                    downloadManager.getDownloadCount(manga.manga)
                }
                // SY <--
                LibraryItem(
                    libraryManga = manga,
                    // SY -->
                    downloadCount = downloadCount,
                    // SY <--
                    unreadCount = manga.unreadCount,
                    isLocal = manga.manga.isLocal(),
                    badges = LibraryItem.Badges(
                        downloadCount = if (preferences.downloadBadge) {
                            // SY -->
                            downloadCount
                            // SY <--
                        } else {
                            0
                        },
                        unreadCount = if (preferences.unreadBadge) {
                            manga.unreadCount
                        } else {
                            0
                        },
                        isLocal = if (preferences.localBadge) {
                            manga.manga.isLocal()
                        } else {
                            false
                        },
                        sourceLanguage = if (preferences.languageBadge) {
                            sourceManager.getOrStub(manga.manga.source).lang
                        } else {
                            ""
                        },
                    ),
                )
            }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFiltersFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val filterFlows = loggedInTrackers.map { tracker ->
                    libraryPreferences.filterTracking(tracker.id.toInt()).changes().map { tracker.id to it }
                }
                combine(filterFlows) { it.toMap() }
            }
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(manga: Manga): Chapter? {
        // SY -->
        val mergedManga = getMergedMangaById.await(manga.id).associateBy { it.id }
        return if (manga.id == MERGED_SOURCE_ID) {
            getMergedChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
        } else {
            getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
        }.getNextUnread(manga, downloadManager, mergedManga)
        // SY <--
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    /**
     * Queues the amount specified of unread chapters from the list of selected manga
     */
    fun performDownloadAction(action: DownloadAction) {
        when (action) {
            DownloadAction.NEXT_1_CHAPTER -> downloadNextChapters(1)
            DownloadAction.NEXT_5_CHAPTERS -> downloadNextChapters(5)
            DownloadAction.NEXT_10_CHAPTERS -> downloadNextChapters(10)
            DownloadAction.NEXT_25_CHAPTERS -> downloadNextChapters(25)
            DownloadAction.UNREAD_CHAPTERS -> downloadNextChapters(null)
            DownloadAction.BOOKMARKED_CHAPTERS -> downloadBookmarkedChapters()
        }
        clearSelection()
    }

    private fun downloadNextChapters(amount: Int?) {
        val mangas = selectedManga
        viewModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                // SY -->
                if (manga.source == MERGED_SOURCE_ID) {
                    val mergedMangas = getMergedMangaById.await(manga.id)
                        .associateBy { it.id }
                    getNextChapters.await(manga.id)
                        .let { if (amount != null) it.take(amount) else it }
                        .groupBy { it.mangaId }
                        .forEach ab@{ (mangaId, chapters) ->
                            val mergedManga = mergedMangas[mangaId] ?: return@ab
                            val downloadChapters = chapters.fastFilterNot { chapter ->
                                downloadManager.queueState.value.fastAny { chapter.id == it.chapter.id } ||
                                    downloadManager.isChapterDownloaded(
                                        chapter.name,
                                        chapter.scanlator,
                                        chapter.url,
                                        mergedManga.ogTitle,
                                        mergedManga.source,
                                    )
                            }

                            downloadManager.downloadChapters(mergedManga, downloadChapters)
                        }

                    return@forEach
                }
                // SY <--

                val chapters = getNextChapters.await(manga.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                chapter.url,
                                // SY -->
                                manga.ogTitle,
                                // SY <--
                                manga.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    private fun downloadBookmarkedChapters() {
        val mangas = selectedManga
        viewModelScope.launchNonCancellable {
            mangas.forEach { manga ->
                // SY -->
                if (manga.source == MERGED_SOURCE_ID) {
                    val mergedMangas = getMergedMangaById.await(manga.id)
                        .associateBy { it.id }
                    getBookmarkedChaptersByMangaId.await(manga.id)
                        .groupBy { it.mangaId }
                        .forEach ab@{ (mangaId, chapters) ->
                            val mergedManga = mergedMangas[mangaId] ?: return@ab
                            val downloadChapters = chapters.fastFilterNot { chapter ->
                                downloadManager.queueState.value.fastAny { chapter.id == it.chapter.id } ||
                                    downloadManager.isChapterDownloaded(
                                        chapter.name,
                                        chapter.scanlator,
                                        chapter.url,
                                        mergedManga.ogTitle,
                                        mergedManga.source,
                                    )
                            }

                            downloadManager.downloadChapters(mergedManga, downloadChapters)
                        }

                    return@forEach
                }
                // SY <--

                val chapters = getBookmarkedChaptersByMangaId.await(manga.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                chapter.url,
                                // SY -->
                                manga.ogTitle,
                                // SY <--
                                manga.source,
                            )
                    }
                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    // SY -->
    fun cleanTitles() {
        state.value.selectedManga.fastFilter {
            it.isEhBasedManga() ||
                it.source in nHentaiSourceIds
        }.fastForEach { manga ->
            val editedTitle =
                manga.title.replace("\\[.*?]".toRegex(), "").trim().replace("\\(.*?\\)".toRegex(), "").trim()
                    .replace("\\{.*?\\}".toRegex(), "").trim().let {
                        if (it.contains("|")) {
                            it.replace(".*\\|".toRegex(), "").trim()
                        } else {
                            it
                        }
                    }
            if (manga.title == editedTitle) return@fastForEach
            val mangaInfo = CustomMangaInfo(
                id = manga.id,
                title = editedTitle.nullIfBlank(),
                author = manga.author.takeUnless { it == manga.ogAuthor },
                artist = manga.artist.takeUnless { it == manga.ogArtist },
                thumbnailUrl = manga.thumbnailUrl.takeUnless { it == manga.ogThumbnailUrl },
                description = manga.description.takeUnless { it == manga.ogDescription },
                genre = manga.genre.takeUnless { it == manga.ogGenre },
                status = manga.status.takeUnless { it == manga.ogStatus },
            )

            setCustomMangaInfo.set(mangaInfo)
        }
        clearSelection()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun syncMangaToDex() {
        launchIO {
            MdUtil.getEnabledMangaDex(sourcePreferences, sourceManager)?.let { mdex ->
                state.value.selectedManga.fastFilter { it.source in mangaDexSourceIds }.fastForEach { manga ->
                    mdex.updateFollowStatus(MdUtil.getMangaId(manga.url), FollowStatus.READING)
                }
            }
            clearSelection()
        }
    }

    fun resetInfo() {
        state.value.selectedManga.fastForEach { manga ->
            val mangaInfo = CustomMangaInfo(
                id = manga.id,
                title = null,
                author = null,
                artist = null,
                thumbnailUrl = null,
                description = null,
                genre = null,
                status = null,
            )

            setCustomMangaInfo.set(mangaInfo)
        }
        clearSelection()
    }
// SY <--

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val selection = selectedManga
        viewModelScope.launchNonCancellable {
            selection.forEach { manga ->
                setReadStatus.await(
                    manga = manga,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangas the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        viewModelScope.launchNonCancellable {
            if (deleteFromLibrary) {
                val toDelete = mangas.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangas.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        if (source is MergedSource) {
                            val mergedMangas = getMergedMangaById.await(manga.id)
                            val sources = mergedMangas.distinctBy {
                                it.source
                            }.map { sourceManager.getOrStub(it.source) }
                            mergedMangas.forEach merge@{ mergedManga ->
                                val mergedSource =
                                    sources.firstOrNull { mergedManga.source == it.id } as? HttpSource ?: return@merge
                                downloadManager.deleteManga(mergedManga, mergedSource)
                            }
                        } else {
                            downloadManager.deleteManga(manga, source)
                        }
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        viewModelScope.launchNonCancellable {
            mangaList.forEach { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setMangaCategories.await(manga.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode.asState(viewModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns else libraryPreferences.portraitColumns)
            .asState(viewModelScope)
    }

    fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        val state = state.value
        return state.getItemsForCategoryId(state.activeCategory?.id).randomOrNull()
    }

    fun showSettingsDialog() {
        dialog.update { Dialog.SettingsSheet }
    }

    // SY -->
    fun showRecommendationSearchDialog() {
        val mangaList = state.value.selectedManga
        dialog.update { Dialog.RecommendationSearchSheet(mangaList) }
    }

    private suspend fun filterLibrary(
        unfiltered: List<LibraryItem>,
        query: String?,
        loggedInTrackServices: Map<Long, TriState>,
    ): List<LibraryItem> {
        return if (unfiltered.isNotEmpty() && !query.isNullOrBlank()) {
            // Prepare filter object
            val parsedQuery = searchEngine.parseQuery(query)
            val mangaWithMetaIds = getIdsOfFavoriteMangaWithMetadata.await()
            val tracks = if (loggedInTrackServices.isNotEmpty()) {
                getTracks.await().groupBy { it.mangaId }
            } else {
                emptyMap()
            }
            val sources = unfiltered
                .distinctBy { it.libraryManga.manga.source }
                .fastMapNotNull { sourceManager.get(it.libraryManga.manga.source) }
                .associateBy { it.id }
            unfiltered.asFlow().cancellable().filter { item ->
                val mangaId = item.libraryManga.manga.id
                if (query.startsWith("id:", true)) {
                    val id = query.substringAfter("id:").toLongOrNull()
                    return@filter mangaId == id
                }
                val sourceId = item.libraryManga.manga.source
                if (isMetadataSource(sourceId)) {
                    if (mangaWithMetaIds.binarySearch(mangaId) < 0) {
                        // No meta? Filter using title
                        filterManga(
                            queries = parsedQuery,
                            libraryManga = item.libraryManga,
                            tracks = tracks[mangaId],
                            source = sources[sourceId],
                            loggedInTrackServices = loggedInTrackServices,
                        )
                    } else {
                        val tags = getSearchTags.await(mangaId)
                        val titles = getSearchTitles.await(mangaId)
                        filterManga(
                            queries = parsedQuery,
                            libraryManga = item.libraryManga,
                            tracks = tracks[mangaId],
                            source = sources[sourceId],
                            checkGenre = false,
                            searchTags = tags,
                            searchTitles = titles,
                            loggedInTrackServices = loggedInTrackServices,
                        )
                    }
                } else {
                    filterManga(
                        queries = parsedQuery,
                        libraryManga = item.libraryManga,
                        tracks = tracks[mangaId],
                        source = sources[sourceId],
                        loggedInTrackServices = loggedInTrackServices,
                    )
                }
            }.toList()
        } else {
            unfiltered
        }
    }

    private fun filterManga(
        queries: List<QueryComponent>,
        libraryManga: LibraryManga,
        tracks: List<Track>?,
        source: Source?,
        checkGenre: Boolean = true,
        searchTags: List<SearchTag>? = null,
        searchTitles: List<SearchTitle>? = null,
        loggedInTrackServices: Map<Long, TriState>,
    ): Boolean {
        val manga = libraryManga.manga
        val sourceIdString = manga.source.takeUnless { it == LocalSource.ID }?.toString()
        val genre = if (checkGenre) manga.genre.orEmpty() else emptyList()
        return queries.all { queryComponent ->
            when (queryComponent.excluded) {
                false -> when (queryComponent) {
                    is Text -> {
                        val query = queryComponent.asQuery()
                        manga.title.contains(query, true) ||
                            (manga.author?.contains(query, true) == true) ||
                            (manga.artist?.contains(query, true) == true) ||
                            (manga.description?.contains(query, true) == true) ||
                            (source?.name?.contains(query, true) == true) ||
                            (sourceIdString != null && sourceIdString == query) ||
                            (
                                loggedInTrackServices.isNotEmpty() &&
                                    tracks != null &&
                                    filterTracks(query, tracks, context)
                                ) ||
                            (genre.fastAny { it.contains(query, true) }) ||
                            (searchTags?.fastAny { it.name.contains(query, true) } == true) ||
                            (searchTitles?.fastAny { it.title.contains(query, true) } == true)
                    }

                    is Namespace -> {
                        searchTags != null &&
                            searchTags.fastAny {
                                val tag = queryComponent.tag
                                (
                                    it.namespace.equals(queryComponent.namespace, true) &&
                                        tag?.run { it.name.contains(tag.asQuery(), true) } == true
                                    ) ||
                                    (tag == null && it.namespace.equals(queryComponent.namespace, true))
                            }
                    }

                    else -> true
                }

                true -> when (queryComponent) {
                    is Text -> {
                        val query = queryComponent.asQuery()
                        query.isBlank() ||
                            (
                                (!manga.title.contains(query, true)) &&
                                    (manga.author?.contains(query, true) != true) &&
                                    (manga.artist?.contains(query, true) != true) &&
                                    (manga.description?.contains(query, true) != true) &&
                                    (source?.name?.contains(query, true) != true) &&
                                    (sourceIdString != null && sourceIdString != query) &&
                                    (
                                        loggedInTrackServices.isEmpty() ||
                                            tracks == null ||
                                            !filterTracks(query, tracks, context)
                                        ) &&
                                    (!genre.fastAny { it.contains(query, true) }) &&
                                    (searchTags?.fastAny { it.name.contains(query, true) } != true) &&
                                    (searchTitles?.fastAny { it.title.contains(query, true) } != true)
                                )
                    }

                    is Namespace -> {
                        val searchedTag = queryComponent.tag?.asQuery()
                        searchTags == null ||
                            (queryComponent.namespace.isBlank() && searchedTag.isNullOrBlank()) ||
                            searchTags.fastAll { mangaTag ->
                                if (queryComponent.namespace.isBlank() && !searchedTag.isNullOrBlank()) {
                                    !mangaTag.name.contains(searchedTag, true)
                                } else if (searchedTag.isNullOrBlank()) {
                                    mangaTag.namespace == null ||
                                        !mangaTag.namespace.equals(queryComponent.namespace, true)
                                } else if (mangaTag.namespace.isNullOrBlank()) {
                                    true
                                } else {
                                    !mangaTag.name.contains(searchedTag, true) ||
                                        !mangaTag.namespace.equals(queryComponent.namespace, true)
                                }
                            }
                    }

                    else -> true
                }
            }
        }
    }

    private fun filterTracks(constraint: String, tracks: List<Track>, context: Context): Boolean {
        return tracks.fastAny { track ->
            val trackService = trackerManager.get(track.trackerId)
            if (trackService != null) {
                val status = trackService.getStatus(track.status)?.let {
                    context.stringResource(it)
                }
                val name = trackerManager.get(track.trackerId)?.name
                status?.contains(constraint, true) == true || name?.contains(constraint, true) == true
            } else {
                false
            }
        }
    }
// SY <--

    private var lastSelectionCategory: Long? = null

    /**
     * Reads from [selection] rather than [state], which is derived asynchronously and can still
     * hold the previous selection immediately after a toggle.
     */
    private val selectedManga: List<Manga>
        get() {
            val favoritesById = state.value.libraryData.favoritesById
            return selection.value.mapNotNull { favoritesById[it]?.libraryManga?.manga }
        }

    fun clearSelection() {
        lastSelectionCategory = null
        selection.update { setOf() }
    }

    fun toggleSelection(category: Category, manga: LibraryManga) {
        selection.update { selection ->
            val newSelection = selection.mutate { set ->
                if (!set.remove(manga.id)) set.add(manga.id)
            }
            lastSelectionCategory = category.id.takeIf { newSelection.isNotEmpty() }
            newSelection
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(category: Category, manga: LibraryManga) {
        val state = state.value
        selection.update { selection ->
            val newSelection = selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelectionCategory != category.id) {
                    list.add(manga.id)
                    return@mutate
                }

                val items = state.getItemsForCategoryId(category.id).fastMap { it.id }
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga.id)

                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> lastMangaIndex..curMangaIndex
                    curMangaIndex < lastMangaIndex -> curMangaIndex..lastMangaIndex
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                selectionRange.mapNotNull { items[it] }.let(list::addAll)
            }
            lastSelectionCategory = category.id
            newSelection
        }
    }

    fun selectAll() {
        lastSelectionCategory = null
        val state = state.value
        selection.update { selection ->
            selection.mutate { list ->
                state.getItemsForCategoryId(state.activeCategory?.id).map { it.id }.let(list::addAll)
            }
        }
    }

    fun invertSelection() {
        lastSelectionCategory = null
        val state = state.value
        selection.update { selection ->
            selection.mutate { list ->
                val itemIds = state.getItemsForCategoryId(state.activeCategory?.id).fastMap { it.id }
                val (toRemove, toAdd) = itemIds.partition { it in list }
                list.removeAll(toRemove.toSet())
                list.addAll(toAdd)
            }
        }
    }

    fun search(query: String?) {
        searchQuery.update { query }
    }

    fun updateActiveCategoryIndex(index: Int) {
        activeCategoryIndex.update { index }
        // Coerce here rather than reading it back off [state], which is derived asynchronously
        // and would still hold the previous index at this point.
        val lastIndex = state.value.displayedCategories.lastIndex.coerceAtLeast(0)
        libraryPreferences.lastUsedCategory.set(index.coerceIn(0, lastIndex))
    }

    fun openChangeCategoryDialog() {
        viewModelScope.launchIO {
            // Create a copy of selected manga
            val mangaList = selectedManga

            // Hide the default category because it has a different behavior than the ones from db.
            // SY -->
            val categories = state.value.libraryData.categories.filter { it.id != 0L }
            // SY <--

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(mangaList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }

            dialog.update { Dialog.ChangeCategory(mangaList, preselected) }
        }
    }

    fun openDeleteMangaDialog() {
        dialog.update { Dialog.DeleteManga(selectedManga) }
    }

    fun closeDialog() {
        dialog.update { null }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val manga: List<Manga>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog

        data class DeleteManga(val manga: List<Manga>) : Dialog

        // SY -->
        data object SyncFavoritesWarning : Dialog
        data object SyncFavoritesConfirm : Dialog
        data class RecommendationSearchSheet(val manga: List<Manga>) : Dialog
        // SY <--
    }

// SY -->

    private suspend fun List<LibraryItem>.getGroupedMangaItems(
        groupType: Int,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val context = preferences.context
        return when (groupType) {
            LibraryGroup.BY_TRACK_STATUS -> {
                val tracks = getTracks.await().groupBy { it.mangaId }
                groupBy { item ->
                    val status = tracks[item.libraryManga.manga.id]?.firstNotNullOfOrNull { track ->
                        TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)
                    } ?: TrackStatus.OTHER

                    status.int
                }.mapKeys { (id) ->
                    Category(
                        id = id.toLong(),
                        name = TrackStatus.entries
                            .find { it.int == id }
                            .let { it ?: TrackStatus.OTHER }
                            .let { context.stringResource(it.res) },
                        order = TrackStatus.entries.indexOfFirst {
                            it.int == id
                        }.takeUnless { it == -1 }?.toLong() ?: TrackStatus.OTHER.ordinal.toLong(),
                        flags = 0,
                    )
                }
            }

            LibraryGroup.BY_SOURCE -> {
                val sources: List<Long>
                groupBy { item ->
                    item.libraryManga.manga.source
                }.also {
                    sources = it.keys
                        .map {
                            sourceManager.getOrStub(it)
                        }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.id.toString() } })
                        .map { it.id }
                }.mapKeys {
                    Category(
                        id = it.key,
                        name = if (it.key == LocalSource.ID) {
                            context.stringResource(MR.strings.local_source)
                        } else {
                            val source = sourceManager.getOrStub(it.key)
                            source.name.ifBlank { source.id.toString() }
                        },
                        order = sources.indexOf(it.key).takeUnless { it == -1 }?.toLong() ?: Long.MAX_VALUE,
                        flags = 0,
                    )
                }
            }

            LibraryGroup.BY_STATUS -> {
                groupBy { item ->
                    item.libraryManga.manga.status
                }.mapKeys {
                    Category(
                        id = it.key + 1,
                        name = when (it.key) {
                            SManga.ONGOING.toLong() -> context.stringResource(MR.strings.ongoing)
                            SManga.LICENSED.toLong() -> context.stringResource(MR.strings.licensed)
                            SManga.CANCELLED.toLong() -> context.stringResource(MR.strings.cancelled)
                            SManga.ON_HIATUS.toLong() -> context.stringResource(MR.strings.on_hiatus)
                            SManga.PUBLISHING_FINISHED.toLong() -> context.stringResource(MR.strings.publishing_finished)
                            SManga.COMPLETED.toLong() -> context.stringResource(MR.strings.completed)
                            else -> context.stringResource(MR.strings.unknown)
                        },
                        order = when (it.key) {
                            SManga.ONGOING.toLong() -> 1
                            SManga.LICENSED.toLong() -> 2
                            SManga.CANCELLED.toLong() -> 3
                            SManga.ON_HIATUS.toLong() -> 4
                            SManga.PUBLISHING_FINISHED.toLong() -> 5
                            SManga.COMPLETED.toLong() -> 6
                            else -> 7
                        },
                        flags = 0,
                    )
                }
            }

            else -> emptyMap()
        }.toSortedMap(compareBy { it.order })
            .mapValues { (_, libraryItem) -> libraryItem.fastMap { it.id } }
    }

    fun runRecommendationSearch(selection: List<Manga>) {
        cancelRecommendationSearch()
        recommendationSearch.runSearch(viewModelScope, selection)?.let {
            recommendationSearchJob = it
        }
    }

    fun cancelRecommendationSearch() {
        recommendationSearchJob?.cancel()
        recommendationSearchJob = null
    }

    fun runSync() {
        favoritesSync.runSync(viewModelScope)
    }

    fun onAcceptSyncWarning() {
        exhPreferences.exhShowSyncIntro.set(false)
    }

    fun openFavoritesSyncDialog() {
        dialog.update {
            if (exhPreferences.exhShowSyncIntro.get()) {
                Dialog.SyncFavoritesWarning
            } else {
                Dialog.SyncFavoritesConfirm
            }
        }
    }
// SY <--

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
        // SY -->
        val filterLewd: TriState,
        // SY <--
    )

    @Immutable
    data class LibraryData(
        val isInitialized: Boolean = false,
        val showSystemCategory: Boolean = false,
        val categories: List<Category> = emptyList(),
        val favorites: List<LibraryItem> = emptyList(),
        val tracksMap: Map</* Manga */ Long, List<Track>> = emptyMap(),
        val loggedInTrackerIds: Set<Long> = emptySet(),
        // SY -->
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        val sortingMode: LibrarySort = LibrarySort.default,
        // SY <--
    ) {
        val favoritesById by lazy { favorites.associateBy { it.id } }
    }

    @Immutable
    data class State(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
        val searchQuery: String? = null,
        val selection: Set</* Manga */ Long> = setOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        val libraryData: LibraryData = LibraryData(),
        private val activeCategoryIndex: Int = 0,
        private val groupedFavorites: Map<Category, List</* LibraryItem */ Long>> = emptyMap(),
        // SY -->
        val showSyncExh: Boolean = false,
        val isSyncEnabled: Boolean = false,
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        // SY <--
    ) {
        val displayedCategories: List<Category> = groupedFavorites.keys.toList()

        val coercedActiveCategoryIndex = activeCategoryIndex.coerceIn(
            minimumValue = 0,
            maximumValue = displayedCategories.lastIndex.coerceAtLeast(0),
        )

        val activeCategory: Category? = displayedCategories.getOrNull(coercedActiveCategoryIndex)

        val isLibraryEmpty = libraryData.favorites.isEmpty()

        val selectionMode = selection.isNotEmpty()

        val selectedManga by lazy { selection.mapNotNull { libraryData.favoritesById[it]?.libraryManga?.manga } }

        // SY -->
        val showCleanTitles: Boolean by lazy {
            selectedManga.fastAny {
                it.isEhBasedManga() ||
                    it.source in nHentaiSourceIds
            }
        }

        val showAddToMangadex: Boolean by lazy {
            selectedManga.any { it.source in mangaDexSourceIds }
        }

        val showResetInfo: Boolean by lazy {
            selectedManga.fastAny { manga ->
                manga.title != manga.ogTitle ||
                    manga.author != manga.ogAuthor ||
                    manga.artist != manga.ogArtist ||
                    manga.thumbnailUrl != manga.ogThumbnailUrl ||
                    manga.description != manga.ogDescription ||
                    manga.genre != manga.ogGenre ||
                    manga.status != manga.ogStatus
            }
        }
        // SY <--

        fun getItemsForCategoryId(categoryId: Long?): List<LibraryItem> {
            if (categoryId == null) return emptyList()
            val category = displayedCategories.find { it.id == categoryId } ?: return emptyList()
            return getItemsForCategory(category)
        }

        fun getItemsForCategory(category: Category): List<LibraryItem> {
            return groupedFavorites[category].orEmpty().mapNotNull { libraryData.favoritesById[it] }
        }

        fun getItemCountForCategory(category: Category): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) groupedFavorites[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = displayedCategories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showMangaCount -> null
                !showCategoryTabs -> getItemCountForCategory(category)
                // Whole library count
                else -> libraryData.favorites.size
            }
            return LibraryToolbarTitle(title, count)
        }
    }
}
