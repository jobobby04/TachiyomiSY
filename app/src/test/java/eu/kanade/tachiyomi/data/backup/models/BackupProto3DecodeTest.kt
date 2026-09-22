package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.data.backup.models.metadata.BackupSearchMetadata
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A SyncYomi v2 server re-encodes backups with proto3 semantics: zero-valued scalars are never written (and
 * other clients never send them). Every backup model must therefore decode with all scalar fields absent.
 */
class BackupProto3DecodeTest {

    private val empty = ByteArray(0)

    @Test
    fun `tracking decodes without library id`() {
        val tracking = ProtoBuf.decodeFromByteArray(BackupTracking.serializer(), empty)
        assertEquals(0L, tracking.libraryId)
        assertEquals(0, tracking.syncId)
    }

    @Test
    fun `search metadata decodes without extra version`() {
        val metadata = ProtoBuf.decodeFromByteArray(BackupSearchMetadata.serializer(), empty)
        assertEquals(0, metadata.extraVersion)
        assertEquals("", metadata.extra)
        assertNull(metadata.uploader)

        val flat = ProtoBuf.decodeFromByteArray(BackupFlatMetadata.serializer(), empty)
        assertEquals(0, flat.searchMetadata.extraVersion)
    }

    @Test
    fun `other models decode from nothing`() {
        ProtoBuf.decodeFromByteArray(BackupHistory.serializer(), empty)
        ProtoBuf.decodeFromByteArray(BackupChapter.serializer(), empty)
        ProtoBuf.decodeFromByteArray(BackupCategory.serializer(), empty)
        ProtoBuf.decodeFromByteArray(BackupSource.serializer(), empty)
        ProtoBuf.decodeFromByteArray(BackupSavedSearch.serializer(), empty)
        ProtoBuf.decodeFromByteArray(BackupExtensionStore.serializer(), empty)
        ProtoBuf.decodeFromByteArray(BackupMergedMangaReference.serializer(), empty)
        ProtoBuf.decodeFromByteArray(BackupSourcePreferences.serializer(), empty)
        assertEquals(0L, ProtoBuf.decodeFromByteArray(BackupManga.serializer(), empty).source)
    }
}
