package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.archive.archiveReader
import mihon.core.archive.epubReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.Format

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 */
class ChapterLoader(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val chapterCache: ChapterCache,
    private val appScope: CoroutineScope,
    private val canLoadAhead: () -> Boolean,
    private val manga: Manga,
    private val source: Source,
    // SY -->
    private val sourceManager: SourceManager,
    private val readerPrefs: ReaderPreferences,
    private val mergedManga: Map<Long, Manga>,
    private val sourcePreferences: SourcePreferences,
    private val cbzCrypto: CbzCrypto,
    // SY <--
) {

    /**
     * Assigns the chapter's page loader and loads the its pages. Returns immediately if the chapter
     * is already loaded.
     */
    suspend fun loadChapter(chapter: ReaderChapter /* SY --> */, page: Int? = null/* SY <-- */) {
        if (chapterIsReady(chapter)) {
            return
        }

        chapter.state = ReaderChapter.State.Loading
        withContext(Dispatchers.IO) {
            logcat { "Loading pages for ${chapter.chapter.name}" }
            try {
                val loader = getPageLoader(chapter)
                chapter.pageLoader = loader

                val pages = loader.getPages()
                    .onEach { it.chapter = chapter }

                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }

                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (!chapter.chapter.read /* SY --> */ ||
                    readerPrefs
                        .preserveReadingPosition
                        .get() ||
                    page != null /* SY <-- */
                ) {
                    chapter.requestedPage = /* SY --> */ page ?: /* SY <-- */ chapter.chapter.lastPageRead
                }

                chapter.state = ReaderChapter.State.Loaded(pages)
            } catch (e: Throwable) {
                chapter.state = ReaderChapter.State.Error(e)
                throw e
            }
        }
    }

    /**
     * Checks [chapter] to be loaded based on present pages and loader in addition to state.
     */
    private fun chapterIsReady(chapter: ReaderChapter): Boolean {
        return chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null
    }

    /**
     * Returns the page loader to use for this [chapter].
     */
    private suspend fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloadedOnDisk(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            /* SY --> */ manga.ogTitle, /* SY <-- */
            source,
        )
        return when {
            // SY -->
            source is MergedSource -> {
                val manga = mergedManga[chapter.chapter.mangaId] ?: error("Manga for merged chapter was null")
                val source = sourceManager.get(manga.source)
                    ?: error("Source ${manga.source} was null")
                val isMergedMangaDownloaded = downloadManager.isChapterDownloaded(
                    chapterName = chapter.chapter.name,
                    chapterScanlator = chapter.chapter.scanlator,
                    chapterUrl = chapter.chapter.url,
                    mangaTitle = manga.ogTitle,
                    sourceId = manga.source,
                )
                when {
                    isMergedMangaDownloaded -> DownloadPageLoader(
                        chapter = chapter,
                        manga = manga,
                        source = source,
                        downloadManager = downloadManager,
                        downloadProvider = downloadProvider,
                        context = context,
                        readerPreferences = readerPrefs,
                        cbzCrypto = cbzCrypto,
                    )
                    source is HttpSource -> HttpPageLoader(
                        chapter,
                        source,
                        chapterCache,
                        appScope,
                        canLoadAhead,
                        sourcePreferences,
                    )
                    source is LocalSource -> source.getFormat(chapter.chapter.toSChapter()).let { format ->
                        when (format) {
                            is Format.Directory -> DirectoryPageLoader(format.file)
                            is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context, cbzCrypto::getDecryptedPasswordCbz), context, readerPrefs)
                            is Format.Epub -> EpubPageLoader(format.file.epubReader(context, cbzCrypto::getDecryptedPasswordCbz))
                        }
                    }
                    else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
                }
            }
            // SY <--
            isDownloaded -> DownloadPageLoader(
                chapter,
                manga,
                source,
                downloadManager,
                downloadProvider,
                // SY -->
                context,
                readerPrefs,
                cbzCrypto,
                // SY <--
            )
            source is LocalSource -> source.getFormat(chapter.chapter.toSChapter()).let { format ->
                when (format) {
                    is Format.Directory -> DirectoryPageLoader(format.file)
                    is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context, /* SY --> */cbzCrypto::getDecryptedPasswordCbz/* SY <-- */), /* SY --> */context, readerPrefs/* SY <-- */)
                    is Format.Epub -> EpubPageLoader(format.file.epubReader(context, /* SY --> */cbzCrypto::getDecryptedPasswordCbz/* SY <-- */))
                }
            }
            source is HttpSource -> HttpPageLoader(
                chapter,
                source,
                chapterCache,
                appScope,
                canLoadAhead,
                // SY -->
                sourcePreferences,
                // SY <--
            )
            source is StubSource -> error(context.stringResource(MR.strings.source_not_installed, source.toString()))
            else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
        }
    }
}
