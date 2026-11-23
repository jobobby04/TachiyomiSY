package exh.recs

import eu.kanade.tachiyomi.source.model.SManga
import exh.recs.batch.RankedSearchResults
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

// Screen args are written to the saved-state Bundle with Java serialization (#1632)
class RecommendsArgsSerializationTest {

    private fun <T : Serializable> roundTrip(value: T): T {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }
        @Suppress("UNCHECKED_CAST")
        return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() } as T
    }

    private val manga = SManga.create().apply {
        url = "/series/1"
        title = "Title"
        memo = JsonObject(mapOf("id" to JsonPrimitive(42)))
    }

    private val results = RankedSearchResults(
        recSourceName = "AniList",
        recSourceCategoryResId = 0,
        recAssociatedSourceId = null,
        results = mapOf(manga to 3),
    )

    @Test
    fun `merged recommendation args survive serialization`() {
        val restored = roundTrip(RecommendsScreen.Args.MergedSourceMangas(listOf(results)))

        val (restoredManga, count) = restored.mergedResults.single().results.entries.single()
        restoredManga.url shouldBe "/series/1"
        restoredManga.title shouldBe "Title"
        restoredManga.memo shouldBe manga.memo
        count shouldBe 3
    }

    @Test
    fun `merged browse args survive serialization`() {
        val restored = roundTrip(BrowseRecommendsScreen.Args.MergedSourceMangas(results))

        restored.results.results.keys.single().memo shouldBe manga.memo
    }
}
