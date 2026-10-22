package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.PagePreviewInfo
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.GalleryAdder
import exh.metadata.metadata.NineHentaiSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.urlImportFetchSearchMangaSuspend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import tachiyomi.core.common.util.system.logcat
import java.util.Calendar

@AssistedInject
class NineHentai(
    @Assisted delegate: HttpSource,
    val context: Context,
    override val getMangaId: MetadataSource.GetMangaId,
    override val insertFlatMetadata: MetadataSource.InsertFlatMetadata,
    override val getFlatMetadataById: MetadataSource.GetFlatMetadataById,
    private val galleryAdder: GalleryAdder,
) :
    DelegatedHttpSource(delegate),
    MetadataSource<NineHentaiSearchMetadata, NineHentai.GalleryInput>,
    UrlImportableSource,
    NamespaceSource,
    PagePreviewSource {
    @AssistedFactory
    fun interface Factory {
        fun create(delegate: HttpSource): NineHentai
    }

    override val metaClass = NineHentaiSearchMetadata::class
    override fun newMetaInstance() = NineHentaiSearchMetadata()
    override val lang = "en"

    class GalleryInput(val document: Document, val book: Book?)

    @Serializable
    class BookResponse(val results: Book)

    @Serializable
    class Book(
        val id: Int,
        val title: String? = null,
        @SerialName("alt_title") val altTitle: String? = null,
        @SerialName("total_page") val totalPage: Int? = null,
        @SerialName("total_favorite") val totalFavorite: Int? = null,
        @SerialName("total_view") val totalView: Int? = null,
        @SerialName("total_download") val totalDownload: Int? = null,
    )

    // Support direct URL importing
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return urlImportFetchSearchMangaSuspend(context, galleryAdder, query) {
            super<DelegatedHttpSource>.getSearchManga(page, query, filters)
        }
    }

    // The extension parses the gallery page on every call regardless of the flags, so the details
    // are only taken from our own parse to keep the metadata and the manga in sync
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val newChapters = if (fetchChapters) {
            super<DelegatedHttpSource>.getMangaUpdate(manga, chapters, fetchDetails = false, fetchChapters = true)
                .chapters
        } else {
            chapters
        }
        val newManga = if (fetchDetails) {
            parseToManga(manga, fetchGallery(manga))
        } else {
            manga
        }
        return SMangaUpdate(newManga, newChapters)
    }

    private suspend fun fetchGallery(manga: SManga): GalleryInput {
        val document = client.newCall(GET(getMangaUrl(manga), headers)).awaitSuccess().asJsoup()
        // The stats only exist in the API, the gallery is still usable without them
        val book = getGalleryId(manga.url)?.let { id ->
            runCatching {
                client.newCall(
                    POST(
                        "$baseUrl/api/getBookByID",
                        headers,
                        """{"id":$id}""".toRequestBody(JSON_MEDIA_TYPE),
                    ),
                ).awaitSuccess().let { with(jsonParser) { it.parseAs<BookResponse>() } }.results
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Failed to fetch 9Hentai stats for $id" }
            }.getOrNull()
        }
        return GalleryInput(document, book)
    }

    override suspend fun parseIntoMetadata(metadata: NineHentaiSearchMetadata, input: GalleryInput) {
        val info = input.document.selectFirst("#info")!!

        with(metadata) {
            nhId = getGalleryId(input.document.location()) ?: input.book?.id

            imageBaseUrl = "https://i.${baseUrl.toHttpUrl().host}/images"

            title = info.selectFirst("h1")!!.text()
            altTitle = info.selectFirst("h2")?.text()?.ifBlank { null } ?: input.book?.altTitle

            pageCount = info.select("> div")
                .firstOrNull { it.ownText().endsWith("pages") }
                ?.ownText()
                ?.substringBefore(" ")
                ?.toIntOrNull()
                ?: input.book?.totalPage

            uploadDate = info.selectFirst("div time")?.text()?.let(::parseRelativeDate)

            favorites = input.book?.totalFavorite
            views = input.book?.totalView
            downloads = input.book?.totalDownload

            tags.clear()
            info.select("#tags .tag-container").forEach { container ->
                val namespace = container.ownText().trim().removeSuffix(":").lowercase()
                val type = if (namespace in VIRTUAL_NAMESPACES) {
                    RaisedSearchMetadata.TAG_TYPE_VIRTUAL
                } else {
                    NineHentaiSearchMetadata.TAG_TYPE_DEFAULT
                }
                container.select("a.tag").mapTo(tags) {
                    RaisedTag(namespace, it.text(), type)
                }
            }
        }
    }

    private fun getGalleryId(url: String): Int? {
        return GALLERY_ID_REGEX.find(url)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseRelativeDate(date: String): Long? {
        val split = date.split(" ")
        val value = split.getOrNull(0)?.toIntOrNull() ?: return null
        val field = when (split.getOrNull(1)?.removeSuffix("s")) {
            "sec", "second" -> Calendar.SECOND
            "min", "minute" -> Calendar.MINUTE
            "hour" -> Calendar.HOUR_OF_DAY
            "day" -> Calendar.DATE
            "week" -> Calendar.WEEK_OF_YEAR
            "month" -> Calendar.MONTH
            "year" -> Calendar.YEAR
            else -> return null
        }
        return Calendar.getInstance().apply { add(field, -value) }.timeInMillis
    }

    override val matchingHosts = listOf(
        "9hentai.so",
        "www.9hentai.so",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        return getGalleryId(uri.toString())?.let { "/g/$it" }
    }

    override suspend fun getPagePreviewList(manga: SManga, chapters: List<SChapter>, page: Int): PagePreviewPage {
        val metadata = fetchOrLoadMetadata(manga.id()) { fetchGallery(manga) }
        return PagePreviewPage(
            page,
            (1..(metadata.pageCount ?: 0)).mapNotNull { index ->
                metadata.getPreviewUrl(index)?.let { PagePreviewInfo(index, imageUrl = it) }
            },
            false,
            1,
        )
    }

    override suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response {
        return client.newCachelessCallWithProgress(
            if (cacheControl != null) {
                GET(page.imageUrl, cache = cacheControl)
            } else {
                GET(page.imageUrl)
            },
            page,
        ).awaitSuccess()
    }

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val GALLERY_ID_REGEX = Regex("""/g/(\d+)""")
        private val VIRTUAL_NAMESPACES = listOf(
            NineHentaiSearchMetadata.NAMESPACE_CATEGORY,
            NineHentaiSearchMetadata.NAMESPACE_LANGUAGE,
        )
    }
}
