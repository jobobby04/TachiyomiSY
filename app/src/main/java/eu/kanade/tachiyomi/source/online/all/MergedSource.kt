package eu.kanade.tachiyomi.source.online.all

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.copy
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.source.interactor.UpdateMangaFromRemote
import okhttp3.Response
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergeSettings
import tachiyomi.domain.manga.interactor.GetMergedEntriesById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.source.service.SourceManager

/**
 * SY-specific file
 */
@AssistedInject
class MergedSource(
    @Assisted
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
    private val getMergedEntriesById: GetMergedEntriesById,
    private val getMergeSettings: GetMergeSettings,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val updateManga: UpdateManga,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val getCategories: GetCategories,
    private val downloadManager: DownloadManager,
    private val downloadPreferences: DownloadPreferences,
    private val filterChaptersForDownload: FilterChaptersForDownload,
) : HttpSource() {

    @AssistedFactory
    fun interface Factory {
        fun create(sourceManager: SourceManager): MergedSource
    }

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getChapterList"))
    override fun fetchChapterList(manga: SManga) = throw UnsupportedOperationException()
    override suspend fun getImage(page: Page, existingSize: Long): Response = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getImageUrl"))
    override fun fetchImageUrl(page: Page) = throw UnsupportedOperationException()
    override suspend fun getImageUrl(page: Page) = throw UnsupportedOperationException()

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter) = throw UnsupportedOperationException()
    override suspend fun getPageList(chapter: SChapter) = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int) = throw UnsupportedOperationException()
    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularManga"))
    override fun fetchPopularManga(page: Int) = throw UnsupportedOperationException()
    override suspend fun getPopularManga(page: Int) = throw UnsupportedOperationException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        return SMangaUpdate(
            getMangaDetails(manga),
            emptyList(),
        )
    }

    suspend fun getMangaDetails(manga: SManga): SManga {
        return withContext(Dispatchers.IO) {
            val mergedManga = requireNotNull(getManga.await(manga.url, id)) { "merged manga not in db" }
            val mangaEntries = getMergedEntriesById.await(mergedManga.id)
                .apply {
                    require(isNotEmpty()) { "Manga merged entries are empty, info unavailable, merge is likely corrupted" }
                }

            val infoMangaId = getMergeSettings.await(mergedManga.id)?.infoMangaId
            val infoEntry = mangaEntries.firstOrNull { it.mangaId == infoMangaId }
                ?: mangaEntries.first()
            val dbManga = getManga.await(infoEntry.mangaId)?.toSManga()
            (dbManga ?: mergedManga.toSManga()).copy(
                url = manga.url,
            )
        }
    }

    suspend fun fetchChaptersAndSync(manga: Manga, downloadChapters: Boolean = true): List<Chapter> {
        val mangaEntries = getMergedEntriesById.await(manga.id)
        require(mangaEntries.isNotEmpty()) {
            "Manga merged entries are empty, chapters unavailable, merge is likely corrupted"
        }

        val semaphore = Semaphore(5)
        var exception: Exception? = null
        return supervisorScope {
            mangaEntries
                .map { entry ->
                    async {
                        semaphore.withPermit {
                            try {
                                val (source, loadedManga, _) = entry.load()
                                if (loadedManga != null && entry.fetchChapterUpdates) {
                                    val results = updateMangaFromRemote(
                                        source,
                                        loadedManga,
                                        fetchDetails = false,
                                        fetchChapters = true,
                                    ).getOrThrow().newChapters

                                    if (downloadChapters && entry.downloadChapters) {
                                        val chaptersToDownload = filterChaptersForDownload.await(manga, results)
                                        if (chaptersToDownload.isNotEmpty()) {
                                            downloadManager.downloadChapters(
                                                loadedManga,
                                                chaptersToDownload,
                                            )
                                        }
                                    }
                                    results
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                exception = e
                                emptyList()
                            }
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }.also {
            exception?.let { throw it }
        }
    }

    suspend fun MergedEntry.load(): LoadedMangaSource {
        val manga = getManga.await(mangaId)
        val source = sourceManager.getOrStub(manga?.source ?: -1)
        return LoadedMangaSource(source, manga, this)
    }

    data class LoadedMangaSource(val source: Source, val manga: Manga?, val entry: MergedEntry)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
