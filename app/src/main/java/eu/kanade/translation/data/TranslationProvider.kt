package eu.kanade.translation.data

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.model.ChapterTranslationMetadata
import eu.kanade.translation.model.ChapterTranslationStatus
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    private val translationsDir: UniFile?
        get() = storageManager.getTranslationsDirectory()

    internal fun getMangaDir(mangaTitle: String, source: Source): UniFile {
        try {
            return translationsDir!!
                .createDirectory(downloadProvider.getSourceDirName(source))!!
                .createDirectory(downloadProvider.getMangaDirName(mangaTitle))!!
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid translation directory" }
            throw IllegalStateException(
                context.stringResource(
                    MR.strings.invalid_location,
                    translationsDir?.displayablePath ?: "",
                ),
            )
        }
    }

    fun findSourceDir(source: Source): UniFile? {
        return translationsDir?.findFile(downloadProvider.getSourceDirName(source))
    }

    fun findMangaDir(mangaTitle: String, source: Source): UniFile? {
        return findSourceDir(source)?.findFile(downloadProvider.getMangaDirName(mangaTitle))
    }

    fun getChapterDir(chapter: Chapter, manga: Manga, source: Source): UniFile {
        return getMangaDir(manga.ogTitle, source)
            .createDirectory(downloadProvider.getChapterDirName(chapter.name, chapter.scanlator, chapter.url))!!
    }

    fun findChapterDir(chapter: Chapter, manga: Manga, source: Source): UniFile? {
        return findMangaDir(manga.ogTitle, source)
            ?.findFile(downloadProvider.getChapterDirName(chapter.name, chapter.scanlator, chapter.url))
    }

    fun getChapterPagesDir(chapter: Chapter, manga: Manga, source: Source): UniFile {
        return getChapterDir(chapter, manga, source).createDirectory(PAGES_DIR_NAME)!!
    }

    fun findChapterPagesDir(chapter: Chapter, manga: Manga, source: Source): UniFile? {
        return findChapterDir(chapter, manga, source)?.findFile(PAGES_DIR_NAME)
    }

    fun clearChapterPages(chapter: Chapter, manga: Manga, source: Source) {
        findChapterPagesDir(chapter, manga, source)?.listFiles()?.forEach { it.delete() }
    }

    fun writeTranslatedPage(
        chapter: Chapter,
        manga: Manga,
        source: Source,
        fileName: String,
        bytes: ByteArray,
    ) {
        val pagesDir = getChapterPagesDir(chapter, manga, source)
        pagesDir.findFile(fileName)?.delete()
        val file = pagesDir.createFile(fileName)
            ?: throw IllegalStateException("Failed to create translated page file: $fileName")
        file.openOutputStream().use { it.write(bytes) }
    }

    fun findTranslatedPageFiles(chapter: Chapter, manga: Manga, source: Source): List<UniFile> {
        return findChapterPagesDir(chapter, manga, source)
            ?.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .sortedBy { it.name }
    }

    fun writeMetadata(
        chapter: Chapter,
        manga: Manga,
        source: Source,
        metadata: ChapterTranslationMetadata,
    ) {
        val chapterDir = getChapterDir(chapter, manga, source)
        chapterDir.findFile(METADATA_FILE_NAME)?.delete()
        val metadataFile = chapterDir.createFile(METADATA_FILE_NAME)
            ?: throw IllegalStateException("Failed to create translation metadata file")
        metadataFile.openOutputStream().bufferedWriter().use {
            it.write(json.encodeToString(ChapterTranslationMetadata.serializer(), metadata))
        }
    }

    fun readMetadata(chapter: Chapter, manga: Manga, source: Source): ChapterTranslationMetadata? {
        val metadataFile = findChapterDir(chapter, manga, source)?.findFile(METADATA_FILE_NAME) ?: return null
        return runCatching {
            metadataFile.openInputStream().bufferedReader().use {
                json.decodeFromString(ChapterTranslationMetadata.serializer(), it.readText())
            }
        }
            .onFailure { logcat(LogPriority.WARN, it) { "Failed to read translation metadata" } }
            .getOrNull()
    }

    fun isChapterTranslated(chapter: Chapter, manga: Manga, source: Source): Boolean {
        val metadata = readMetadata(chapter, manga, source) ?: return false
        val pageFiles = findTranslatedPageFiles(chapter, manga, source)
        return metadata.status == ChapterTranslationStatus.TRANSLATED &&
            pageFiles.size == metadata.pageCount &&
            pageFiles.mapNotNull { it.name } == metadata.translatedPageFiles
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source) {
        findChapterDir(chapter, manga, source)?.delete()
        val mangaDir = findMangaDir(manga.ogTitle, source)
        if (mangaDir?.listFiles()?.isEmpty() == true) {
            mangaDir.delete()
            val sourceDir = findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
            }
        }
    }

    fun deleteManga(manga: Manga, source: Source) {
        findMangaDir(manga.ogTitle, source)?.delete()
        val sourceDir = findSourceDir(source)
        if (sourceDir?.listFiles()?.isEmpty() == true) {
            sourceDir.delete()
        }
    }

    private companion object {
        const val METADATA_FILE_NAME = "meta.json"
        const val PAGES_DIR_NAME = "pages"
    }
}
