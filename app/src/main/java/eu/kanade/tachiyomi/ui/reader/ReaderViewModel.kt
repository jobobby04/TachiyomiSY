package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import ca.mpreg.imagedecoder.ImageDecoder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.util.chapter.filterDownloaded
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.MAX_FILE_NAME_BYTES
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.activeNetworkState
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.util.defaultReaderType
import exh.util.mangaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.isLocal
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Presenter used by the activity to perform background operations.
 */
@AssistedInject
class ReaderViewModel(
    @Assisted private val savedState: SavedStateHandle,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val imageSaver: ImageSaver,
    val readerPreferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
    private val downloadPreferences: DownloadPreferences,
    private val trackPreferences: TrackPreferences,
    private val trackChapter: TrackChapter,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getNextChapters: GetNextChapters,
    private val upsertHistory: UpsertHistory,
    private val updateChapter: UpdateChapter,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    private val getIncognitoState: GetIncognitoState,
    private val libraryPreferences: LibraryPreferences,
    private val coverManager: LocalCoverManager,
    private val updateManga: UpdateManga,
    private val coverCache: CoverCache,
    private val chapterCache: ChapterCache,
    private val downloadCache: DownloadCache,
    // SY -->
    private val syncPreferences: SyncPreferences,
    private val uiPreferences: UiPreferences,
    private val getFlatMetadataById: GetFlatMetadataById,
    private val getMergedMangaById: GetMergedMangaById,
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    private val setReadStatus: SetReadStatus,
    private val sourcePreferences: SourcePreferences,
    private val cbzCrypto: CbzCrypto,
    // SY <--
) : ViewModel() {

    @AssistedFactory
    @ViewModelAssistedFactoryKey(ReaderViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): ReaderViewModel {
            return create(extras.createSavedStateHandle())
        }

        fun create(@Assisted savedState: SavedStateHandle): ReaderViewModel
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    /**
     * Ids of the manga and chapter the reader was launched with, taken from the activity intent.
     */
    val mangaId = savedState.get<Long>("manga") ?: -1L
    private val initialChapterId = savedState.get<Long>("chapter") ?: -1L

    // SY -->
    private val page = savedState.get<Int>("page")?.takeUnless { it == -1 }
    // SY <--

    val hasValidArgs = mangaId != -1L && initialChapterId != -1L

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    /**
     * The source of the manga loaded in the reader. Null until it has been resolved.
     */
    val source: Source?
        get() = state.value.source

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The visible page index of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterPageIndex = savedState.get<Int>("page_index") ?: -1
        set(value) {
            savedState["page_index"] = value
            field = value
        }

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Instant? = null

    // Serializes the progress writes, which outlive the reader, so they are applied in order
    private val progressWrites = Mutex()

    // Set on Main, flushed from the chapter load too
    @Volatile
    private var pendingProgressChapter: ReaderChapter? = null
    private var pendingProgressSave: Job? = null

    private var chapterToDownload: Download? = null

    private var unfilteredChapterList: List<Chapter>? = null

    private suspend fun getUnfilteredChapterList(): List<Chapter> {
        return unfilteredChapterList
            // SY-->
            ?: if (manga!!.source == MERGED_SOURCE_ID) {
                getMergedChaptersByMangaId.await(manga!!.id, applyScanlatorFilter = false)
                    .also { unfilteredChapterList = it }
            } else {
                getChaptersByMangaId.await(manga!!.id, applyScanlatorFilter = false)
                    .also { unfilteredChapterList = it }
            }
        // SY <--
    }

    /**
     * Chapter list for the active manga. Loaded by [init].
     */
    private lateinit var chapterList: List<ReaderChapter>

    private suspend fun loadChapterList(): List<ReaderChapter> {
        val manga = manga!!
        // SY -->
        val (chapters, mangaMap) = if (manga.source == MERGED_SOURCE_ID) {
            getMergedChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
                .let { filtered ->
                    if (filtered.any { it.id == chapterId }) {
                        filtered
                    } else {
                        filtered + getMergedChaptersByMangaId.await(manga.id, applyScanlatorFilter = false)
                            .filter { it.id == chapterId }
                    }
                } to
                getMergedMangaById.await(manga.id)
                    .associateBy { it.id }
        } else {
            getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
                .let { filtered ->
                    if (filtered.any { it.id == chapterId }) {
                        filtered
                    } else {
                        filtered + getUnfilteredChapterList().filter { it.id == chapterId }
                    }
                } to null
        }
        fun isChapterDownloaded(chapter: Chapter): Boolean {
            val chapterManga = mangaMap?.get(chapter.mangaId) ?: manga
            return downloadManager.isChapterDownloaded(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                chapterUrl = chapter.url,
                mangaTitle = chapterManga.ogTitle,
                sourceId = chapterManga.source,
            )
        }
        // SY <--

        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader = when {
            (readerPreferences.skipRead.get() || readerPreferences.skipFiltered.get()) -> {
                val filteredChapters = chapters.filterNot {
                    when {
                        readerPreferences.skipRead.get() && it.read -> true
                        readerPreferences.skipFiltered.get() -> {
                            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !it.read) ||
                                (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                                // SY -->
                                (
                                    manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_DOWNLOADED &&
                                        !isChapterDownloaded(it)
                                    ) ||
                                (
                                    manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_NOT_DOWNLOADED &&
                                        isChapterDownloaded(it)
                                    ) ||
                                // SY <--
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark)
                        }
                        else -> false
                    }
                }

                if (filteredChapters.any { it.id == chapterId }) {
                    filteredChapters
                } else {
                    filteredChapters + listOf(selectedChapter)
                }
            }
            else -> chapters
        }

        return chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .run {
                if (readerPreferences.skipDupe.get()) {
                    removeDuplicates(selectedChapter)
                } else {
                    this
                }
            }
            .run {
                if (basePreferences.downloadedOnly.get()) {
                    filterDownloaded(manga, downloadCache, /* SY --> */mangaMap/* SY <-- */)
                } else {
                    this
                }
            }
            .map(::ReaderChapter)
    }

    private var incognitoMode: Boolean = false
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileReading.get()

    init {
        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            // SY -->
            .drop(1) // allow the loader to set the first page and chapter id
            // SY <--
            .onEach { currentChapter ->
                if (chapterPageIndex >= 0) {
                    // Restore from SavedState
                    currentChapter.requestedPage = chapterPageIndex
                } else if (!currentChapter.chapter.read) {
                    currentChapter.requestedPage = currentChapter.chapter.lastPageRead
                }
                chapterId = currentChapter.chapter.id
            }
            .launchIn(viewModelScope)

        if (hasValidArgs) {
            viewModelScope.launch { init() }
        }

        // SY -->
        state.mapLatest { it.ehAutoscrollFreq }
            .distinctUntilChanged()
            .drop(1)
            .onEach { text ->
                val parsed = text.toDoubleOrNull()

                if (parsed == null || parsed <= 0 || parsed > 9999) {
                    readerPreferences.autoscrollInterval.set(-1f)
                    mutableState.update { it.copy(isAutoScrollEnabled = false) }
                } else {
                    readerPreferences.autoscrollInterval.set(parsed.toFloat())
                    mutableState.update { it.copy(isAutoScrollEnabled = true) }
                }
            }
            .launchIn(viewModelScope)
        // SY <--
    }

    override fun onCleared() {
        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
            chapterToDownload?.let {
                // viewModelScope is already cancelled here
                appScope.launch { downloadManager.addDownloadsToStartOfQueue(listOf(it)) }
            }
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onActivityFinish() {
        deletePendingChapters()
    }

    /**
     * Initializes this presenter with the [mangaId] and [initialChapterId] the reader was launched
     * with. This method will fetch the manga from the database and initialize the initial chapter.
     * Failures are reported through [State.initError].
     */
    private suspend fun init() {
        withContext(Dispatchers.IO) {
            try {
                val manga = getManga.await(mangaId) ?: error("Requested manga of id $mangaId not found")
                val source = sourceManager.getOrStub(manga.source)
                incognitoMode = getIncognitoState.await(manga.source)
                // SY -->
                val metadataSource = source.getMainSource<MetadataSource<*, *>>()
                val metadata = if (metadataSource != null) {
                    getFlatMetadataById.await(mangaId)?.raise(metadataSource.metaClass)
                } else {
                    null
                }
                val mergedManga = if (source is MergedSource) {
                    getMergedMangaById.await(manga.id).associateBy { it.id }
                } else {
                    emptyMap()
                }
                val relativeTime = uiPreferences.relativeTime.get()
                val autoScrollFreq = readerPreferences.autoscrollInterval.get()
                // SY <--
                mutableState.update {
                    it.copy(
                        manga = manga,
                        source = source,
                        /* SY --> */
                        meta = metadata,
                        mergedManga = mergedManga,
                        dateRelativeTime = relativeTime,
                        ehAutoscrollFreq = if (autoScrollFreq == -1f) {
                            ""
                        } else {
                            autoScrollFreq.toString()
                        },
                        isAutoScrollEnabled = autoScrollFreq != -1f,
                        /* SY <-- */
                    )
                }
                if (chapterId == -1L) chapterId = initialChapterId

                loader = ChapterLoader(
                    context = context,
                    downloadManager = downloadManager,
                    downloadProvider = downloadProvider,
                    chapterCache = chapterCache,
                    appScope = appScope,
                    canLoadAhead = ::canLoadAhead,
                    manga = manga,
                    source = source,
                    // SY -->
                    sourceManager = sourceManager,
                    readerPrefs = readerPreferences,
                    mergedManga = mergedManga,
                    sourcePreferences = sourcePreferences,
                    cbzCrypto = cbzCrypto,
                    // SY <--
                )

                chapterList = loadChapterList()
                loadChapter(
                    loader!!,
                    chapterList.first { chapterId == it.chapter.id },
                    /* SY --> */page, /* SY <-- */
                )
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                mutableState.update { it.copy(initError = e) }
            }
        }
    }

    // SY -->
    fun getChapters(): List<ReaderChapterItem> {
        if (!::chapterList.isInitialized) return emptyList()

        val manga = manga ?: return emptyList()
        val mangaList = state.value.mergedManga?.takeIf { it.isNotEmpty() } ?: mapOf(manga.id to manga)

        val currentChapter = getCurrentChapter()

        return chapterList.map {
            ReaderChapterItem(
                chapter = it.chapter,
                manga = mangaList[it.chapter.mangaId] ?: manga,
                isCurrent = it.chapter.id == currentChapter?.chapter?.id,
                dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat.get(), Locale.getDefault()),
            )
        }
    }
    // SY <--

    /**
     * Loads the given [chapter] with this [loader] and updates the currently active chapters.
     * Callers must handle errors.
     */
    private suspend fun loadChapter(
        loader: ChapterLoader,
        chapter: ReaderChapter,
        // SY -->
        page: Int? = null,
        // SY <--
    ): ViewerChapters {
        loader.loadChapter(chapter /* SY --> */, page/* SY <-- */)

        val chapterPos = chapterList.indexOf(chapter)
        val newChapters = ViewerChapters(
            chapter,
            chapterList.getOrNull(chapterPos - 1),
            chapterList.getOrNull(chapterPos + 1),
        )
        preloadNextChapterAfter(newChapters)

        withContext(Dispatchers.Main) {
            val toDownload = cancelQueuedDownloads(newChapters.currChapter)
            mutableState.update {
                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                it.viewerChapters?.unref()

                chapterToDownload = toDownload
                it.copy(
                    viewerChapters = newChapters,
                    bookmarked = newChapters.currChapter.chapter.bookmark,
                )
            }
        }
        return newChapters
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        viewModelScope.launch(Dispatchers.IO) {
            logcat { "Loading ${chapter.chapter.url}" }

            updateHistory()
            restartReadTimer()

            try {
                loadChapter(loader, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    // SY -->
    fun loadNewChapterFromDialog(chapter: Chapter) {
        viewModelScope.launch(Dispatchers.IO) {
            val newChapter = chapterList.firstOrNull { it.chapter.id == chapter.id } ?: return@launch
            loadAdjacent(newChapter)
        }
    }
    // SY <--

    /**
     * Called when the user is going to load the prev/next chapter through the toolbar buttons.
     */
    private suspend fun loadAdjacent(chapter: ReaderChapter) {
        val loader = loader ?: return

        logcat { "Loading adjacent ${chapter.chapter.url}" }

        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withContext(Dispatchers.IO) {
                loadChapter(loader, chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    private var nextChapterPreloadJob: Job? = null

    /**
     * Preloads the next chapter of [chapters] once the current one has loaded, so it doesn't take bandwidth from the
     * pages being read. Skipped when loading ahead isn't allowed on this network.
     */
    private fun preloadNextChapterAfter(chapters: ViewerChapters) {
        val nextChapter = chapters.nextChapter ?: return
        nextChapterPreloadJob?.cancel()
        nextChapterPreloadJob = viewModelScope.launch(Dispatchers.IO) {
            if (!canLoadAhead()) return@launch
            // SY -->
            // E-Hentai limits how many images an account can view, so its chapters only load near the page being read
            if (manga?.isEhBasedManga() == true) return@launch
            // SY <--
            val current = chapters.currChapter
            // Only online pages load in the background; other loaders load a page once it's shown
            if (current.pageLoader is HttpPageLoader) {
                current.pages.orEmpty().forEach { page ->
                    page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
                }
            }
            preload(nextChapter)
        }
    }

    /**
     * Whether online chapters may load past the pages near the one being read.
     */
    private fun canLoadAhead(): Boolean =
        !readerPreferences.loadAheadOnlyOverWifi.get() || context.activeNetworkState().isWifi

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    suspend fun preload(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) {
            return
        }

        if (chapter.pageLoader?.isLocal == false) {
            val manga = /* SY --> */ state.value.mergedManga?.get(chapter.chapter.mangaId) ?: /* SY <-- */ manga ?: return
            val dbChapter = chapter.chapter
            val source = state.value.source ?: return
            val isDownloaded = downloadManager.isChapterDownloadedOnDisk(
                dbChapter.name,
                dbChapter.scanlator,
                dbChapter.url,
                /* SY --> */ manga.ogTitle /* SY <-- */,
                source,
            )
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        val loader = loader ?: return
        try {
            logcat { "Preloading ${chapter.chapter.url}" }
            loader.loadChapter(chapter)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            return
        }
        eventChannel.trySend(Event.ReloadViewerChapters)
    }

    fun onViewerLoaded(viewer: Viewer?) {
        mutableState.update {
            it.copy(viewer = viewer)
        }
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage, currentPageText: String /* SY --> */, hasExtraPage: Boolean /* SY <-- */) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) {
            return
        }

        // SY -->
        mutableState.update { it.copy(currentPageText = currentPageText) }
        // SY <--

        val selectedChapter = page.chapter
        val pages = selectedChapter.pages ?: return

        // Save last page read and mark as read if needed
        updateChapterProgress(selectedChapter, page/* SY --> */, hasExtraPage/* SY <-- */)

        if (selectedChapter != getCurrentChapter()) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }

        val inDownloadRange = page.number.toDouble() / pages.size > 0.25
        if (inDownloadRange) {
            downloadNextChapters()
        }

        eventChannel.trySend(Event.PageChanged)
    }

    private fun downloadNextChapters() {
        if (downloadAheadAmount == 0) return
        val manga = manga ?: return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
        val nextChapter = state.value.viewerChapters?.nextChapter?.chapter ?: return

        // SY -->
        val mangas = state.value.mergedManga ?: mapOf(manga.id to manga)
        val nextChapterManga = mangas[nextChapter.mangaId] ?: return
        // SY <--

        viewModelScope.launch(Dispatchers.IO) {
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                nextChapter.url,
                // SY -->
                nextChapterManga.ogTitle,
                nextChapterManga.source,
                // SY <--
            )
            if (!isNextChapterDownloaded) return@launch

            val chaptersToDownload = getNextChapters.await(manga.id, nextChapter.id).run {
                if (readerPreferences.skipDupe.get()) {
                    removeDuplicates(nextChapter)
                } else {
                    this
                }
            }.take(downloadAheadAmount)

            // SY -->
            chaptersToDownload.groupBy { it.mangaId }.forEach { (mangaId, chapters) ->
                val chapterManga = mangas[mangaId] ?: return@forEach
                downloadManager.downloadChapters(
                    chapterManga,
                    chapters,
                )
            }
            // SY <--
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private suspend fun cancelQueuedDownloads(currentChapter: ReaderChapter): Download? {
        return downloadManager.getQueuedDownloadOrNull(currentChapter.chapter.id)?.also {
            downloadManager.cancelQueuedDownloads(listOf(it))
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private suspend fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots.get()
        if (removeAfterReadSlots == -1) return

        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // If chapter is completely read, no need to download it
        chapterToDownload = null

        if (chapterToDelete == null || !chapterToDelete.chapter.read) return

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        val duplicateChapters = if (markDuplicateAsRead) {
            getUnfilteredChapterList()
                .filter {
                    it.id != chapterToDelete.chapter.id &&
                        it.isRecognizedNumber &&
                        it.chapterNumber == chapterToDelete.chapter.chapterNumber
                }
                // The list is loaded once, before these were marked read along with chapterToDelete
                .map { it.copy(read = true) }
        } else {
            emptyList()
        }
        enqueueDeleteChapters(listOf(chapterToDelete.chapter) + duplicateChapters)
    }

    /**
     * Saves the chapter progress (last read page and whether it's read)
     * if incognito mode isn't on.
     *
     * Completing a chapter is saved right away. Other page changes are saved once the reader settles on a page, or
     * when [saveChapterProgress] is called, so flipping through pages doesn't write to the database on every page.
     */
    private fun updateChapterProgress(
        readerChapter: ReaderChapter,
        page: Page/* SY --> */,
        hasExtraPage: Boolean, /* SY <-- */
    ) {
        val pageIndex = page.index

        mutableState.update {
            it.copy(currentPage = pageIndex + 1)
        }
        readerChapter.requestedPage = pageIndex
        chapterPageIndex = pageIndex

        if (incognitoMode || page.status is Page.State.Error) return

        // SY -->
        val isLastPage = readerChapter.pages?.lastIndex == pageIndex ||
            (hasExtraPage && readerChapter.pages?.lastIndex?.minus(1) == page.index)
        // SY <--
        val read = readerChapter.chapter.read || isLastPage
        // A page change in another chapter would replace that chapter's pending save
        if (pendingProgressChapter != readerChapter) saveChapterProgress()
        readerChapter.update { it.copy(lastPageRead = pageIndex, read = read) }
        pendingProgressSave?.cancel()

        if (/* SY --> */isLastPage/* SY <-- */) {
            pendingProgressChapter = null
            writeChapterProgress(readerChapter, onComplete = true)
        } else {
            pendingProgressChapter = readerChapter
            pendingProgressSave = viewModelScope.launch {
                delay(PROGRESS_SAVE_DELAY)
                saveChapterProgress()
            }
        }

        // SY -->
        // Check if syncing is enabled for chapter open:
        if (pageIndex == 0 && syncPreferences.isSyncEnabled() && syncPreferences.getSyncTriggerOptions().syncOnChapterOpen) {
            SyncDataJob.startNow(context)
        }
        // SY <--
    }

    /**
     * Writes the progress of the chapter whose page change hasn't been saved yet.
     */
    private fun saveChapterProgress() {
        val readerChapter = pendingProgressChapter ?: return
        pendingProgressChapter = null
        writeChapterProgress(readerChapter, onComplete = false)
    }

    /**
     * Writes the progress of [readerChapter] on the app scope, so it's saved even when the reader is closing.
     */
    private fun writeChapterProgress(readerChapter: ReaderChapter, onComplete: Boolean) {
        // Immediate and through the mutex, which is fair, so the writes land in the order they were made
        appScope.launch(Dispatchers.Main.immediate) {
            progressWrites.withLock {
                if (onComplete) {
                    updateChapterProgressOnComplete(readerChapter)

                    // SY -->
                    // Check if syncing is enabled for chapter read:
                    if (syncPreferences.isSyncEnabled() && syncPreferences.getSyncTriggerOptions().syncOnChapterRead) {
                        SyncDataJob.startNow(context)
                    }
                    // SY <--
                }

                val chapter = readerChapter.chapter
                updateChapter.await(
                    ChapterUpdate(chapter.id) {
                        read = chapter.read
                        lastPageRead = chapter.lastPageRead
                    },
                )
            }
        }
    }

    private suspend fun updateChapterProgressOnComplete(readerChapter: ReaderChapter) {
        // SY -->
        if (manga?.isEhBasedManga() == true) {
            appScope.launch {
                val chapterUpdates = getUnfilteredChapterList()
                    .filter { it.sourceOrder > readerChapter.chapter.sourceOrder }
                    .map { chapter ->
                        ChapterUpdate(chapter.id) { read = true }
                    }
                updateChapter.awaitAll(chapterUpdates)
            }
        }
        // SY <--

        updateTrackChapterRead(readerChapter)
        deleteChapterIfNeeded(readerChapter)

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        val duplicateUnreadChapters = getUnfilteredChapterList()
            .mapNotNull { chapter ->
                if (
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    chapter.chapterNumber == readerChapter.chapter.chapterNumber
                ) {
                    ChapterUpdate(chapter.id) { read = true }
                } else {
                    null
                }
            }
        updateChapter.awaitAll(duplicateUnreadChapters)
        // SY -->
        duplicateUnreadChapters.forEach { chapterUpdate ->
            val chapter = getUnfilteredChapterList().first { it.id == chapterUpdate.id }
            deleteChapterIfNeeded(ReaderChapter(chapter))
        }
        // SY <--
    }

    fun restartReadTimer() {
        chapterReadStartTime = Clock.System.now()
    }

    /**
     * Saves the chapter last read history on the app scope, so it's written even when the reader is closing.
     */
    fun saveHistory() {
        // Immediate so the read time is taken when the reader pauses
        appScope.launch(Dispatchers.Main.immediate) { updateHistory() }
    }

    /**
     * Saves the chapter last read history if incognito mode isn't on.
     */
    private suspend fun updateHistory() {
        saveChapterProgress()
        getCurrentChapter()?.let { readerChapter ->
            if (incognitoMode) return@let

            val chapterId = readerChapter.chapter.id
            val endTime = Clock.System.now()
            val sessionReadDuration = chapterReadStartTime?.let { (endTime - it).inWholeMilliseconds } ?: 0
            chapterReadStartTime = null

            upsertHistory.await(HistoryUpdate(chapterId, endTime, sessionReadDuration))
        }
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    suspend fun loadNextChapter() {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        loadAdjacent(nextChapter)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    suspend fun loadPreviousChapter() {
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return
        loadAdjacent(prevChapter)
    }

    /**
     * Returns the currently active chapter.
     */
    private fun getCurrentChapter(): ReaderChapter? {
        return state.value.currentChapter
    }

    fun getSource() = state.value.source as? HttpSource

    fun getChapterUrl(): String? {
        val sChapter = getCurrentChapter()?.chapter?.toSChapter() ?: return null
        val source = getSource() ?: return null

        return try {
            source.getChapterUrl(sChapter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun toggleChapterBookmark() {
        val readerChapter = getCurrentChapter() ?: return
        val bookmarked = !readerChapter.chapter.bookmark
        readerChapter.update { it.copy(bookmark = bookmarked) }

        // On Main so quick toggles are saved in order
        viewModelScope.launch {
            updateChapter.await(
                ChapterUpdate(readerChapter.chapter.id) {
                    bookmark = bookmarked
                },
            )
        }

        mutableState.update {
            it.copy(
                bookmarked = bookmarked,
            )
        }
    }

    // SY -->
    fun toggleBookmark(chapterId: Long, bookmarked: Boolean) {
        val readerChapter = chapterList.find { it.chapter.id == chapterId } ?: return
        readerChapter.update { it.copy(bookmark = bookmarked) }
        // On Main so quick toggles are saved in order
        viewModelScope.launch {
            updateChapter.await(
                ChapterUpdate(chapterId) { bookmark = bookmarked },
            )
        }
    }
    // SY <--

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultReadingMode.get()
        // SY -->
        val manga = manga ?: return default
        val readingMode = ReadingMode.fromPreference(manga.readingMode.toInt())
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT && readerPreferences.useAutoWebtoon.get() -> {
                manga.defaultReaderType(manga.mangaType(sourceName = source?.name))
                    ?: default
            }
            resolveDefault && readingMode == ReadingMode.DEFAULT -> default
            else -> manga.readingMode.toInt()
        }
        // SY <--
    }

    private var readingModeJob: Job? = null

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        // Only the latest pick matters, and letting an older one finish last would restore its choice
        readingModeJob?.cancel()
        readingModeJob = viewModelScope.launch(Dispatchers.IO) {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, readingMode.flagValue.toLong())
            val updatedManga = getManga.await(manga.id)
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.lastPageRead

                mutableState.update { it.copy(manga = updatedManga) }
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultOrientationType.get()
        val orientation = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
        return when {
            resolveDefault && orientation == ReaderOrientation.DEFAULT -> default
            else -> manga?.readerOrientation?.toInt() ?: default
        }
    }

    private var orientationJob: Job? = null

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(orientation: ReaderOrientation) {
        val manga = manga ?: return
        // Only the latest pick matters, and letting an older one finish last would restore its choice
        orientationJob?.cancel()
        orientationJob = viewModelScope.launch(Dispatchers.IO) {
            setMangaViewerFlags.awaitSetOrientation(manga.id, orientation.flagValue.toLong())
            val updatedManga = getManga.await(manga.id)
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.lastPageRead

                mutableState.update { it.copy(manga = updatedManga) }
                eventChannel.send(Event.SetOrientation(getMangaOrientation()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    // SY -->
    fun toggleCropBorders(): Boolean {
        val readingMode = getMangaReadingMode()
        val isPagerType = ReadingMode.isPagerType(readingMode)
        val isWebtoon = ReadingMode.WEBTOON.flagValue == readingMode
        return if (isPagerType) {
            readerPreferences.cropBorders.toggle()
        } else if (isWebtoon) {
            readerPreferences.cropBordersWebtoon.toggle()
        } else {
            readerPreferences.cropBordersContinuousVertical.toggle()
        }
    }
    // SY <--

    /**
     * Generate a filename for the given [manga] and [page]
     */
    private fun generateFilename(
        manga: Manga,
        page: ReaderPage,
    ): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    // SY -->
    fun showEhUtils(visible: Boolean) {
        mutableState.update { it.copy(ehUtilsVisible = visible) }
    }

    fun setIndexChapterToShift(index: Long?) {
        mutableState.update { it.copy(indexChapterToShift = index) }
    }

    fun setIndexPageToShift(index: Int?) {
        mutableState.update { it.copy(indexPageToShift = index) }
    }

    fun openChapterListDialog() {
        mutableState.update { it.copy(dialog = Dialog.ChapterList) }
    }

    fun setDoublePages(doublePages: Boolean) {
        mutableState.update { it.copy(doublePages = doublePages) }
    }

    fun openAutoScrollHelpDialog() {
        mutableState.update { it.copy(dialog = Dialog.AutoScrollHelp) }
    }

    fun openAutoScrollGesturesHelpDialog() {
        mutableState.update { it.copy(dialog = Dialog.AutoScrollGesturesHelp) }
    }

    fun openAutoScrollGestureToastsHelpDialog() {
        mutableState.update { it.copy(dialog = Dialog.AutoScrollGestureToastsHelp) }
    }

    fun openBoostPageHelp() {
        mutableState.update { it.copy(dialog = Dialog.BoostPageHelp) }
    }

    fun openRetryAllHelp() {
        mutableState.update { it.copy(dialog = Dialog.RetryAllHelp) }
    }

    fun toggleAutoScroll(enabled: Boolean) {
        mutableState.update { it.copy(autoScroll = enabled) }
    }

    fun setAutoScrollFrequency(frequency: String) {
        mutableState.update { it.copy(ehAutoscrollFreq = frequency) }
    }
    // SY <--

    fun showLoadingDialog() {
        mutableState.update { it.copy(dialog = Dialog.Loading) }
    }

    fun openReadingModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingModeSelect) }
    }

    fun openOrientationModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.OrientationModeSelect) }
    }

    fun openPageDialog(page: ReaderPage/* SY --> */, extraPage: ReaderPage? = null/* SY <-- */) {
        mutableState.update { it.copy(dialog = Dialog.PageActions(page, extraPage)) }
    }

    fun openSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun setBrightnessOverlayValue(value: Int) {
        mutableState.update { it.copy(brightnessOverlayValue = value) }
    }

    /**
     * Saves the image of the selected page on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(useExtraPage: Boolean) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val filename = generateFilename(manga, page)

        // Pictures directory.
        val relativePath = if (readerPreferences.folderPerManga.get()) {
            DiskUtil.buildValidFilename(
                manga.title,
            )
        } else {
            ""
        }

        // The save and its notification outlive the reader, the result event doesn't
        viewModelScope.launch {
            val result = appScope.async {
                try {
                    val uri = imageSaver.save(
                        image = Image.Page(
                            inputStream = page.stream!!,
                            name = filename,
                            location = Location.Pictures.create(relativePath),
                        ),
                    )
                    withContext(Dispatchers.Main) { notifier.onComplete(uri) }
                    SaveImageResult.Success(uri)
                } catch (e: Throwable) {
                    notifier.onError(e.message)
                    SaveImageResult.Error(e)
                }
            }.await()
            eventChannel.send(Event.SavedImage(result))
        }
    }

    // SY -->
    fun saveImages() {
        val (firstPage, secondPage) = (state.value.dialog as? Dialog.PageActions ?: return)
        val viewer = state.value.viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor (viewer.config.invertDoublePages)
        val bg = viewer.config.pageCanvasColor

        if (firstPage.status != Page.State.Ready) return
        if (secondPage?.status != Page.State.Ready) return

        val manga = manga ?: return

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        // The save and its notification outlive the reader, the result event doesn't
        viewModelScope.launch {
            val result = appScope.async {
                try {
                    val uri = saveImages(
                        page1 = firstPage,
                        page2 = secondPage,
                        isLTR = isLTR,
                        bg = bg,
                        location = Location.Pictures.create(DiskUtil.buildValidFilename(manga.title)),
                        manga = manga,
                    )
                    SaveImageResult.Success(uri)
                } catch (e: Throwable) {
                    notifier.onError(e.message)
                    SaveImageResult.Error(e)
                }
            }.await()
            eventChannel.send(Event.SavedImage(result))
        }
    }

    private fun saveImages(
        page1: ReaderPage,
        page2: ReaderPage,
        isLTR: Boolean,
        @ColorInt bg: Int,
        location: Location,
        manga: Manga,
    ): Uri {
        val stream1 = page1.stream!!
        ImageUtil.findImageType(stream1) ?: throw Exception("Not an image")
        val stream2 = page2.stream!!
        ImageUtil.findImageType(stream2) ?: throw Exception("Not an image")
        val imageBitmap = try {
            ImageDecoder.open(stream1()).use { it.decodeNext() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to decode stream1" }
            throw e
        }
        val imageBitmap2 = try {
            ImageDecoder.open(stream2()).use { it.decodeNext() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to decode stream2" }
            throw e
        }

        val chapter = page1.chapter.chapter

        // Build destination file.
        val filenameSuffix = " - ${page1.number}-${page2.number}.jpg"
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}".takeBytes(MAX_FILE_NAME_BYTES - filenameSuffix.byteSize()),
        ) + filenameSuffix

        return imageSaver.save(
            image = Image.Page(
                inputStream = { ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, 0, bg).inputStream() },
                name = filename,
                location = location,
            ),
        )
    }
    // SY <--

    /**
     * Shares the image of the selected page and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(copyToClipboard: Boolean, useExtraPage: Boolean) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val destDir = context.cacheImageDir

        val filename = generateFilename(manga, page)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, page))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    // SY -->
    fun shareImages(copyToClipboard: Boolean) {
        val (firstPage, secondPage) = (state.value.dialog as? Dialog.PageActions ?: return)
        val viewer = state.value.viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor (viewer.config.invertDoublePages)
        val bg = viewer.config.pageCanvasColor

        if (firstPage.status != Page.State.Ready) return
        if (secondPage?.status != Page.State.Ready) return
        val manga = manga ?: return

        val destDir = context.cacheImageDir

        viewModelScope.launch(Dispatchers.IO) {
            try {
                destDir.deleteRecursively()
                val uri = saveImages(
                    page1 = firstPage,
                    page2 = secondPage,
                    isLTR = isLTR,
                    bg = bg,
                    location = Location.Cache,
                    manga = manga,
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, firstPage, secondPage))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
            }
        }
    }
    // SY <--

    /**
     * Sets the image of the selected page as cover and notifies the UI of the result.
     */
    fun setAsCover(useExtraPage: Boolean) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        viewModelScope.launch {
            val result = appScope.async {
                try {
                    manga.editCover(coverManager, stream(), updateManga, coverCache)
                    if (manga.isLocal() || manga.favorite) {
                        SetAsCoverResult.Success
                    } else {
                        SetAsCoverResult.AddToLibraryFirst
                    }
                } catch (e: Exception) {
                    SetAsCoverResult.Error
                }
            }.await()
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    enum class SetAsCoverResult {
        Success,
        AddToLibraryFirst,
        Error,
    }

    sealed interface SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult
        class Error(val error: Throwable) : SaveImageResult
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (incognitoMode) return
        if (!trackPreferences.autoUpdateTrack.get()) return

        val manga = manga ?: return

        // Reading the last page and leaving right away is common, so it outlives the reader
        appScope.launch {
            trackChapter.await(context, manga.id, readerChapter.chapter.chapterNumber)
        }
    }

    /**
     * Enqueues these [chapters] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting them across process deaths.
     */
    private suspend fun enqueueDeleteChapters(chapters: List<Chapter>) {
        // SY -->
        val mergedManga = state.value.mergedManga
        val chaptersByManga = if (mergedManga.isNullOrEmpty()) {
            val manga = manga ?: return
            mapOf(manga to chapters)
        } else {
            chapters.groupBy { mergedManga[it.mangaId] }
                .mapNotNull { (manga, chapters) -> manga?.let { it to chapters } }
                .toMap()
        }
        chaptersByManga.forEach { (manga, chapters) ->
            downloadManager.enqueueChaptersToDelete(chapters, manga)
        }
        // SY <--
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        appScope.launch {
            // Progress writes started before leaving may still enqueue the chapter just finished
            progressWrites.withLock {}
            downloadManager.deletePendingChapters()
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val source: Source? = null,
        val initError: Throwable? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,

        /**
         * Viewer used to display the pages (pager, webtoon, ...).
         */
        val viewer: Viewer? = null,
        val dialog: Dialog? = null,
        val menuVisible: Boolean = false,
        @IntRange(from = -100, to = 100) val brightnessOverlayValue: Int = 0,

        // SY -->
        val currentPageText: String = "",
        val meta: RaisedSearchMetadata? = null,
        val mergedManga: Map<Long, Manga>? = null,
        val ehUtilsVisible: Boolean = false,
        val lastShiftDoubleState: Boolean? = null,
        val indexPageToShift: Int? = null,
        val indexChapterToShift: Long? = null,
        val doublePages: Boolean = false,
        val dateRelativeTime: Boolean = true,
        val autoScroll: Boolean = false,
        val isAutoScrollEnabled: Boolean = false,
        val ehAutoscrollFreq: String = "",
        // SY <--
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            get() = currentChapter?.pages?.size ?: -1
    }

    sealed interface Dialog {
        data object Loading : Dialog
        data object Settings : Dialog
        data object ReadingModeSelect : Dialog
        data object OrientationModeSelect : Dialog

        // SY -->
        data object ChapterList : Dialog
        // SY <--

        data class PageActions(
            val page: ReaderPage/* SY --> */,
            val extraPage: ReaderPage? = null, /* SY <-- */
        ) : Dialog

        // SY -->
        data object AutoScrollHelp : Dialog
        data object AutoScrollGesturesHelp : Dialog
        data object AutoScrollGestureToastsHelp : Dialog
        data object RetryAllHelp : Dialog
        data object BoostPageHelp : Dialog
        // SY <--
    }

    sealed interface Event {
        data object ReloadViewerChapters : Event
        data object PageChanged : Event
        data class SetOrientation(val orientation: Int) : Event
        data class SetCoverResult(val result: SetAsCoverResult) : Event

        data class SavedImage(val result: SaveImageResult) : Event
        data class ShareImage(
            val uri: Uri,
            val page: ReaderPage/* SY --> */,
            val secondPage: ReaderPage? = null, /* SY <-- */
        ) : Event
        data class CopyImage(val uri: Uri) : Event
    }
}

private val PROGRESS_SAVE_DELAY = 2.seconds
