package eu.kanade.tachiyomi.ui.browse.source.browse

import android.util.Log
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SMangaImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.Observable

open class RecommendsPager(
    val manga: Manga,
    val smart: Boolean = true,
    var preferredApi: API = API.MYANIMELIST
) : Pager() {
    private val client = OkHttpClient.Builder().build()
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private fun getMyAnimeListRecsById(id: String): Deferred<List<SMangaImpl>> {
        val endpoint =
            myAnimeListEndpoint.toHttpUrlOrNull()
                ?: throw Exception("Could not convert endpoint url")
        val urlBuilder = endpoint.newBuilder()
        urlBuilder.addPathSegment("manga")
        urlBuilder.addPathSegment(id)
        urlBuilder.addPathSegment("recommendations")
        val url = urlBuilder.build().toUrl()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return scope.async {
            val response = client.newCall(request).await()
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) {
                throw Exception("Null Response")
            }
            val data = JsonParser.parseString(body).obj
            val recommendations = data["recommendations"].array
            return@async recommendations.map { rec ->
                Log.d("MYANIMELIST RECOMMEND", "${rec["title"].string}")
                SMangaImpl().apply {
                    this.title = rec["title"].string
                    this.thumbnail_url = rec["image_url"].string
                    this.initialized = true
                    this.url = rec["url"].string
                }
            }
        }
    }

    private fun getMyAnimeListRecsBySearch(search: String): Deferred<List<SMangaImpl>> {
        val endpoint =
            myAnimeListEndpoint.toHttpUrlOrNull()
                ?: throw Exception("Could not convert endpoint url")
        val urlBuilder = endpoint.newBuilder()
        urlBuilder.addPathSegment("search")
        urlBuilder.addPathSegment("manga")
        urlBuilder.addQueryParameter("q", search)
        val url = urlBuilder.build().toUrl()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return scope.async {
            val response = client.newCall(request).await()
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) {
                throw Exception("Null Response")
            }
            val data = JsonParser.parseString(body).obj
            val result = data["results"].array.first().nullObj
            val id = result?.get("mal_id")?.string
                ?: throw Exception("Couldn't find id for $search")
            return@async getMyAnimeListRecsById(id).await()
        }
    }

    private fun getAnilistRecsBySearch(search: String): Deferred<List<SMangaImpl>> {
        fun countOccurrence(arr: JsonArray, search: String): Int {
            return arr.count {
                val synonym = it.string
                synonym.contains(search, true)
            }
        }

        fun languageContains(obj: JsonObject, language: String, search: String): Boolean {
            return obj["title"].obj[language].nullString?.contains(search, true) == true
        }

        fun getTitle(obj: JsonObject): String {
            return obj["title"].obj["romaji"].nullString
                ?: obj["title"].obj["english"].nullString
                ?: obj["title"].obj["native"].string
        }

        val query =
            """
            {
                Page {
                    media(search: "$search", type: MANGA) {
                        title {
                            romaji
                            english
                            native
                        }
                        synonyms
                        recommendations {
                            edges {
                                node {
                                    mediaRecommendation {
                                        siteUrl
                                        title {
                                            romaji
                                            english
                                            native
                                        }
                                        coverImage {
                                            large
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()
        val variables = jsonObject()
        val payload = jsonObject(
            "query" to query,
            "variables" to variables
        )
        val payloadBody =
            payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(anilistEndpoint)
            .post(payloadBody)
            .build()

        return scope.async {
            val response = client.newCall(request).await()
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) {
                throw Exception("Null Response")
            }
            val data = JsonParser.parseString(body).obj["data"].obj
            val page = data["Page"].obj
            val media = page["media"].array
            val result = media.sortedWith(
                compareBy(
                    { languageContains(it.obj, "romaji", manga.title) },
                    { languageContains(it.obj, "english", manga.title) },
                    { languageContains(it.obj, "native", manga.title) },
                    { countOccurrence(it.obj["synonyms"].array, manga.title) > 0 }
                )
            ).last().nullObj
            val recommendations =
                result?.get("recommendations")?.obj?.get("edges")?.array ?: JsonArray()
            return@async recommendations.map {
                val rec = it["node"]["mediaRecommendation"].obj
                Log.d("ANILIST RECOMMEND", "${rec["title"].obj["romaji"].string}")
                SMangaImpl().apply {
                    this.title = getTitle(rec)
                    this.thumbnail_url = rec["coverImage"].obj["large"].string
                    this.initialized = true
                    this.url = rec["siteUrl"].string
                }
            }
        }
    }

    override fun requestNext(): Observable<MangasPage> {
        val recs = runBlocking {
            getMyAnimeListRecsBySearch(manga.title).await()
        }

        /*
        if (smart) {
            preferredApi =
                if (manga.mangaType() != MangaType.TYPE_MANGA) API.ANILIST else preferredApi
            Log.d("SMART RECOMMEND", preferredApi.toString())
        }

        val apiList = API.values().toMutableList()
        apiList.removeAt(apiList.indexOf(preferredApi))
        apiList.add(0, preferredApi)

        for (api in apiList) {
            // TODO await this
            when (api) {
                API.MYANIMELIST -> getMyAnimeListRecsBySearch(manga.title)
                API.ANILIST -> getAnilistRecsBySearch(manga.title)
            }
            // !TODO await this

            if (recs != null) { // this clause is only here because await isn't implemented yet. recs should never be null here
                if (recs!!.isNotEmpty()) {
                    break
                }
            }
        }
        */

        if (recs.isEmpty()) {
            throw Exception("No recommendations found")
        }

        return Observable.just(recs).map {
            MangasPage(it, false)
        }.doOnNext {
            onPageReceived(it)
        }
    }

    companion object {
        private const val myAnimeListEndpoint = "https://api.jikan.moe/v3/"
        private const val anilistEndpoint = "https://graphql.anilist.co/"

        enum class API { MYANIMELIST, ANILIST }
    }
}
