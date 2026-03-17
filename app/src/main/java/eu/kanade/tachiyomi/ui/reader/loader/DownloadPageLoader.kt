package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.translation.data.TranslationProvider
import mihon.core.common.archive.archiveReader
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.injectLazy

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
) : PageLoader() {

    private val context: Application by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val translationPreferences: TranslationPreferences by injectLazy()
    private val translationProvider: TranslationProvider by injectLazy()

    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            /* SY --> */ manga.ogTitle, /* <-- SY */
            source,
        )
        val pages = if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
        return applyTranslatedPagesIfEnabled(pages)
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile): List<ReaderPage> {
        val loader = ArchivePageLoader(file.archiveReader(context)).also { archivePageLoader = it }
        return loader.getPages()
    }

    private fun getPagesFromDirectory(): List<ReaderPage> {
        val pages = downloadManager.buildPageList(source, manga, chapter.chapter.toDomainChapter()!!)
        return pages.map { page ->
            ReaderPage(page.index, page.url, page.imageUrl) {
                context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!!
            }.apply {
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }

    private fun applyTranslatedPagesIfEnabled(pages: List<ReaderPage>): List<ReaderPage> {
        if (!translationPreferences.translationEnabled().get()) return pages
        val mode = ReaderPreferences.TranslationPageMode.fromPreference(
            readerPreferences.translationPageMode().get(),
        )
        if (mode == ReaderPreferences.TranslationPageMode.ORIGINAL) return pages

        val domainChapter = chapter.chapter.toDomainChapter() ?: return pages
        val translatedFiles = translationProvider.findTranslatedPageFiles(domainChapter, manga, source)
        if (translatedFiles.isEmpty()) return pages

        translatedFiles.forEachIndexed { index, file ->
            val page = pages.getOrNull(index) ?: return@forEachIndexed
            page.url = file.uri.toString()
            page.imageUrl = file.uri.toString()
            page.stream = {
                file.openInputStream()
            }
            page.apply {
                status = Page.State.Ready
            }
        }
        return pages
    }
}
