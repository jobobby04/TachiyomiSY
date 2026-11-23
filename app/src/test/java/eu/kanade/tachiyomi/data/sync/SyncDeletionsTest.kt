package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupDeletedItem
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * SY-specific file
 */
class SyncDeletionsTest {

    private class Repositories {
        val manga: MangaRepository = mockk(relaxed = true)
        val chapter: ChapterRepository = mockk(relaxed = true)
        val history: HistoryRepository = mockk(relaxed = true)
        val category: CategoryRepository = mockk(relaxed = true)
    }

    private fun item(kind: String, key: String, seq: Long = 1, at: Long = 0) =
        BackupDeletedItem(kind = kind, key = key, seq = seq, at = at)

    private fun localManga() = Manga.create().copy(id = 7, source = 1, url = "/m")

    private fun localChapter(id: Long, url: String = "/m/ch1") =
        Chapter.create().copy(id = id, mangaId = 7, url = url)

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start -> sequence.indices.all { offset -> getOrNull(start + offset) == sequence[offset] } }

    // region serialisation

    @Test
    fun `a deletion survives a protobuf round trip at tag 11000`() {
        val backup = Backup(
            backupManga = listOf(BackupManga(source = 1, url = "/m", title = "/m")),
            deletions = listOf(
                item("manga", "1|/m", seq = 42),
                item("history", "1|/m\u001f/m/ch1", seq = 43, at = 1_700_000_123),
            ),
        )

        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)

        // (11000 shl 3) or 2, the varint field header of `repeated BackupDeletedItem deletions = 11000`
        assertTrue(bytes.containsSequence(byteArrayOf(0xC2.toByte(), 0xAF.toByte(), 0x05)))
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        decoded.deletions shouldBe listOf(
            BackupDeletedItem(kind = "manga", key = "1|/m", seq = 42),
            // the server's action time rides the item (field 4, Unix seconds)
            BackupDeletedItem(kind = "history", key = "1|/m\u001f/m/ch1", seq = 43, at = 1_700_000_123),
        )
    }

    @Test
    fun `an old server without the list still feeds the snapshot fallback`() {
        val uploaded = listOf(
            BackupManga(source = 1, url = "/gone", title = "/gone", favorite = true),
            BackupManga(source = 1, url = "/kept", title = "/kept", favorite = true),
        )
        val remote = ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            ProtoBuf.encodeToByteArray(Backup.serializer(), Backup(backupManga = listOf(uploaded[1]))),
        )

        // a payload written before the field existed decodes with no tombstones at all
        remote.deletions.shouldBeEmpty()
        deletionPlan(remote.deletions).manga.shouldBeEmpty()
        // so the full-snapshot heuristic is the only thing left that can drop the removed manga
        mangaKeysToDelete(
            uploaded = uploaded,
            remote = remote.backupManga,
            full = true,
            protocolV2 = true,
            librarySyncEnabled = true,
        ) shouldBe setOf(1L to "/gone")
    }

    // endregion

    // region parsing

    @Test
    fun `parses every kind this client applies`() {
        val plan = deletionPlan(
            listOf(
                item("manga", "1|/m"),
                // only the first separator splits: a url may hold one of its own
                item("manga", "3|/a|b"),
                item("chapter", "1|/m\u001f/m/ch1"),
                item("history", "2|/other\u001f/other/ch", at = 1_700_000_123),
                item("category", "uid:12"),
                item("category", "name:Reading"),
            ),
        )

        plan.manga shouldBe listOf(1L to "/m", 3L to "/a|b")
        plan.chapters shouldBe listOf(ChapterTombstone(sourceId = 1, mangaUrl = "/m", chapterUrl = "/m/ch1"))
        plan.history shouldBe listOf(
            HistoryTombstone(
                ChapterTombstone(sourceId = 2, mangaUrl = "/other", chapterUrl = "/other/ch"),
                at = 1_700_000_123,
            ),
        )
        plan.categories shouldBe listOf(CategoryTombstone(uid = 12), CategoryTombstone(name = "Reading"))
        plan.skippedKinds shouldBe emptySet()
    }

    @Test
    fun `unreadable keys become no local deletions`() = runTest {
        val repositories = Repositories()
        val plan = deletionPlan(
            listOf(
                item("manga", "no-separator"),
                item("manga", "1|"),
                item("manga", "abc|/m"),
                // a chapter key has to carry its chapter url too
                item("chapter", "1|/m"),
                item("history", "1|/m"),
                item("category", "other:1"),
                item("category", "uid:x"),
                item("category", "name:"),
            ),
        )

        plan.manga.shouldBeEmpty()
        plan.chapters.shouldBeEmpty()
        plan.history.shouldBeEmpty()
        plan.categories.shouldBeEmpty()
        plan.skippedKinds shouldBe emptySet()

        applyDeletions(
            plan = plan,
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        ) shouldBe emptySet()

        coVerify(exactly = 0) {
            repositories.manga.getMangaByUrlAndSourceId(any(), any())
            repositories.manga.deleteManga(any())
            repositories.chapter.removeChaptersWithIds(any())
            repositories.history.deleteHistory(any())
            repositories.category.getAll()
            repositories.category.deleteAll(any())
        }
    }

    // endregion

    // region applying

    @Test
    fun `a manga tombstone deletes the row and its children with it`() = runTest {
        val repositories = Repositories()
        coEvery { repositories.manga.getMangaByUrlAndSourceId("/m", 1) } returns localManga()

        applyDeletions(
            plan = deletionPlan(listOf(item("manga", "1|/m"))),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        ) shouldBe emptySet()

        coVerify(exactly = 1) { repositories.manga.deleteManga(7) }
        // chapters, history and category links go with the manga row through the foreign keys
        coVerify(exactly = 0) {
            repositories.chapter.removeChaptersWithIds(any())
            repositories.history.deleteHistory(any())
        }
    }

    @Test
    fun `a chapter tombstone deletes only that chapter`() = runTest {
        val repositories = Repositories()
        coEvery { repositories.manga.getMangaByUrlAndSourceId("/m", 1) } returns localManga()
        coEvery { repositories.chapter.getChapterByUrlAndMangaId("/m/ch1", 7) } returns localChapter(42)

        applyDeletions(
            plan = deletionPlan(listOf(item("chapter", "1|/m\u001f/m/ch1"))),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        ) shouldBe emptySet()

        coVerify(exactly = 1) { repositories.chapter.removeChaptersWithIds(listOf(42L)) }
        coVerify(exactly = 0) {
            repositories.manga.deleteManga(any())
            repositories.history.deleteHistory(any())
        }
    }

    @Test
    fun `a history tombstone deletes only that history entry`() = runTest {
        val repositories = Repositories()
        coEvery { repositories.manga.getMangaByUrlAndSourceId("/m", 1) } returns localManga()
        coEvery { repositories.chapter.getChapterByUrlAndMangaId("/m/ch1", 7) } returns localChapter(42)

        applyDeletions(
            plan = deletionPlan(listOf(item("history", "1|/m\u001f/m/ch1"))),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        ) shouldBe emptySet()

        // no action time: the row goes whatever its read, as it did before the field existed
        coVerify(exactly = 1) { repositories.history.deleteHistory(listOf(42L), 0) }
        coVerify(exactly = 0) {
            repositories.manga.deleteManga(any())
            repositories.chapter.removeChaptersWithIds(any())
        }
    }

    @Test
    fun `a history tombstone reaches the row with its own action time`() = runTest {
        val repositories = Repositories()
        coEvery { repositories.manga.getMangaByUrlAndSourceId("/m", 1) } returns localManga()
        coEvery { repositories.chapter.getChapterByUrlAndMangaId("/m/ch1", 7) } returns localChapter(42)
        coEvery { repositories.chapter.getChapterByUrlAndMangaId("/m/ch2", 7) } returns localChapter(43)

        applyDeletions(
            plan = deletionPlan(
                listOf(
                    item("history", "1|/m\u001f/m/ch1", at = 1_700_000_100),
                    item("history", "1|/m\u001f/m/ch2", at = 1_700_000_200),
                ),
            ),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        ) shouldBe emptySet()

        // every tombstone is applied at its own moment, in whole seconds: a single delete would let one
        // device's removal decide the fate of a read the other one made later
        coVerify(exactly = 1) { repositories.history.deleteHistory(listOf(42L), 1_700_000_100) }
        coVerify(exactly = 1) { repositories.history.deleteHistory(listOf(43L), 1_700_000_200) }
        coVerify(exactly = 0) { repositories.history.deleteHistory(any(), 0) }
    }

    @Test
    fun `history tombstones of one action time share a delete`() = runTest {
        val repositories = Repositories()
        coEvery { repositories.manga.getMangaByUrlAndSourceId("/m", 1) } returns localManga()
        coEvery { repositories.chapter.getChapterByUrlAndMangaId("/m/ch1", 7) } returns localChapter(42)
        coEvery { repositories.chapter.getChapterByUrlAndMangaId("/m/ch2", 7) } returns localChapter(43)

        applyDeletions(
            plan = deletionPlan(
                listOf(
                    item("history", "1|/m\u001f/m/ch1", at = 1_700_000_100),
                    item("history", "1|/m\u001f/m/ch2", at = 1_700_000_100),
                ),
            ),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        ) shouldBe emptySet()

        coVerify(exactly = 1) { repositories.history.deleteHistory(listOf(42L, 43L), 1_700_000_100) }
    }

    @Test
    fun `category tombstones resolve by uid and by name, never the system category`() = runTest {
        val repositories = Repositories()
        val local = listOf(
            Category(id = 0, name = "Default", order = 0, flags = 0, uid = 0),
            Category(id = 3, name = "Reading", order = 1, flags = 0, uid = 12),
            Category(id = 4, name = "Old name", order = 2, flags = 0, uid = 13),
        )
        coEvery { repositories.category.getAll() } returns local

        applyDeletions(
            plan = deletionPlan(
                listOf(
                    item("category", "uid:12"),
                    item("category", "name:Old name"),
                    // the system category has uid 0 and is never a tombstone target
                    item("category", "uid:0"),
                    item("category", "name:Default"),
                ),
            ),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        ) shouldBe emptySet()

        coVerify(exactly = 1) { repositories.category.getAll() }
        coVerify(exactly = 1) { repositories.category.deleteAll(listOf(3L, 4L)) }
    }

    @Test
    fun `a tombstone of a row this device never had is a no-op`() = runTest {
        val repositories = Repositories()
        coEvery { repositories.manga.getMangaByUrlAndSourceId(any(), any()) } returns null
        coEvery { repositories.category.getAll() } returns emptyList()

        val skipped = applyDeletions(
            plan = deletionPlan(
                listOf(
                    item("manga", "1|/unknown"),
                    item("chapter", "1|/unknown\u001f/unknown/ch"),
                    item("history", "1|/unknown\u001f/unknown/ch"),
                    item("category", "uid:404"),
                ),
            ),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        )

        skipped shouldBe emptySet()
        coVerify(exactly = 0) {
            repositories.manga.deleteManga(any())
            repositories.chapter.removeChaptersWithIds(any())
            repositories.history.deleteHistory(any())
            repositories.category.deleteAll(any())
        }
    }

    @Test
    fun `section kinds have no local deletion and are reported as skipped`() = runTest {
        val repositories = Repositories()
        val plan = deletionPlan(unappliedTombstoneKinds.map { item(it, "1|/m") })

        val skipped = applyDeletions(
            plan = plan,
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
        )

        skipped shouldBe unappliedTombstoneKinds
        // a settings or source tombstone must never reach a deletion path by accident
        coVerify(exactly = 0) {
            repositories.manga.getMangaByUrlAndSourceId(any(), any())
            repositories.manga.deleteManga(any())
            repositories.chapter.removeChaptersWithIds(any())
            repositories.history.deleteHistory(any())
            repositories.category.getAll()
            repositories.category.deleteAll(any())
        }
    }

    @Test
    fun `sections the user disabled are not deleted on the server's word`() = runTest {
        val repositories = Repositories()
        coEvery { repositories.manga.getMangaByUrlAndSourceId("/m", 1) } returns localManga()
        coEvery { repositories.chapter.getChapterByUrlAndMangaId("/m/ch1", 7) } returns localChapter(42)

        val skipped = applyDeletions(
            plan = deletionPlan(
                listOf(
                    item("manga", "1|/m"),
                    item("history", "1|/m\u001f/m/ch1"),
                    item("chapter", "1|/m\u001f/m/ch1"),
                ),
            ),
            mangaRepository = repositories.manga,
            chapterRepository = repositories.chapter,
            historyRepository = repositories.history,
            categoryRepository = repositories.category,
            librarySyncEnabled = false,
            historySyncEnabled = false,
        )

        skipped shouldBe setOf("manga", "history")
        coVerify(exactly = 0) {
            repositories.manga.deleteManga(any())
            repositories.history.deleteHistory(any())
        }
        // a chapter tombstone stands on its own, it does not need library sync to remove one stale row
        coVerify(exactly = 1) { repositories.chapter.removeChaptersWithIds(listOf(42L)) }
    }

    // endregion

    // region the early return

    @Test
    fun `a delta whose only change is a tombstone is not skipped`() {
        val deletion = item("manga", "1|/m")

        assertFalse(
            skipRemoteResponse(
                ownBackup = false,
                protocolV2 = true,
                changed = false,
                deletions = listOf(deletion),
            ),
        )
        // nothing changed and nothing tombstoned: the restore can be skipped
        assertTrue(
            skipRemoteResponse(ownBackup = false, protocolV2 = true, changed = false, deletions = emptyList()),
        )
    }

    @Test
    fun `our own backup is skipped even when it carries a deletion`() {
        assertTrue(
            skipRemoteResponse(
                ownBackup = true,
                protocolV2 = true,
                changed = false,
                deletions = listOf(item("manga", "1|/m")),
            ),
        )
    }

    // endregion
}
