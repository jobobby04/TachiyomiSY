package eu.kanade.presentation.library.tracker

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackerMangaListScreenModel(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<TrackerMangaListState>(TrackerMangaListState()) {

    private var remoteIds: Set<Long> = emptySet()

    // TODO: Implement getPaginatedMangaList for all trackers
    // When changing, also update in LibraryScreenModel
    val trackers: List<Tracker> = trackerManager.loggedInTrackers().filterNot { it is EnhancedTracker }.filter { tracker -> tracker::class in listOf(Anilist::class, MyAnimeList::class) }
    private var tracker: Tracker = trackers.first()

    init {
        screenModelScope.launchIO {
            getLibraryManga.subscribe().collectLatest { mangaList ->
                val mangaIds = mangaList.map { it.id }.toSet()
                remoteIds = getTracks.await().filter { it.mangaId in mangaIds && it.trackerId == tracker.id }.map { it.remoteId }.toSet()
                mutableState.update {
                    TrackerMangaListState(
                        trackerId = tracker.id,
                        statusList = tracker.getStatusList(),
                        getStatusRes = tracker::getStatus,
                    )
                }
            }
        }
    }

    fun changeTab(index: Int) {
        mutableState.update {
            it.copy(currentTabIndex = index)
        }
        if (mutableState.value.tabs[index]?.items?.isEmpty() != false) {
            loadNextPage(index)
        }
    }

    fun loadNextPage(tabIndex: Int) {
        val currentTab = mutableState.value.tabs[tabIndex] ?: TabMangaList()
        if (currentTab.isLoading || currentTab.endReached) return

        screenModelScope.launchIO {
            val statusId = mutableState.value.statusList.getOrNull(tabIndex) ?: return@launchIO
            val currentPage = currentTab.page

            mutableState.update {
                it.copy(
                    tabs = it.tabs + (tabIndex to currentTab.copy(isLoading = true)),
                )
            }

            val newItems = tracker.getPaginatedMangaList(currentPage, statusId).filterNot { it.remoteId in remoteIds }

            mutableState.update {
                val updatedTab = currentTab.copy(
                    items = currentTab.items + newItems,
                    page = currentPage + 1,
                    isLoading = false,
                    endReached = newItems.isEmpty(),
                )
                it.copy(tabs = it.tabs + (tabIndex to updatedTab))
            }
        }
    }

    fun getTrackerName(): String {
        return tracker.name
    }

    fun changeTracker(trackerId: Long) {
        tracker = trackerManager.get(trackerId)!!
        mutableState.update {
            TrackerMangaListState(
                trackerId = trackerId,
                statusList = tracker.getStatusList(),
                getStatusRes = tracker::getStatus,
                trackerSelectDialog = false,
            )
        }
    }

    fun toggleTrackerSelectDialog() {
        mutableState.update {
            it.copy(trackerSelectDialog = !it.trackerSelectDialog)
        }
    }
}

@Immutable
data class TrackerMangaListState(
    val statusList: List<Long> = emptyList(),
    val getStatusRes: (Long) -> StringResource? = { null },
    val trackerId: Long? = null,
    val currentTabIndex: Int = 0,
    val tabs: Map<Int, TabMangaList> = emptyMap(),
    val trackerSelectDialog: Boolean = false,
)

@Immutable
data class TabMangaList(
    val items: List<TrackMangaMetadata> = emptyList(),
    val page: Int = 1,
    val endReached: Boolean = false,
    val isLoading: Boolean = false,
)
