package eu.kanade.translation

import android.content.Context
import com.hippo.unifile.UniFile
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
    private val mitApiClient: MitApiClient = Injekt.get(),
    private val serverWakeService: ServerWakeService = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queueState = MutableStateFlow<List<ChapterTranslation>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private var translationJob: Job? = null

    val isRunning: Boolean
        get() = translationJob?.isActive == true

    fun startTranslation() {
        if (isRunning || queueState.value.isEmpty()) return

        queueState.value
            .filter { it.status != ChapterTranslation.State.ERROR }
            .forEach { it.status = ChapterTranslation.State.QUEUE }

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
            .forEach { it.status = ChapterTranslation.State.QUEUE }
    }

    fun clearQueue() {
        pauseTranslation()
        _queueState.value = emptyList()
    }

    fun getQueuedTranslationOrNull(chapterId: Long): ChapterTranslation? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun translateChapter(manga: Manga, chapter: Chapter) {
        val source = sourceManager.getOrStub(manga.source)
        if (queueState.value.any { it.chapter.id == chapter.id }) return

        val translation = ChapterTranslation(source, manga, chapter).apply {
            status = ChapterTranslation.State.QUEUE
        }
        _queueState.value += translation
        startTranslation()
    }

    fun cancelQueuedTranslation(translation: ChapterTranslation) {
        val wasRunning = isRunning
        if (wasRunning) pauseTranslation()
        _queueState.value = queueState.value - translation
        if (wasRunning && queueState.value.isNotEmpty()) startTranslation()
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source) {
        scope.launch {
            queueState.value.find { it.chapter.id == chapter.id }?.let { cancelQueuedTranslation(it) }
            translationProvider.deleteTranslation(chapter, manga, source)
        }
    }

    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        scope.launch {
            if (removeQueued) {
                _queueState.value = queueState.value.filterNot { it.manga.id == manga.id }
            }
            translationProvider.deleteManga(manga, source)
        }
    }

    fun getChapterTranslationStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        chapterUrl: String,
        title: String,
        sourceId: Long,
    ): ChapterTranslation.State {
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

    fun statusFlow(): Flow<ChapterTranslation> = queueState
        .flatMapLatest { translations ->
            translations
                .map { translation ->
                    translation.statusFlow.drop(1).map { translation }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value
                    .filter { it.status == ChapterTranslation.State.TRANSLATING }
                    .asFlow(),
            )
        }

    private suspend fun prepareServerIfNeeded() {
        if (!translationPreferences.wakeServerBeforeTranslation().get()) return
        val pending = queueState.value.filter { it.status == ChapterTranslation.State.QUEUE }
        if (pending.isEmpty()) return

        pending.forEach { it.status = ChapterTranslation.State.WAKING_SERVER }
        try {
            pending.forEach { it.status = ChapterTranslation.State.WAITING_SERVER }
            serverWakeService.prepareServerForQueue()
            pending.filter { it.status == ChapterTranslation.State.WAITING_SERVER }
                .forEach { it.status = ChapterTranslation.State.QUEUE }
        } catch (e: Throwable) {
            pending.forEach {
                it.errorMessage = e.message
                it.status = ChapterTranslation.State.ERROR
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
        val configJson = runCatching { buildMitConfigJson(translationPreferences, json) }.getOrDefault("{}")
        try {
            translation.status = ChapterTranslation.State.TRANSLATING
            translation.errorMessage = null

            val pageInputs = loadDownloadedPages(translation)
            require(pageInputs.isNotEmpty()) { "No downloaded pages found for translation" }

            translationProvider.clearChapterPages(translation.chapter, translation.manga, translation.source)

            val savedFiles = pageInputs.mapIndexed { index, pageInput ->
                val translatedBytes = mitApiClient.translatePage(pageInput.bytes, pageInput.name, configJson)
                val extension = ImageUtil.findImageType { translatedBytes.inputStream() }?.extension ?: DEFAULT_EXTENSION
                val fileName = String.format(Locale.ENGLISH, "%03d.%s", index + 1, extension)
                translationProvider.writeTranslatedPage(
                    chapter = translation.chapter,
                    manga = translation.manga,
                    source = translation.source,
                    fileName = fileName,
                    bytes = translatedBytes,
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

            translation.status = ChapterTranslation.State.TRANSLATED
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
            translation.errorMessage = e.message
            translation.status = ChapterTranslation.State.ERROR
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
    }
}
