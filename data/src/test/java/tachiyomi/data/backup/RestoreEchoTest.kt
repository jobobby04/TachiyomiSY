package tachiyomi.data.backup

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.time.Instant

class RestoreEchoTest {

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
    fun `server chapters without a timestamp are not local changes after a sync restore`() = runTest {
        restore(isSync = true)

        mangaRepository.getMangasModifiedSince(AFTER_EPOCH).shouldBeEmpty()
    }

    @Test
    fun `backed up chapters without a timestamp are local changes after a file restore`() = runTest {
        restore(isSync = false)

        mangaRepository.getMangasModifiedSince(AFTER_EPOCH).map { it.url } shouldContainExactly listOf("/m")
    }

    private suspend fun restore(isSync: Boolean) {
        val entry = RestoredManga(
            manga = Manga.create().copy(source = 1, url = "/m", ogTitle = "m", favoriteAt = Instant.fromEpochSeconds(1_000)),
            chapters = listOf(Chapter.create().copy(url = "/c1", name = "c1", lastModifiedAt = 0)),
            categoryIds = emptyList(),
            history = emptyList(),
            tracks = emptyList(),
            excludedScanlators = emptyList(),
        )
        restoreRepository.restoreManga(listOf(entry), isSync) { MangaUpdate(it.id) {} }
        mangaRepository.resetIsSyncing()
        chapterRepository.resetIsSyncing()
    }

    private companion object {
        const val AFTER_EPOCH = 1L
    }
}
