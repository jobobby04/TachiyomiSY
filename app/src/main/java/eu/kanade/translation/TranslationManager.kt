package eu.kanade.translation

import android.content.Context
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.model.ChapterTranslation
import eu.kanade.translation.model.ChapterTranslationMetadata
import eu.kanade.translation.model.ChapterTranslationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.common.archive.archiveReader
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.json.Json

class TranslationManager(
    private val context: Context,
    private val translationProvider: TranslationProvider = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
    private val mitApiClient: MitApiClient = Injekt.get(),
    private val serverWakeService: ServerWakeService = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queueState = MutableStateFlow<List<ChapterTranslation>>(emptyList())
    private val statusEvents = MutableSharedFlow<ChapterTranslation>(extraBufferCapacity = 64)
    val queueState = _queueState.asStateFlow()

    private var translationJob: Job? = null

    val isRunning: Boolean
        get() = translationJob?.isActive == true

    init {
        observeTranslationEnabled()
        observeCompletedDownloads()
    }

    fun startTranslation() {
        if (!translationPreferences.translationEnabled().get()) return
        if (isRunning || queueState.value.isEmpty()) return

        queueState.value
            .filter { it.status != ChapterTranslation.State.ERROR }
            .forEach { updateStatus(it, ChapterTranslation.State.QUEUE) }

        translationJob = scope.launch {
            try {
                prepareServerIfNeeded()
                processQueue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Translation queue stopped unexpectedly" }
            } finally {
                translationJob = null
            }
        }
    }

    fun pauseTranslation() {
        translationJob?.cancel()
        translationJob = null
        queueState.value
            .filter {
                it.status == ChapterTranslation.State.TRANSLATING ||
                    it.status == ChapterTranslation.State.WAKING_SERVER ||
                    it.status == ChapterTranslation.State.WAITING_SERVER
            }
            .forEach { updateStatus(it, ChapterTranslation.State.QUEUE) }
    }

    fun clearQueue() {
        pauseTranslation()
        _queueState.value = emptyList()
    }

    fun getQueuedTranslationOrNull(chapterId: Long): ChapterTranslation? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun translateChapter(manga: Manga, chapter: Chapter) {
        if (!translationPreferences.translationEnabled().get()) return
        val source = sourceManager.getOrStub(manga.source)
        if (queueState.value.any { it.chapter.id == chapter.id }) return

        val translation = ChapterTranslation(source, manga, chapter).apply {
            status = ChapterTranslation.State.QUEUE
            translatedPages = 0
            totalPages = 0
        }
        _queueState.value += translation
        statusEvents.tryEmit(translation)
        startTranslation()
    }

    fun cancelQueuedTranslation(translation: ChapterTranslation) {
        val wasRunning = isRunning
        if (wasRunning) pauseTranslation()
        _queueState.value = queueState.value - translation
        if (wasRunning && queueState.value.isNotEmpty()) startTranslation()
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source) {
        queueState.value.find { it.chapter.id == chapter.id }?.let { cancelQueuedTranslation(it) }
        translationProvider.deleteTranslation(chapter, manga, source)
    }

    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        if (removeQueued) {
            removeQueuedTranslations { it.manga.id == manga.id }
        }
        translationProvider.deleteManga(manga, source)
    }

    fun getChapterTranslationStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        chapterUrl: String,
        title: String,
        sourceId: Long,
    ): ChapterTranslation.State {
        if (!translationPreferences.translationEnabled().get()) return ChapterTranslation.State.NOT_TRANSLATED
        val queued = getQueuedTranslationOrNull(chapterId)
        if (queued != null) return queued.status

        val source = sourceManager.get(sourceId) ?: return ChapterTranslation.State.NOT_TRANSLATED
        val chapter = Chapter.create().copy(
            id = chapterId,
            url = chapterUrl,
            name = chapterName,
            scanlator = scanlator,
        )
        val manga = Manga.create().copy(
            id = -1L,
            source = sourceId,
            ogTitle = title,
        )
        return if (translationProvider.isChapterTranslated(chapter, manga, source)) {
            ChapterTranslation.State.TRANSLATED
        } else {
            ChapterTranslation.State.NOT_TRANSLATED
        }
    }

    fun statusFlow(): Flow<ChapterTranslation> = statusEvents.onStart {
        queueState.value.forEach { emit(it) }
    }

    private fun observeCompletedDownloads() {
        scope.launch {
            downloadManager.statusFlow()
                .filter { it.status == Download.State.DOWNLOADED }
                .collect { download ->
                    enqueueAutoTranslation(download)
                }
        }
    }

    private fun observeTranslationEnabled() {
        scope.launch {
            translationPreferences.translationEnabled().changes().collect { enabled ->
                if (!enabled) {
                    clearQueue()
                }
            }
        }
    }

    private fun enqueueAutoTranslation(download: Download) {
        if (!translationPreferences.translationEnabled().get()) return
        if (!translationPreferences.autoTranslateAfterDownload().get()) return
        if (!isAutoTranslateSourceEnabled(download.source)) return
        if (queueState.value.any { it.chapter.id == download.chapter.id }) return
        if (translationProvider.isChapterTranslated(download.chapter, download.manga, download.source)) return

        val translation = ChapterTranslation(download.source, download.manga, download.chapter).apply {
            status = ChapterTranslation.State.QUEUE
            translatedPages = 0
            totalPages = 0
        }
        _queueState.value += translation
        statusEvents.tryEmit(translation)
        startTranslation()
    }

    private fun removeQueuedTranslations(predicate: (ChapterTranslation) -> Boolean) {
        val removedTranslations = queueState.value.filter(predicate)
        if (removedTranslations.isEmpty()) return

        val wasRunning = isRunning
        if (wasRunning) {
            pauseTranslation()
        }

        _queueState.value = queueState.value - removedTranslations.toSet()

        if (wasRunning && queueState.value.isNotEmpty()) {
            startTranslation()
        }
    }

    private suspend fun prepareServerIfNeeded() {
        if (!translationPreferences.wakeServerBeforeTranslation().get()) return
        val pending = queueState.value.filter { it.status == ChapterTranslation.State.QUEUE }
        if (pending.isEmpty()) return

        pending.forEach { updateStatus(it, ChapterTranslation.State.WAKING_SERVER) }
        try {
            pending.forEach { updateStatus(it, ChapterTranslation.State.WAITING_SERVER) }
            serverWakeService.prepareServerForQueue()
            pending.filter { it.status == ChapterTranslation.State.WAITING_SERVER }
                .forEach { updateStatus(it, ChapterTranslation.State.QUEUE) }
        } catch (e: Throwable) {
            pending.forEach {
                updateStatus(it, ChapterTranslation.State.ERROR, e.message)
            }
        }
    }

    private suspend fun processQueue() {
        while (true) {
            val next = queueState.value.firstOrNull { it.status == ChapterTranslation.State.QUEUE } ?: break
            translateQueuedChapter(next)
        }
    }

    private suspend fun translateQueuedChapter(translation: ChapterTranslation) {
        var configJson = "{}"
        try {
            updateStatus(translation, ChapterTranslation.State.TRANSLATING)

            val pageInputs = loadDownloadedPages(translation)
            require(pageInputs.isNotEmpty()) { "No downloaded pages found for translation" }
            updateProgress(
                translation = translation,
                translatedPages = 0,
                totalPages = pageInputs.size,
            )
            val pageConfigJsons = pageInputs.map { pageInput ->
                buildMitConfigJson(
                    preferences = translationPreferences,
                    json = json,
                    shouldUpscale = shouldUpscaleImage(pageInput.bytes),
                )
            }
            configJson = pageConfigJsons.firstOrNull() ?: "{}"

            translationProvider.clearChapterPages(translation.chapter, translation.manga, translation.source)

            val savedFiles = pageInputs.mapIndexed { index, pageInput ->
                val translatedBytes = mitApiClient.translatePage(pageInput.bytes, pageInput.name, pageConfigJsons[index])
                val extension = ImageUtil.findImageType { translatedBytes.inputStream() }?.extension ?: DEFAULT_EXTENSION
                val fileName = String.format(Locale.ENGLISH, "%03d.%s", index + 1, extension)
                translationProvider.writeTranslatedPage(
                    chapter = translation.chapter,
                    manga = translation.manga,
                    source = translation.source,
                    fileName = fileName,
                    bytes = translatedBytes,
                )
                updateProgress(
                    translation = translation,
                    translatedPages = index + 1,
                    totalPages = pageInputs.size,
                )
                fileName
            }

            translationProvider.writeMetadata(
                chapter = translation.chapter,
                manga = translation.manga,
                source = translation.source,
                metadata = ChapterTranslationMetadata(
                    mangaId = translation.manga.id,
                    chapterId = translation.chapter.id,
                    sourceId = translation.source.id,
                    mangaTitle = translation.manga.ogTitle,
                    chapterName = translation.chapter.name,
                    chapterScanlator = translation.chapter.scanlator,
                    chapterUrl = translation.chapter.url,
                    pageCount = savedFiles.size,
                    translatedPageFiles = savedFiles,
                    status = ChapterTranslationStatus.TRANSLATED,
                    serverBaseUrl = translationPreferences.translationServerBaseUrl().get(),
                    endpointPath = mitApiClient.buildEndpointUrl(),
                    usedConfigJson = configJson,
                    translatedAt = System.currentTimeMillis(),
                ),
            )

            updateStatus(translation, ChapterTranslation.State.TRANSLATED)
            _queueState.value = queueState.value - translation
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to translate chapter ${translation.chapter.name}" }
            translationProvider.clearChapterPages(translation.chapter, translation.manga, translation.source)
            translationProvider.writeMetadata(
                chapter = translation.chapter,
                manga = translation.manga,
                source = translation.source,
                metadata = ChapterTranslationMetadata(
                    mangaId = translation.manga.id,
                    chapterId = translation.chapter.id,
                    sourceId = translation.source.id,
                    mangaTitle = translation.manga.ogTitle,
                    chapterName = translation.chapter.name,
                    chapterScanlator = translation.chapter.scanlator,
                    chapterUrl = translation.chapter.url,
                    pageCount = 0,
                    translatedPageFiles = emptyList(),
                    status = ChapterTranslationStatus.ERROR,
                    serverBaseUrl = translationPreferences.translationServerBaseUrl().get(),
                    endpointPath = runCatching { mitApiClient.buildEndpointUrl() }.getOrDefault(""),
                    usedConfigJson = configJson,
                    translatedAt = System.currentTimeMillis(),
                    errorMessage = e.message,
                ),
            )
            updateStatus(translation, ChapterTranslation.State.ERROR, e.message)
        }
    }

    private fun updateStatus(
        translation: ChapterTranslation,
        status: ChapterTranslation.State,
        errorMessage: String? = null,
    ) {
        translation.errorMessage = errorMessage
        translation.status = status
        statusEvents.tryEmit(translation)
    }

    private fun updateProgress(
        translation: ChapterTranslation,
        translatedPages: Int,
        totalPages: Int,
    ) {
        translation.translatedPages = translatedPages
        translation.totalPages = totalPages
        statusEvents.tryEmit(translation)
    }

    private fun shouldUpscaleImage(imageBytes: ByteArray): Boolean {
        return when (translationPreferences.translationUpscaleMode().get()) {
            "auto" -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                options.outHeight in 1..<AUTO_UPSCALE_MAX_HEIGHT
            }
            else -> false
        }
    }

    private fun isAutoTranslateSourceEnabled(source: Source): Boolean {
        if (!translationPreferences.autoTranslateOnlySelectedSources().get()) {
            return true
        }

        return if (translationPreferences.autoTranslateSourceSelectionCustomized().get()) {
            source.id.toString() in translationPreferences.autoTranslateSelectedSourceIds().get()
        } else {
            source.lang == DEFAULT_AUTO_TRANSLATE_LANGUAGE
        }
    }

    private fun loadDownloadedPages(translation: ChapterTranslation): List<PageInput> {
        val chapterPath = downloadProvider.findChapterDir(
            translation.chapter.name,
            translation.chapter.scanlator,
            translation.chapter.url,
            translation.manga.ogTitle,
            translation.source,
        ) ?: throw IllegalStateException("Chapter must be downloaded before translation")

        return if (chapterPath.isFile) {
            loadArchivePages(chapterPath)
        } else {
            loadDirectoryPages(chapterPath)
        }
    }

    private fun loadDirectoryPages(chapterDir: UniFile): List<PageInput> {
        return chapterDir.listFiles()
            .orEmpty()
            .filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }
            .sortedBy { it.name }
            .map { file ->
                PageInput(
                    name = file.name.orEmpty(),
                    bytes = file.openInputStream().use(InputStream::readBytes),
                )
            }
    }

    private fun loadArchivePages(archiveFile: UniFile): List<PageInput> {
        return archiveFile.archiveReader(context).use { reader ->
            reader.useEntries { entries ->
                entries
                    .filter { entry ->
                        entry.isFile && ImageUtil.isImage(entry.name) {
                            reader.getInputStream(entry.name)!!
                        }
                    }
                    .sortedBy { it.name }
                    .map { entry ->
                        PageInput(
                            name = entry.name.substringAfterLast('/'),
                            bytes = reader.getInputStream(entry.name)!!.use(InputStream::readBytes),
                        )
                    }
                    .toList()
            }
        }
    }

    private data class PageInput(
        val name: String,
        val bytes: ByteArray,
    )

    private companion object {
        const val DEFAULT_EXTENSION = "png"
        const val AUTO_UPSCALE_MAX_HEIGHT = 1000
        const val DEFAULT_AUTO_TRANSLATE_LANGUAGE = "ja"
    }
}
