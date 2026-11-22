package tachiyomi.data.backup

import app.cash.sqldelight.async.coroutines.awaitAsList
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.InMemoryDatabase
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.data.manga.MangaMetadataRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.time.Instant

class RestoreRepositorySyncTest {

    private val db = InMemoryDatabase()
    private val mangaRepository = MangaRepositoryImpl(db.database, db.driver)
    private val chapterRepository = ChapterRepositoryImpl(db.database)
    private val restoreRepository = RestoreRepositoryImpl(
        database = db.database,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        trackRepository = TrackRepositoryImpl(db.database),
        mangaMergeRepository = MangaMergeRepositoryImpl(db.database),
        mangaMetadataRepository = MangaMetadataRepositoryImpl(db.database),
    )

    @BeforeEach
    fun create() = runTest {
        db.create()
    }

    @AfterEach
    fun close() {
        db.close()
    }

    @Test
    fun `a sync restore takes a library removal from the server`() = runTest {
        restore(isSync = false, manga(inLibrary = true, version = 3))

        restore(isSync = true, manga(inLibrary = false, version = 4))

        val local = localManga()
        local.favorite shouldBe false
        local.version shouldBe 4
        mangaRepository.getMangasToSync(removedSince = 0).shouldBeEmpty()
    }

    @Test
    fun `a sync restore takes a library add from the server`() = runTest {
        restore(isSync = false, manga(inLibrary = false, version = 3))

        restore(isSync = true, manga(inLibrary = true, version = 4))

        localManga().favorite shouldBe true
    }

    @Test
    fun `a sync restore keeps a manga changed locally after the upload`() = runTest {
        restore(isSync = false, manga(inLibrary = false, version = 5))

        restore(isSync = true, manga(inLibrary = true, version = 4))

        val local = localManga()
        local.favorite shouldBe false
        local.version shouldBe 5
    }

    @Test
    fun `a file restore never removes a library entry`() = runTest {
        restore(isSync = false, manga(inLibrary = true, version = 3))

        restore(isSync = false, manga(inLibrary = false, version = 4))

        localManga().favorite shouldBe true
    }

    @Test
    fun `a sync restore takes an unread chapter and a lower page from the server`() = runTest {
        restore(isSync = false, manga(inLibrary = true, version = 1), chapter(read = true, lastPageRead = 10, version = 2))

        restore(isSync = true, manga(inLibrary = true, version = 1), chapter(read = false, lastPageRead = 3, version = 3))

        val local = localChapter()
        local.read shouldBe false
        local.lastPageRead shouldBe 3
        local.version shouldBe 3
    }

    @Test
    fun `a sync restore keeps a chapter changed locally after the upload`() = runTest {
        restore(isSync = false, manga(inLibrary = true, version = 1), chapter(read = true, lastPageRead = 10, version = 4))

        restore(isSync = true, manga(inLibrary = true, version = 1), chapter(read = false, lastPageRead = 0, version = 3))

        val local = localChapter()
        local.read shouldBe true
        local.lastPageRead shouldBe 10
        local.version shouldBe 4
    }

    @Test
    fun `a file restore never unreads a chapter`() = runTest {
        restore(isSync = false, manga(inLibrary = true, version = 1), chapter(read = true, lastPageRead = 10, version = 2))

        restore(isSync = false, manga(inLibrary = true, version = 1), chapter(read = false, lastPageRead = 3, version = 3))

        val local = localChapter()
        local.read shouldBe true
        local.lastPageRead shouldBe 10
    }

    @Test
    fun `a file restore adds a backed up manga with its chapters and reading history`() = runTest {
        val readAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

        restoreRepository.restoreManga(
            listOf(entry(manga(inLibrary = true, version = 0), chapter(read = true, lastPageRead = 4, version = 0), readAt = readAt)),
            isSync = false,
        ) { MangaUpdate(it.id) {} }

        localManga().favorite shouldBe true
        localChapter().read shouldBe true
        localChapter().lastPageRead shouldBe 4
        lastReadAt() shouldBe readAt
        mangaRepository.getMangasToSync(removedSince = 0).map { it.url } shouldBe listOf(URL)
    }

    @Test
    fun `a file restore keeps the latest reading history`() = runTest {
        val local = Instant.fromEpochMilliseconds(2_000_000)
        val backedUp = Instant.fromEpochMilliseconds(1_000_000)
        val c1 = chapter(read = true, lastPageRead = 1, version = 1)
        restoreRepository.restoreManga(listOf(entry(manga(inLibrary = true, version = 1), c1, readAt = local)), isSync = false) {
            MangaUpdate(it.id) {}
        }

        restoreRepository.restoreManga(listOf(entry(manga(inLibrary = true, version = 1), c1, readAt = backedUp)), isSync = false) {
            MangaUpdate(it.id) {}
        }

        lastReadAt() shouldBe local
    }

    @Test
    fun `a sync restore applies newer reading history`() = runTest {
        val c1 = chapter(read = true, lastPageRead = 1, version = 1)
        restore(isSync = false, manga(inLibrary = true, version = 1), c1)
        val readAt = Instant.fromEpochMilliseconds(3_000_000)

        restoreRepository.restoreManga(listOf(entry(manga(inLibrary = true, version = 1), c1, readAt = readAt)), isSync = true) {
            MangaUpdate(it.id) {}
        }

        lastReadAt() shouldBe readAt
    }

    private fun entry(manga: Manga, chapter: Chapter, readAt: Instant) = RestoredManga(
        manga = manga,
        chapters = listOf(chapter),
        categoryIds = emptyList(),
        history = listOf(RestoredHistory(chapterUrl = chapter.url, readAt = readAt, readDuration = 10)),
        tracks = emptyList(),
        excludedScanlators = emptyList(),
    )

    private suspend fun lastReadAt(): Instant? =
        db.database.historyQueries.getHistoryByMangaId(localManga().id).awaitAsList().single().read_at

    private suspend fun restore(isSync: Boolean, manga: Manga, vararg chapters: Chapter) {
        val entry = RestoredManga(
            manga = manga,
            chapters = chapters.toList(),
            categoryIds = emptyList(),
            history = emptyList(),
            tracks = emptyList(),
            excludedScanlators = emptyList(),
        )
        restoreRepository.restoreManga(listOf(entry), isSync) { MangaUpdate(it.id) {} }
        mangaRepository.resetIsSyncing()
        chapterRepository.resetIsSyncing()
    }

    private suspend fun localManga(): Manga = requireNotNull(mangaRepository.getMangaByUrlAndSourceId(URL, SOURCE))

    private suspend fun localChapter(): Chapter = chapterRepository.getChapterByMangaId(localManga().id).single()

    private fun manga(inLibrary: Boolean, version: Long) = Manga.create().copy(
        source = SOURCE,
        url = URL,
        ogTitle = "m",
        favoriteAt = Instant.fromEpochSeconds(1_000).takeIf { inLibrary },
        version = version,
    )

    private fun chapter(read: Boolean, lastPageRead: Int, version: Long) = Chapter.create().copy(
        url = "/c1",
        name = "c1",
        read = read,
        lastPageRead = lastPageRead,
        version = version,
    )

    private companion object {
        const val SOURCE = 1L
        const val URL = "/m"
    }
}
