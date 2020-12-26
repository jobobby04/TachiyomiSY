package eu.kanade.tachiyomi.ui.manga.track

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.await
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.toast
import exh.mangaDexSourceIds
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackPresenter(
    val manga: Manga,
    preferences: PreferencesHelper = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get()
) : BasePresenter<TrackController>() {

    private val context = preferences.context

    private var trackList: List<TrackItem> = emptyList()

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

    private var trackSubscription: Subscription? = null

    private var searchSubscription: Subscription? = null

    private var refreshSubscription: Subscription? = null

    // SY -->
    var needsRefresh = false
    // SY <--

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        fetchTrackings()
    }

    fun fetchTrackings() {
        trackSubscription?.let { remove(it) }
        trackSubscription = db.getTracks(manga)
            .asRxObservable()
            .map { tracks ->
                loggedServices.map { service ->
                    TrackItem(tracks.find { it.sync_id == service.id }, service)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            // SY -->
            .map { trackItems ->
                val mdTrack = trackItems.firstOrNull { it.service.id == TrackManager.MDLIST }
                if (manga.source in mangaDexSourceIds) {
                    when {
                        mdTrack == null -> {
                            trackItems
                        }
                        mdTrack.track == null -> {
                            needsRefresh = true
                            trackItems - mdTrack + createMdListTrack()
                        }
                        else -> trackItems
                    }
                } else mdTrack?.let { trackItems - it } ?: trackItems
            }
            // SY <--
            .doOnNext { trackList = it }
            .subscribeLatestCache(TrackController::onNextTrackings)
    }

    // SY -->
    private fun createMdListTrack(): TrackItem {
        val track = trackManager.mdList.createInitialTracker(manga)
        track.id = db.insertTrack(track).executeAsBlocking().insertedId()
        return TrackItem(track, trackManager.mdList)
    }
    // SY <--

    fun refresh() {
        refreshSubscription?.let { remove(it) }
        refreshSubscription = Observable.from(trackList)
            .filter { it.track != null }
            .flatMap { item ->
                item.service.refresh(item.track!!)
                    .flatMap { db.insertTrack(it).asRxObservable() }
                    .map { item }
                    .onErrorReturn { item }
            }
            .toList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, _ -> view.onRefreshDone() },
                TrackController::onRefreshError
            )
    }

    fun search(query: String, service: TrackService) {
        searchSubscription?.let { remove(it) }
        searchSubscription = service.search(query)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(
                TrackController::onSearchResults,
                TrackController::onSearchResultsError
            )
    }

    fun registerTracking(item: Track?, service: TrackService) {
        if (item != null) {
            item.manga_id = manga.id!!
            add(
                service.bind(item)
                    .flatMap { db.insertTrack(item).asRxObservable() }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { },
                        { error -> context.toast(error.message) }
                    )
            )
        } else {
            unregisterTracking(service)
        }
    }

    fun unregisterTracking(service: TrackService) {
        db.deleteTrackForManga(manga, service).executeAsBlocking()
    }

    private fun updateRemote(track: Track, service: TrackService) {
        launchIO {
            try {
                service.update(track)
                db.insertTrack(track).await()
                launchUI {
                    view!!.onRefreshDone()
                }
            } catch (e: Throwable) {
                launchUI {
                    view!!.onRefreshError(e)

                    // Restart on error to set old values
                    fetchTrackings()
                }
            }
        }
    }

    fun setStatus(item: TrackItem, index: Int) {
        val track = item.track!!
        track.status = item.service.getStatusList()[index]
        if (track.status == item.service.getCompletionStatus() && track.total_chapters != 0) {
            track.last_chapter_read = track.total_chapters
        }
        updateRemote(track, item.service)
    }

    fun setScore(item: TrackItem, index: Int) {
        val track = item.track!!
        track.score = item.service.indexToScore(index)
        updateRemote(track, item.service)
    }

    fun setLastChapterRead(item: TrackItem, chapterNumber: Int) {
        val track = item.track!!
        track.last_chapter_read = chapterNumber
        if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
            track.status = item.service.getCompletionStatus()
        }
        updateRemote(track, item.service)
    }
}
