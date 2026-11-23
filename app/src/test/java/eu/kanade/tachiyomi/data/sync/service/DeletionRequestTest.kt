package eu.kanade.tachiyomi.data.sync.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.PendingHistoryDeletion

/**
 * SY-specific file
 */
class DeletionRequestTest {

    // the instance `providesJson` hands the sync services
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun queued(
        id: Long,
        chapterUrl: String = "/m/ch1",
        removedAtMs: Long = 1_700_000_000_000,
    ) = PendingHistoryDeletion(
        id = id,
        sourceId = 1,
        mangaUrl = "/m",
        chapterUrl = chapterUrl,
        removedAtMs = removedAtMs,
    )

    private fun bodyOf(
        chapters: List<String> = emptyList(),
        mangas: List<String> = emptyList(),
        histories: List<PendingHistoryDeletion> = emptyList(),
    ): String = json.encodeToString(
        SyncYomiSyncService.Deletions.serializer(),
        SyncYomiSyncService.Deletions(deletionItems(chapters, mangas, histories)),
    )

    @Test
    fun `a history item carries the removal time in seconds and a two-segment key`() {
        val body = bodyOf(
            chapters = listOf("1|/m\u001f/m/ch1"),
            mangas = listOf("1|/m"),
            histories = listOf(queued(id = 7, chapterUrl = "/m/ch2", removedAtMs = 1_700_000_000_123)),
        )

        val items = Json.parseToJsonElement(body).jsonObject.getValue("items").jsonArray
        // the queue row is not part of the key: the server merges on the key its chapter makes
        assertEquals("1|/m\u001f/m/ch2", items[2].jsonObject.getValue("key").jsonPrimitive.content)
        // and the moment of the removal travels as whole seconds, not as the queue's milliseconds
        assertEquals(1_700_000_000L, items[2].jsonObject.getValue("at").jsonPrimitive.long)
        // chapter and manga tombstones keep the two-field body the server already accepts
        assertEquals(setOf("kind", "key"), items[0].jsonObject.keys)
        assertEquals(setOf("kind", "key"), items[1].jsonObject.keys)
    }

    @Test
    fun `a queued entry with no recorded moment sends no at`() {
        // 0 is the column's "the removal happened before any time was recorded", so it must not read as
        // a removal at the epoch, which would lose to every read there is
        assertEquals(
            """{"items":[{"kind":"history","key":"1|/m\u001f/m/ch1"}]}""",
            bodyOf(histories = listOf(queued(id = 7, removedAtMs = 0))),
        )
    }

    @Test
    fun `a response that accounts for every sent entry confirms the request`() {
        assertTrue(deletionCoversEveryItem("""{"applied":3,"acknowledged":3}""", 3))
        // a retry against entries the server already tombstoned changes nothing and still read them all
        assertTrue(deletionCoversEveryItem("""{"applied":0,"acknowledged":3}""", 3))
    }

    @Test
    fun `a legacy or partial response keeps the tombstones queued`() {
        // a server that only reads the legacy chapter list answers without `applied` at all
        assertFalse(deletionCoversEveryItem("""{"acknowledged":3}""", 3))
        // and one that applied a part of the request accounts for fewer entries than were sent
        assertFalse(deletionCoversEveryItem("""{"applied":1,"acknowledged":1}""", 3))
    }

    @Test
    fun `an unreadable response confirms nothing`() {
        assertFalse(deletionCoversEveryItem(null, 3))
        assertFalse(deletionCoversEveryItem("", 3))
        assertFalse(deletionCoversEveryItem("<html>bad gateway</html>", 3))
    }
}
