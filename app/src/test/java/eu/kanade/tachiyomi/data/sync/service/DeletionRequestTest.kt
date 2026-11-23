package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.sync.QueuedHistoryTombstone
import eu.kanade.tachiyomi.data.sync.queuedHistoryTombstone
import eu.kanade.tachiyomi.data.sync.queuedHistoryTombstoneOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SY-specific file
 */
class DeletionRequestTest {

    // the instance `providesJson` hands the sync services
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun bodyOf(
        chapters: List<String> = emptyList(),
        mangas: List<String> = emptyList(),
        histories: List<String> = emptyList(),
    ): String = json.encodeToString(
        SyncYomiSyncService.Deletions.serializer(),
        SyncYomiSyncService.Deletions(deletionItems(chapters, mangas, histories)),
    )

    @Test
    fun `a history item carries the queued time in seconds and nothing else does`() {
        val historyKey = "1|/m\u001f/m/ch2"
        val body = bodyOf(
            chapters = listOf("1|/m\u001f/m/ch1"),
            mangas = listOf("1|/m"),
            histories = listOf(queuedHistoryTombstone(historyKey, 1_700_000_000L)),
        )

        val items = Json.parseToJsonElement(body).jsonObject.getValue("items").jsonArray
        // the queued entry's time segment must not leak into the key: the server merges on the two-segment key
        assertEquals(historyKey, items[2].jsonObject.getValue("key").jsonPrimitive.content)
        // and the moment of the removal travels as whole seconds, not as the queue's raw milliseconds
        assertEquals(1_700_000_000L, items[2].jsonObject.getValue("at").jsonPrimitive.long)
        // chapter and manga tombstones keep the two-field body the server already accepts
        assertEquals(setOf("kind", "key"), items[0].jsonObject.keys)
        assertEquals(setOf("kind", "key"), items[1].jsonObject.keys)
    }

    @Test
    fun `a queued entry without a time sends its key unchanged and no at`() {
        // queued by a build that did not record the removal: it must read as no action time at all, not as
        // a removal at the epoch, which would lose to every read there is
        assertEquals(
            """{"items":[{"kind":"history","key":"1|/m\u001f/m/ch1"}]}""",
            bodyOf(histories = listOf("1|/m\u001f/m/ch1")),
        )
    }

    @Test
    fun `a request with no history entry is unchanged`() {
        assertEquals(
            """{"items":[{"kind":"chapter","key":"1|/m\u001f/m/ch1"},{"kind":"manga","key":"1|/m"}]}""",
            bodyOf(chapters = listOf("1|/m\u001f/m/ch1"), mangas = listOf("1|/m")),
        )
    }

    @Test
    fun `a legacy key whose chapter url is a number is not read as a time`() {
        // the separator only separates our segments, but a chapter url can be a bare number; an untimed
        // entry keeps all of it or the request tombstones a key the server never held
        assertEquals(QueuedHistoryTombstone("1|/m\u001f12345"), queuedHistoryTombstoneOf("1|/m\u001f12345"))
        assertEquals(
            QueuedHistoryTombstone(key = "1|/m\u001f/m/ch1", at = 1_700_000_000L),
            queuedHistoryTombstoneOf(queuedHistoryTombstone("1|/m\u001f/m/ch1", 1_700_000_000L)),
        )
    }
}
