package eu.kanade.tachiyomi.ui.browse.source.browse

import android.util.Log
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SMangaImpl
import exh.util.MangaType
import exh.util.mangaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.Observable
import rx.schedulers.Schedulers

open class RecommendsPager(
    val manga: Manga,
    val smart: Boolean = true,
    var preferredApi: API = API.MYANIMELIST
) : Pager() {
    private val client = OkHttpClient.Builder().build()

    private fun countOccurrence(array: JsonArray, search: String): Int {
        return array.count {
            val synonym = it.string
            synonym.contains(search, true)
        }
    }

    private fun myAnimeList(): Observable<List<SMangaImpl>>? {
        fun getId(): Observable<String?> {
            val endpoint =
                myAnimeListEndpoint.toHttpUrlOrNull()
                    ?: throw Exception("Could not convert endpoint url")
            val urlBuilder = endpoint.newBuilder()
            urlBuilder.addPathSegment("search")
            urlBuilder.addPathSegment("manga")
            urlBuilder.addQueryParameter("q", manga.title)
            val url = urlBuilder.build().toUrl()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            return client.newCall(request)
                .asObservableSuccess().subscribeOn(Schedulers.io())
                .map { netResponse ->
                    val responseBody = netResponse.body?.string().orEmpty()
                    if (responseBody.isEmpty()) {
                        throw Exception("Null Response")
                    }
                    val response = JsonParser.parseString(responseBody).obj
                    val result = response["results"].array
                        .find {
                            val title = it["title"].string
                            title.contains(manga.title, true)
                        }
                        ?: return@map null
                    result["mal_id"].string
                }
        }

        return getId().map { id ->
            if (id == null) {
                return@map listOf<SMangaImpl>()
            }
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

            client.newCall(request)
                .asObservableSuccess().subscribeOn(Schedulers.io())
                .map { netResponse ->
                    val responseBody = netResponse.body?.string().orEmpty()
                    if (responseBody.isEmpty()) {
                        throw Exception("Null Response")
                    }
                    val response = JsonParser.parseString(responseBody).obj
                    val recommendations = response["recommendations"].array
                    recommendations.map { rec ->
                        Log.d("MYANIMELIST RECOMMEND", "${rec["title"].string}")
                        SMangaImpl().apply {
                            this.title = rec["title"].string
                            this.thumbnail_url = rec["image_url"].string
                            this.initialized = true
                            this.url = rec["url"].string
                        }
                    }
                }.toBlocking().first()
        }
    }

    private fun anilist(): Observable<List<SMangaImpl>>? {
        val query =
            """
            {
                Page {
                    media(search: "${manga.title}", type: MANGA) {
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
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(anilistEndpoint)
            .post(body)
            .build()

        return client.newCall(request)
            .asObservableSuccess().subscribeOn(Schedulers.io())
            .map { netResponse ->
                val responseBody = netResponse.body?.string().orEmpty()
                if (responseBody.isEmpty()) {
                    throw Exception("Null Response")
                }
                val response = JsonParser.parseString(responseBody).obj
                val data = response["data"]!!.obj
                val page = data["Page"].obj
                val media = page["media"].array
                val result = media.find {
                    val title = it["title"].obj
                    if (title["romaji"].nullString?.contains(manga.title, true) == true) {
                        return@find true
                    }
                    if (title["english"].nullString?.contains(manga.title, true) == true) {
                        return@find true
                    }
                    if (title["native"].nullString?.contains(manga.title, true) == true) {
                        return@find true
                    }
                    if (countOccurrence(it["synonyms"].array, manga.title) <= 0) {
                        return@find true
                    }
                    false
                }
                    ?: return@map listOf<SMangaImpl>()
                val recommendations = result["recommendations"].obj
                val edges = recommendations["edges"].array
                edges.map {
                    val rec = it["node"]["mediaRecommendation"].obj
                    Log.d("ANILIST RECOMMEND", "${rec["title"].obj["romaji"].string}")
                    SMangaImpl().apply {
                        this.title = rec["title"].obj["romaji"].nullString
                            ?: rec["title"].obj["english"].nullString
                            ?: rec["title"].obj["native"].string
                        this.thumbnail_url = rec["coverImage"].obj["large"].string
                        this.initialized = true
                        this.url = rec["siteUrl"].string
                    }
                }
            }
    }

    override fun requestNext(): Observable<MangasPage> {
        if (smart) {
            preferredApi = if (manga.mangaType() != MangaType.TYPE_MANGA) API.ANILIST else preferredApi
            Log.d("SMART RECOMMEND", preferredApi.toString())
        }

        val apiList = API.values().toMutableList()
        apiList.removeAt(apiList.indexOf(preferredApi))
        apiList.add(0, preferredApi)

        var recommendations: Observable<List<SMangaImpl>>? = null
        for (api in apiList) {
            val currentRecommendations = when (api) {
                API.MYANIMELIST -> myAnimeList()
                API.ANILIST -> anilist()
            }

            if (currentRecommendations != null &&
                currentRecommendations.toBlocking().first().isNotEmpty()
            ) {
                recommendations = currentRecommendations
                break
            }
        }

        if (recommendations == null) {
            throw Exception("No recommendations found")
        }

        return recommendations.map {
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
