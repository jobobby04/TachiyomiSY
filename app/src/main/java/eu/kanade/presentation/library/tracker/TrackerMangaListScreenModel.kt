package eu.kanade.presentation.library.tracker

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
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

    init {
        screenModelScope.launchIO {
            val tracker = trackerManager.loggedInTrackers().firstOrNull() ?: return@launchIO
            val mangaIds = getLibraryManga.await().map { it.id }.toSet()
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
            val trackerId = mutableState.value.trackerId ?: return@launchIO
            val tracker = trackerManager.get(trackerId) ?: return@launchIO

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
}

@Immutable
data class TrackerMangaListState(
    val statusList: List<Long> = emptyList(),
    val getStatusRes: (Long) -> StringResource? = { null },
    val trackerId: Long? = null,
    val currentTabIndex: Int = 0,
    val tabs: Map<Int, TabMangaList> = emptyMap(),
)

@Immutable
data class TabMangaList(
    val items: List<TrackMangaMetadata> = emptyList(),
    val page: Int = 1,
    val endReached: Boolean = false,
    val isLoading: Boolean = false,
)
