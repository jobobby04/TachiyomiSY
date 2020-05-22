package eu.kanade.tachiyomi.ui.browse.source.browse

import android.util.Log
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.nullArray
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
import exh.util.MangaType
import exh.util.mangaType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.Observable
import timber.log.Timber

// TODO api classes

abstract class API(_endpoint: String) {
    var endpoint: String = _endpoint
    val client = OkHttpClient.Builder().build()
    val scope = CoroutineScope(Job() + Dispatchers.Default)

    abstract fun getRecsBySearch(
        search: String,
        callback: (onResolve: List<SMangaImpl>?, onReject: Throwable?) -> Unit
    )
}

class MyAnimeList() : API("https://api.jikan.moe/v3/") {
    fun getRecsById(
        id: String,
        callback: (resolve: List<SMangaImpl>?, reject: Throwable?) -> Unit
    ) {
        val httpUrl =
            endpoint.toHttpUrlOrNull()
        if (httpUrl == null) {
            callback.invoke(null, Exception("Could not convert endpoint url"))
            return
        }
        val urlBuilder = httpUrl.newBuilder()
        urlBuilder.addPathSegment("manga")
        urlBuilder.addPathSegment(id)
        urlBuilder.addPathSegment("recommendations")
        val url = urlBuilder.build().toUrl()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val handler = CoroutineExceptionHandler { _, exception ->
            callback.invoke(null, exception)
        }

        scope.launch(handler) {
            val response = client.newCall(request).await()
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) {
                throw Exception("Null Response")
            }
            val data = JsonParser.parseString(body).obj
            val recommendations = data["recommendations"].nullArray
                ?: throw Exception("Unexpected response")
            val recs = recommendations.map { rec ->
                Log.d("MYANIMELIST RECOMMEND", "${rec["title"].string}")
                SMangaImpl().apply {
                    this.title = rec["title"].string
                    this.thumbnail_url = rec["image_url"].string
                    this.initialized = true
                    this.url = rec["url"].string
                }
            }
            callback.invoke(recs, null)
        }
    }

    override fun getRecsBySearch(
        search: String,
        callback: (recs: List<SMangaImpl>?, error: Throwable?) -> Unit
    ) {
        val httpUrl =
            endpoint.toHttpUrlOrNull()
        if (httpUrl == null) {
            callback.invoke(null, Exception("Could not convert endpoint url"))
            return
        }
        val urlBuilder = httpUrl.newBuilder()
        urlBuilder.addPathSegment("search")
        urlBuilder.addPathSegment("manga")
        urlBuilder.addQueryParameter("q", search)
        val url = urlBuilder.build().toUrl()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val handler = CoroutineExceptionHandler { _, exception ->
            callback.invoke(null, exception)
        }

        scope.launch(handler) {
            val response = client.newCall(request).await()
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) {
                throw Exception("Null Response")
            }
            val data = JsonParser.parseString(body).obj
            val results = data["results"].nullArray ?: throw Exception("Unexpected response")
            if (results.size() <= 0) {
                throw Exception("'$search' not found")
            }
            val result = results.first().obj
            val id = result["mal_id"].string
            getRecsById(id, callback)
        }
    }
}

class Anilist() : API("https://graphql.anilist.co/") {
    private fun countOccurrence(arr: JsonArray, search: String): Int {
        return arr.count {
            val synonym = it.string
            synonym.contains(search, true)
        }
    }

    private fun languageContains(obj: JsonObject, language: String, search: String): Boolean {
        return obj["title"].obj[language].nullString?.contains(search, true) == true
    }

    private fun getTitle(obj: JsonObject): String {
        return obj["title"].obj["romaji"].nullString
            ?: obj["title"].obj["english"].nullString
            ?: obj["title"].obj["native"].string
    }

    override fun getRecsBySearch(
        search: String,
        callback: (onResolve: List<SMangaImpl>?, onReject: Throwable?) -> Unit
    ) {
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
            .url(endpoint)
            .post(payloadBody)
            .build()

        val handler = CoroutineExceptionHandler { _, exception ->
            callback.invoke(null, exception)
        }

        scope.launch(handler) {
            val response = client.newCall(request).await()
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) {
                throw Exception("Null Response")
            }
            val data = JsonParser.parseString(body).obj["data"].nullObj
                ?: throw Exception("Unexpected response")
            val page = data["Page"].nullObj
            val media = page?.get("media").nullArray
            val result = media?.sortedWith(
                compareBy(
                    { languageContains(it.obj, "romaji", search) },
                    { languageContains(it.obj, "english", search) },
                    { languageContains(it.obj, "native", search) },
                    { countOccurrence(it.obj["synonyms"].array, search) > 0 }
                )
            )?.last().nullObj
            val recommendations =
                result?.get("recommendations")?.obj?.get("edges").nullArray
                    ?: throw Exception("Couldn't find any recommendations")
            val recs = recommendations.map {
                val rec = it["node"]["mediaRecommendation"].obj
                Log.d("ANILIST RECOMMEND", "${rec["title"].obj["romaji"].string}")
                SMangaImpl().apply {
                    this.title = getTitle(rec)
                    this.thumbnail_url = rec["coverImage"].obj["large"].string
                    this.initialized = true
                    this.url = rec["siteUrl"].string
                }
            }
            callback.invoke(recs, null)
        }
    }
}

open class RecommendsPager(
    val manga: Manga,
    val smart: Boolean = true,
    var preferredApi: API = API.MYANIMELIST
) : Pager() {
    private val apiList = API_MAP.toMutableMap()
    private var currentApi: API? = null

    private fun handleSuccess(recs: List<SMangaImpl>) {
        if (recs.isEmpty()) {
            apiList.remove(currentApi)
            val list = apiList.toList()
            currentApi = if (list.isEmpty()) {
                null
            } else {
                apiList.toList().first().first
            }

            if (currentApi != null) {
                getRecs(currentApi!!)
            } else {
                Timber.e("Couldn't find recommendations")
                onPageReceived(MangasPage(recs, false))
            }
        } else {
            onPageReceived(MangasPage(recs, false))
        }
    }

    private fun handleError(error: Throwable) {
        Timber.e(error)
        handleSuccess(listOf()) // tmp workaround until errors can be displayed in app
    }

    private fun getRecs(api: API) {
        Log.d("USED RECOMMEND", api.toString())
        apiList[api]?.getRecsBySearch(manga.title) { recs, error ->
            if (error != null) {
                handleError(error)
            }
            if (recs != null) {
                handleSuccess(recs)
            }
        }
    }

    override fun requestNext(): Observable<MangasPage> {
        if (smart) {
            preferredApi =
                if (manga.mangaType() != MangaType.TYPE_MANGA) API.ANILIST else preferredApi
            Log.d("SMART RECOMMEND", preferredApi.toString())
        }
        currentApi = preferredApi

        getRecs(currentApi!!)

        return Observable.just(MangasPage(listOf(), false))
    }

    companion object {
        val API_MAP = mapOf(
            API.MYANIMELIST to MyAnimeList(),
            API.ANILIST to Anilist()
        )

        enum class API { MYANIMELIST, ANILIST }
    }
}
