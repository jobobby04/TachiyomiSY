package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

class ShikimoriPagingSource(manga: Manga) : TrackerRecommendationPagingSource(
    "https://shikimori.one/api/",
    manga,
) {
    override val name: String = "Shikimori"
    override val category: StringResource = SYMR.strings.community_recommendations
    override val associatedTrackerId: Long = trackerManager.shikimori.id

    override suspend fun getRecsById(id: String): List<SManga> {
        val url = "https://shikimori.one/api/mangas/$id/similar".toHttpUrl()
        val data = with(json) { client.newCall(GET(url)).awaitSuccess().parseAs<JsonArray>() }
        return data.map { it.jsonObject }.map { rec ->
            SManga(
                title = rec["russian"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: rec["name"]?.jsonPrimitive?.content
                    ?: "Unknown",
                url = "https://shikimori.one${rec["url"]?.jsonPrimitive?.content}",
                thumbnail_url = "https://shikimori.one${rec["image"]?.jsonObject?.get("original")?.jsonPrimitive?.content}",
                initialized = true,
            )
        }
    }

    override suspend fun getRecsBySearch(search: String): List<SManga> {
        // Search for the manga by title first to get its Shikimori ID
        val searchUrl = "https://shikimori.one/api/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("search", search)
            .addQueryParameter("limit", "1")
            .addQueryParameter("kind", "manga,manhwa,manhua,one_shot,doujin")
            .build()
        val searchData = with(json) {
            client.newCall(GET(searchUrl)).awaitSuccess().parseAs<JsonArray>()
        }
        val id = searchData.firstOrNull()
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.content
            ?: return emptyList()

        return getRecsById(id)
    }
}
