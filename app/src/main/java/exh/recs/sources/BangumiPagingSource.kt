package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

class BangumiPagingSource(manga: Manga) : TrackerRecommendationPagingSource(
    "https://api.bgm.tv/",
    manga,
) {
    override val name: String = "Bangumi"
    override val category: StringResource = SYMR.strings.community_recommendations
    override val associatedTrackerId: Long = trackerManager.bangumi.id

    override suspend fun getRecsById(id: String): List<SManga> {
        val url = "https://api.bgm.tv/v0/subjects/$id/subjects".toHttpUrl()
        val data = with(json) { client.newCall(GET(url)).awaitSuccess().parseAs<JsonArray>() }
        return data
            .map { it.jsonObject }
            // Bangumi type 1 = book/manga
            .filter { rec -> rec["type"]?.jsonPrimitive?.content == "1" }
            .map { rec ->
                SManga(
                    title = rec["name_cn"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: rec["name"]?.jsonPrimitive?.content
                        ?: "Unknown",
                    url = "https://bgm.tv/subject/${rec["id"]?.jsonPrimitive?.content}",
                    thumbnail_url = rec["images"]?.jsonObject?.get("common")?.jsonPrimitive?.content,
                    initialized = true,
                )
            }
    }

    override suspend fun getRecsBySearch(search: String): List<SManga> {
        // Search for the manga by title to get its Bangumi ID
        val searchUrl = "https://api.bgm.tv/search/subject/${search.encodeToSearchPath()}".toHttpUrl()
            .newBuilder()
            .addQueryParameter("type", "1") // 1 = book/manga
            .addQueryParameter("responseGroup", "small")
            .addQueryParameter("max_results", "1")
            .build()

        val searchData = with(json) {
            client.newCall(GET(searchUrl)).awaitSuccess().parseAs<JsonObject>()
        }

        val id = searchData["list"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.content
            ?: return emptyList()

        return getRecsById(id)
    }

    private fun String.encodeToSearchPath(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
