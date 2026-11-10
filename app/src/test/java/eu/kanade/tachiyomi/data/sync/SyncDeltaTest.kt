package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Instant

/**
 * SY-specific file
 */
class SyncDeltaTest {

    private fun manga(url: String, modifiedAt: Long, vararg chapters: BackupChapter) = BackupManga(
        source = 1,
        url = url,
        title = url,
        lastModifiedAt = modifiedAt,
        chapters = chapters.toList(),
    )

    private fun chapter(url: String, modifiedAt: Long) = BackupChapter(
        url = url,
        name = url,
        lastModifiedAt = modifiedAt,
    )

    @Test
    fun `keeps only what changed since the last upload`() {
        val mangas = listOf(
            manga("/old", 10, chapter("/old/1", 10)),
            manga("/touched", 50, chapter("/touched/1", 10), chapter("/touched/2", 50)),
            manga("/chapter-only", 10, chapter("/chapter-only/1", 60)),
            manga("/boundary", 40),
        )

        val delta = changedSince(mangas, since = 40)

        assertEquals(listOf("/touched", "/chapter-only", "/boundary"), delta.map { it.url })
        assertEquals(listOf("/touched/2"), delta[0].chapters.map { it.url })
        assertEquals(listOf("/chapter-only/1"), delta[1].chapters.map { it.url })
        assertTrue(delta[2].chapters.isEmpty())
    }

    @Test
    fun `everything is changed since zero`() {
        val mangas = listOf(manga("/a", 0, chapter("/a/1", 0)), manga("/b", 5))

        assertEquals(2, changedSince(mangas, since = 0).size)
        assertEquals(1, changedSince(mangas, since = 0)[0].chapters.size)
    }

    @Test
    fun `categories compare by content, not identity`() {
        val local = listOf(BackupCategory(name = "Reading", order = 0, id = 3, uid = 7, lastModifiedAt = 10))
        val same = listOf(BackupCategory(name = "Reading", order = 0, id = 9, uid = 7, lastModifiedAt = 99))

        assertFalse(categoriesDiffer(local, same))
        assertTrue(categoriesDiffer(local, listOf(BackupCategory(name = "Renamed", order = 0, uid = 7))))
        assertTrue(categoriesDiffer(local, listOf(BackupCategory(name = "Reading", order = 1, uid = 7))))
        assertTrue(categoriesDiffer(local, emptyList()))
    }

    // A v2 delta response can carry categories but no manga; that must decode as an empty list.
    @Test
    fun `decodes a response without manga`() {
        val bytes = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(backupCategories = listOf(BackupCategory(name = "Reading", uid = 7))),
        )

        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        assertTrue(decoded.backupManga.isEmpty())
        assertEquals("Reading", decoded.backupCategories.single().name)

        val empty = ProtoBuf.decodeFromByteArray(Backup.serializer(), ByteArray(0))
        assertTrue(empty.backupManga.isEmpty())
    }

    @Test
    fun `chapter tombstone key matches the server key and survives the JSON body`() {
        val key = chapterTombstoneKey(
            sourceId = 9999999999,
            mangaUrl = "/e2e/E2E Alpha/01",
            chapterUrl = "/e2e/E2E Alpha/01/ch01",
        )

        assertEquals(
            "9999999999|/e2e/E2E Alpha/01\u001f/e2e/E2E Alpha/01/ch01",
            key,
        )

        val oddKey = chapterTombstoneKey(
            sourceId = 7,
            mangaUrl = "/manga/t\"a\u0001",
            chapterUrl = "/c/x\n\t—",
        )
        val serializer = ListSerializer(String.serializer())
        assertEquals(
            listOf(oddKey),
            Json.decodeFromString(serializer, Json.encodeToString(serializer, listOf(oddKey))),
        )
    }

    private fun local(
        favorite: Boolean = true,
        version: Long = 3,
        chapters: List<Chapter> = listOf(Chapter.create().copy(url = "/c1", version = 2)),
        lastRead: Map<String, Long> = mapOf("/c1" to 100L),
    ) = LocalMangaState(
        manga = Manga.create().copy(favoriteAt = Instant.fromEpochSeconds(1).takeIf { favorite }, version = version),
        chapters = chapters,
        categoryOrders = emptySet(),
        lastReadByChapterUrl = lastRead,
    )

    private fun remote(
        favorite: Boolean = true,
        version: Long = 3,
        history: List<BackupHistory> = listOf(BackupHistory(url = "/c1", lastRead = 100L)),
    ) = BackupManga(
        source = 1,
        url = "/m",
        favorite = favorite,
        version = version,
        chapters = listOf(BackupChapter(url = "/c1", version = 2)),
        history = history,
    )

    @Test
    fun `an unchanged manga needs no restore`() {
        assertFalse(needsRestore(local(), remote()))
    }

    @Test
    fun `a library removal from the server needs a restore`() {
        assertTrue(needsRestore(local(favorite = true), remote(favorite = false)))
    }

    @Test
    fun `a newer manga version needs a restore`() {
        assertTrue(needsRestore(local(version = 3), remote(version = 4)))
    }

    @Test
    fun `a chapter version change needs a restore`() {
        assertTrue(needsRestore(local(chapters = listOf(Chapter.create().copy(url = "/c1", version = 1))), remote()))
    }

    @Test
    fun `newer reading history alone needs a restore`() {
        assertTrue(needsRestore(local(), remote(history = listOf(BackupHistory(url = "/c1", lastRead = 200L)))))
    }

    @Test
    fun `older reading history needs no restore`() {
        assertFalse(needsRestore(local(), remote(history = listOf(BackupHistory(url = "/c1", lastRead = 50L)))))
    }

    @Test
    fun `manga and history tombstone keys match the server keys`() {
        assertEquals("9999999999|/e2e/E2E Alpha/01", mangaTombstoneKey(9999999999, "/e2e/E2E Alpha/01"))
        // A history row is keyed exactly like its chapter, separator included
        assertEquals(
            chapterTombstoneKey(sourceId = 7, mangaUrl = "/m", chapterUrl = "/c"),
            historyTombstoneKey(sourceId = 7, mangaUrl = "/m", chapterUrl = "/c"),
        )
        assertEquals("7|/m\u001f/c", historyTombstoneKey(sourceId = 7, mangaUrl = "/m", chapterUrl = "/c"))
    }

    private fun uploaded(url: String, favorite: Boolean = true) = BackupManga(source = 1, url = url, favorite = favorite)

    private fun snapshot(url: String, favorite: Boolean = true) =
        BackupManga(source = 1, url = url, favorite = favorite)

    @Test
    fun `a full snapshot leaves out a manga this device uploaded`() {
        val deleted = mangaKeysToDelete(
            uploaded = listOf(uploaded("/gone"), uploaded("/kept")),
            remote = listOf(snapshot("/kept")),
            full = true,
            protocolV2 = true,
            librarySyncEnabled = true,
        )

        assertEquals(setOf(1L to "/gone"), deleted)
    }

    @Test
    fun `a manga the snapshot holds as not favorite is not deleted`() {
        // The server keeps the entry to record the library removal; that is not a deletion
        val deleted = mangaKeysToDelete(
            uploaded = listOf(uploaded("/removed-elsewhere")),
            remote = listOf(snapshot("/removed-elsewhere", favorite = false)),
            full = true,
            protocolV2 = true,
            librarySyncEnabled = true,
        )

        assertEquals(emptySet<Pair<Long, String>>(), deleted)
    }

    @Test
    fun `a library removal uploaded as a not-favorite entry is not a deletion`() {
        // The row exists locally only because it was removed from the library; the server records that as
        // a not-favorite entry, and a row that never reached the server is no candidate at all
        val deleted = mangaKeysToDelete(
            uploaded = listOf(uploaded("/only"), uploaded("/not-favorite", favorite = false)),
            remote = listOf(snapshot("/only")),
            full = true,
            protocolV2 = true,
            librarySyncEnabled = true,
        )

        assertEquals(emptySet<Pair<Long, String>>(), deleted)
    }

    @Test
    fun `a delta never deletes manga`() {
        val deleted = mangaKeysToDelete(
            uploaded = listOf(uploaded("/gone")),
            remote = emptyList(),
            full = false,
            protocolV2 = true,
            librarySyncEnabled = true,
        )

        assertEquals(emptySet<Pair<Long, String>>(), deleted)
    }

    @Test
    fun `manga are not deleted without protocol v2 or library sync or a snapshot`() {
        val candidates = listOf(uploaded("/gone"))

        assertEquals(
            emptySet<Pair<Long, String>>(),
            mangaKeysToDelete(candidates, emptyList(), full = true, protocolV2 = true, librarySyncEnabled = true),
        )
        assertEquals(
            emptySet<Pair<Long, String>>(),
            mangaKeysToDelete(
                candidates,
                listOf(snapshot("/kept")),
                full = true,
                protocolV2 = false,
                librarySyncEnabled = true,
            ),
        )
        assertEquals(
            emptySet<Pair<Long, String>>(),
            mangaKeysToDelete(
                candidates,
                listOf(snapshot("/kept")),
                full = true,
                protocolV2 = true,
                librarySyncEnabled = false,
            ),
        )
    }

    private fun payload(history: List<BackupHistory>) = BackupManga(source = 1, url = "/m", history = history)

    @Test
    fun `history rows a payload does not list are deleted`() {
        val deleted = historyChapterIdsToDelete(
            payload = payload(listOf(BackupHistory(url = "/c1"))),
            historySyncEnabled = true,
            localHistoryChapterIds = listOf(11L, 22L, 33L),
            chapterUrlById = mapOf(11L to "/c1", 22L to "/c2", 33L to "/c3"),
        )

        assertEquals(setOf(22L, 33L), deleted)
    }

    @Test
    fun `history is left alone when the payload carries none`() {
        // An empty history field cannot be told apart from "nothing to say about history"
        val deleted = historyChapterIdsToDelete(
            payload = payload(emptyList()),
            historySyncEnabled = true,
            localHistoryChapterIds = listOf(11L),
            chapterUrlById = mapOf(11L to "/c1"),
        )

        assertEquals(emptySet<Long>(), deleted)
    }

    @Test
    fun `history is not deleted when history sync is off`() {
        val deleted = historyChapterIdsToDelete(
            payload = payload(listOf(BackupHistory(url = "/c1"))),
            historySyncEnabled = false,
            localHistoryChapterIds = listOf(11L, 22L),
            chapterUrlById = mapOf(11L to "/c1", 22L to "/c2"),
        )

        assertEquals(emptySet<Long>(), deleted)
    }

    @Test
    fun `history whose chapter url is unknown locally is kept`() {
        val deleted = historyChapterIdsToDelete(
            payload = payload(listOf(BackupHistory(url = "/c1"))),
            historySyncEnabled = true,
            localHistoryChapterIds = listOf(11L, 22L),
            // 22 has no chapter row, so it cannot be matched against the payload
            chapterUrlById = mapOf(11L to "/c1"),
        )

        assertEquals(emptySet<Long>(), deleted)
    }
}
