package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
