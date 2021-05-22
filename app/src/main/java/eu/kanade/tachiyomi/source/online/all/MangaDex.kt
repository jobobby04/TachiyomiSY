package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.BrowseSourceFilterHeader
import eu.kanade.tachiyomi.source.online.FollowsSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.RandomMangaSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.md.MangaDexFabHeaderAdapter
import exh.md.handlers.ApiChapterParser
import exh.md.handlers.ApiMangaParser
import exh.md.handlers.FollowsHandler
import exh.md.handlers.MangaHandler
import exh.md.handlers.MangaPlusHandler
import exh.md.handlers.SimilarHandler
import exh.md.network.MangaDexLoginHelper
import exh.md.network.NoSessionException
import exh.md.network.TokenAuthenticator
import exh.md.utils.FollowStatus
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.MangaDexDescriptionAdapter
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.reflect.KClass

@Suppress("OverridingDeprecatedMember")
class MangaDex(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<MangaDexSearchMetadata, Response>,
    // UrlImportableSource,
    FollowsSource,
    LoginSource,
    BrowseSourceFilterHeader,
    RandomMangaSource,
    NamespaceSource {
    override val lang: String = delegate.lang

    private val mdLang by lazy {
        MdLang.fromExt(lang) ?: MdLang.ENGLISH
    }

    // override val matchingHosts: List<String> = listOf("mangadex.org", "www.mangadex.org")

    val preferences = Injekt.get<PreferencesHelper>()
    val mdList: MdList = Injekt.get<TrackManager>().mdList

    /*private val sourcePreferences: SharedPreferences by lazy {
        context.getSharedPreferences("source_$id", 0x0000)
    }*/

    private val loginHelper = MangaDexLoginHelper(networkHttpClient, preferences, mdList)

    override val baseHttpClient: OkHttpClient = super.client.newBuilder()
        .authenticator(
            TokenAuthenticator(loginHelper)
        )
        .build()

    private fun useLowQualityThumbnail() = false // sourcePreferences.getInt(SHOW_THUMBNAIL_PREF, 0) == LOW_QUALITY

    private val apiMangaParser by lazy {
        ApiMangaParser(baseHttpClient, mdLang.lang)
    }
    private val apiChapterParser by lazy {
        ApiChapterParser()
    }
    private val followsHandler by lazy {
        FollowsHandler(baseHttpClient, headers, preferences, mdLang.lang, mdList)
    }
    private val mangaHandler by lazy {
        MangaHandler(baseHttpClient, headers, mdLang.lang, apiMangaParser, followsHandler)
    }
    private val similarHandler by lazy {
        SimilarHandler(baseHttpClient, mdLang.lang)
    }
    private val mangaPlusHandler by lazy {
        MangaPlusHandler(network.client)
    }

    /*override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            importIdToMdId(query) {
                super.fetchSearchManga(page, query, filters)
            }
        }*/

    /*override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        return if (lcFirstPathSegment == "title" || lcFirstPathSegment == "manga") {
            "/manga/" + uri.pathSegments[1]
        } else {
            null
        }
    }

    override fun mapUrlToChapterUrl(uri: Uri): String? {
        if (!uri.pathSegments.firstOrNull().equals("chapter", true)) return null
        val id = uri.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return MdUtil.oldApiChapter + id
    }

    override suspend fun mapChapterUrlToMangaUrl(uri: Uri): String? {
        val id = uri.pathSegments.getOrNull(2) ?: return null
        val mangaId = MangaHandler(baseHttpClient, headers, mdLang).getMangaIdFromChapterId(id)
        return MdUtil.mapMdIdToMangaUrl(mangaId)
    }*/

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return mangaHandler.fetchMangaDetailsObservable(manga, id, preferences.mangaDexForceLatestCovers().get())
    }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        return mangaHandler.getMangaDetails(manga, id, preferences.mangaDexForceLatestCovers().get())
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return mangaHandler.fetchChapterListObservable(manga)
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return mangaHandler.getChapterList(manga)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return if (chapter.scanlator == "MangaPlus") {
            mangaPlusHandler.client.newCall(mangaPlusPageListRequest(chapter))
                .asObservableSuccess()
                .map { response ->
                    val chapterId = apiChapterParser.externalParse(response)
                    mangaPlusHandler.fetchPageList(chapterId)
                }
        } else super.fetchPageList(chapter)
    }

    private fun mangaPlusPageListRequest(chapter: SChapter): Request {
        return GET(MdUtil.chapterUrl + MdUtil.getChapterId(chapter.url), headers, CacheControl.FORCE_NETWORK)
    }

    override fun fetchImage(page: Page): Observable<Response> {
        return if (page.imageUrl?.contains("mangaplus", true) == true) {
            mangaPlusHandler.client.newCall(GET(page.imageUrl!!, headers))
                .asObservableSuccess()
        } else super.fetchImage(page)
    }

    override val metaClass: KClass<MangaDexSearchMetadata> = MangaDexSearchMetadata::class

    override fun getDescriptionAdapter(controller: MangaController): MangaDexDescriptionAdapter {
        return MangaDexDescriptionAdapter(controller)
    }

    override suspend fun parseIntoMetadata(metadata: MangaDexSearchMetadata, input: Response) {
        apiMangaParser.parseIntoMetadata(metadata, input, emptyList())
    }

    override suspend fun fetchFollows(page: Int): MangasPage {
        return followsHandler.fetchFollows(page)
    }

    override val requiresLogin: Boolean = false

    override val twoFactorAuth = LoginSource.AuthSupport.NOT_SUPPORTED

    override fun isLogged(): Boolean {
        return mdList.isLogged
    }

    override fun getUsername(): String {
        return mdList.getUsername()
    }

    override fun getPassword(): String {
        return mdList.getPassword()
    }

    override suspend fun login(
        username: String,
        password: String,
        twoFactorCode: String?
    ): Boolean {
        val result = loginHelper.login(username, password)
        return if (result is MangaDexLoginHelper.LoginResult.Success) {
            MdUtil.updateLoginToken(result.token, preferences, mdList)
            mdList.saveCredentials(username, password)
            true
        } else false
    }

    override suspend fun logout(): Boolean {
        val result = try {
            loginHelper.logout(MdUtil.getAuthHeaders(Headers.Builder().build(), preferences, mdList))
        } catch (e: NoSessionException) {
            true
        } catch (e: Exception) {
            e.message?.equals("HTTP error 405") ?: false
        }

        return if (result) {
            mdList.logout()
            true
        } else false
    }

    override suspend fun fetchAllFollows(): List<Pair<SManga, MangaDexSearchMetadata>> {
        return followsHandler.fetchAllFollows()
    }

    suspend fun updateReadingProgress(track: Track): Boolean {
        return followsHandler.updateReadingProgress(track)
    }

    suspend fun updateRating(track: Track): Boolean {
        return followsHandler.updateRating(track)
    }

    override suspend fun fetchTrackingInfo(url: String): Track {
        if (!isLogged()) {
            throw Exception("Not Logged in")
        }
        return followsHandler.fetchTrackingInfo(url)
    }

    suspend fun getTrackingAndMangaInfo(track: Track): Pair<Track, MangaDexSearchMetadata?> {
        return mangaHandler.getTrackingInfo(track, mdList)
    }

    override suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean {
        return followsHandler.updateFollowStatus(mangaID, followStatus)
    }

    override fun getFilterHeader(controller: BaseController<*>, onClick: () -> Unit): MangaDexFabHeaderAdapter {
        return MangaDexFabHeaderAdapter(controller, this, onClick)
    }

    override suspend fun fetchRandomMangaUrl(): String {
        return mangaHandler.fetchRandomMangaId()
    }

    suspend fun getMangaSimilar(manga: MangaInfo): MangasPage {
        return similarHandler.getSimilar(manga)
    }

    /*private fun importIdToMdId(query: String, fail: () -> Observable<MangasPage>): Observable<MangasPage> =
        when {
            query.toIntOrNull() != null -> {
                runAsObservable({
                    GalleryAdder().addGallery(context, MdUtil.baseUrl + MdUtil.mapMdIdToMangaUrl(query.toInt()), false, this@MangaDex)
                })
                    .map { res ->
                        MangasPage(
                            if (res is GalleryAddEvent.Success) {
                                listOf(res.manga)
                            } else {
                                emptyList()
                            },
                            false
                        )
                    }
            }
            else -> fail()
        }*/

    /*companion object {
        private const val REMEMBER_ME = "mangadex_rememberme_token"
        private const val SHOW_THUMBNAIL_PREF = "showThumbnailDefault"
        private const val LOW_QUALITY = 1
    }*/
}
