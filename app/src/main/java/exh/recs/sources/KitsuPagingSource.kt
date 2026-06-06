package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

class KitsuPagingSource(manga: Manga) : TrackerRecommendationPagingSource(
    "https://kitsu.io/api/edge/",
    manga,
) {
    override val name: String = "Kitsu"
    override val category: StringResource = SYMR.strings.community_recommendations
    override val associatedTrackerId: Long = trackerManager.kitsu.id

    private fun extractTitle(attributes: JsonObject): String {
        return attributes["titles"]?.jsonObject?.let { titles ->
            titles["en"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: titles["en_jp"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: titles["ja_jp"]?.jsonPrimitive?.contentOrNull
        } ?: attributes["canonicalTitle"]?.jsonPrimitive?.contentOrNull
        ?: "Unknown"
    }

    private fun extractThumbnail(attributes: JsonObject): String? {
        return attributes["posterImage"]?.jsonObject?.let { poster ->
            poster["medium"]?.jsonPrimitive?.contentOrNull
                ?: poster["small"]?.jsonPrimitive?.contentOrNull
                ?: poster["original"]?.jsonPrimitive?.contentOrNull
        }
    }

    override suspend fun getRecsById(id: String): List<SManga> {
        // Fetch manga related to this entry via mediaRelationships
        val url = "https://kitsu.io/api/edge/manga/$id/relationships/mediaRelationships"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("include", "destination")
            .addQueryParameter("filter[destinationType]", "Manga")
            .addQueryParameter("page[limit]", "20")
            .build()

        val data = with(json) {
            client.newCall(GET(url)).awaitSuccess().parseAs<JsonObject>()
        }

        val included = data["included"]?.jsonArray ?: return emptyList()

        return included
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "manga" }
            .mapNotNull { entry ->
                val entryId = entry["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val attributes = entry["attributes"]?.jsonObject ?: return@mapNotNull null
                SManga(
                    title = extractTitle(attributes),
                    url = "https://kitsu.io/manga/${attributes["slug"]?.jsonPrimitive?.contentOrNull ?: entryId}",
                    thumbnail_url = extractThumbnail(attributes),
                    initialized = true,
                )
            }
    }

    override suspend fun getRecsBySearch(search: String): List<SManga> {
        // Search for manga by title to get its Kitsu ID
        val searchUrl = "https://kitsu.io/api/edge/manga".toHttpUrl().newBuilder()
            .addQueryParameter("filter[text]", search)
            .addQueryParameter("page[limit]", "1")
            .addQueryParameter("fields[manga]", "id,slug,canonicalTitle,titles,posterImage")
            .build()

        val searchData = with(json) {
            client.newCall(GET(searchUrl)).awaitSuccess().parseAs<JsonObject>()
        }

        val id = searchData["data"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return emptyList()

        return getRecsById(id)
    }
}
