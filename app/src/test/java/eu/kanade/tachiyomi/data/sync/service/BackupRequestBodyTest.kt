package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.GzipSource
import okio.buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupRequestBodyTest {

    private fun manga(url: String, title: String = url) = BackupManga(source = 1, url = url, title = title)

    private fun bytesOf(backup: Backup, gzip: Boolean = false): ByteArray =
        Buffer().also { BackupRequestBody(backup, ProtoBuf, gzip).writeTo(it) }.readByteArray()

    private fun whole(backup: Backup): ByteArray = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)

    private fun decode(bytes: ByteArray): Backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

    @Test
    fun `decodes back to the same data`() {
        val backup = Backup(
            backupManga = listOf(manga("/manga/a", "A"), manga("/manga/b", "B")),
            backupCategories = listOf(BackupCategory(name = "Cat", order = 1)),
            backupSavedSearches = listOf(BackupSavedSearch(name = "s", query = "q", filterList = "[]", source = 1)),
        )

        val decoded = decode(bytesOf(backup))

        assertEquals(backup.backupManga.map { it.url to it.title }, decoded.backupManga.map { it.url to it.title })
        assertEquals(
            backup.backupCategories.map { it.name to it.order },
            decoded.backupCategories.map { it.name to it.order },
        )
        assertEquals(backup.backupSavedSearches, decoded.backupSavedSearches)
    }

    @Test
    fun `keeps manga order`() {
        val backup = Backup(backupManga = (1..25).map { manga("/manga/$it") })

        assertEquals(backup.backupManga.map { it.url }, decode(bytesOf(backup)).backupManga.map { it.url })
    }

    @Test
    fun `matches the whole encode byte for byte`() {
        val backup = Backup(
            backupManga = (1..5).map { manga("/manga/$it") },
            backupCategories = listOf(BackupCategory(name = "Cat", order = 1)),
        )

        assertArrayEquals(whole(backup), bytesOf(backup))
    }

    @Test
    fun `a delta without manga matches the whole encode`() {
        val backup = Backup(backupCategories = listOf(BackupCategory(name = "Cat", order = 1)))

        assertArrayEquals(whole(backup), bytesOf(backup))
    }

    @Test
    fun `an empty backup is empty`() {
        assertTrue(BackupRequestBody(Backup(), ProtoBuf).metaBytes.isEmpty())
        assertEquals(0, bytesOf(Backup()).size)
    }

    @Test
    fun `gzip output inflates to the raw output`() {
        val backup = Backup(
            backupManga = (1..5).map { manga("/manga/$it") },
            backupCategories = listOf(BackupCategory(name = "Cat", order = 1)),
        )

        val inflated = GzipSource(Buffer().write(bytesOf(backup, gzip = true))).buffer().readByteArray()

        assertArrayEquals(bytesOf(backup), inflated)
    }
}
