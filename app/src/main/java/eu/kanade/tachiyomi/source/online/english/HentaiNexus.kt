package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
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
import exh.metadata.metadata.HentaiNexusSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.urlImportFetchSearchMangaSuspend
import okhttp3.CacheControl
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@AssistedInject
class HentaiNexus(
    @Assisted delegate: HttpSource,
    val context: Context,
    override val getMangaId: MetadataSource.GetMangaId,
    override val insertFlatMetadata: MetadataSource.InsertFlatMetadata,
    override val getFlatMetadataById: MetadataSource.GetFlatMetadataById,
    private val galleryAdder: GalleryAdder,
) :
    DelegatedHttpSource(delegate),
    MetadataSource<HentaiNexusSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource,
    PagePreviewSource {
    @AssistedFactory
    fun interface Factory {
        fun create(delegate: HttpSource): HentaiNexus
    }

    override val metaClass = HentaiNexusSearchMetadata::class
    override fun newMetaInstance() = HentaiNexusSearchMetadata()
    override val lang = "en"

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
            parseToManga(manga, fetchGalleryPage(manga))
        } else {
            manga
        }
        return SMangaUpdate(newManga, newChapters)
    }

    private suspend fun fetchGalleryPage(manga: SManga): Document {
        return client.newCall(GET(getMangaUrl(manga), headers)).awaitSuccess().asJsoup()
    }

    override suspend fun parseIntoMetadata(metadata: HentaiNexusSearchMetadata, input: Document) {
        val details = input.selectFirst(".view-page-details")!!

        fun cell(name: String) = details.select("td.viewcolumn")
            .firstOrNull { it.text() == name }
            ?.nextElementSibling()

        with(metadata) {
            hnId = input.selectFirst("meta[property=og:url]")?.attr("content")
                ?.let { Uri.parse(it).lastPathSegment?.toIntOrNull() }
                ?: Uri.parse(input.location()).lastPathSegment?.toIntOrNull()

            title = input.selectFirst("h1.title")!!.text()

            thumbnailUrl = input.selectFirst("figure.image img")?.absUrl("src")

            summary = cell("Description")?.text()?.ifBlank { null }

            publishedDate = cell("Published")?.text()?.let {
                runCatching {
                    LocalDate.parse(it, DATE_FORMAT).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                }.getOrNull()
            }

            pageCount = cell("Pages")?.text()?.toIntOrNull()

            favorites = cell("Favorites")?.text()?.replace(",", "")?.toIntOrNull()

            pagePreviewUrls = input.select("a[href^=/read/] .card-image img").map { it.absUrl("src") }

            tags.clear()
            NAMESPACES.forEach { namespace ->
                cell(namespace)?.select("a")?.mapTo(tags) {
                    RaisedTag(
                        namespace.lowercase(),
                        it.ownText().trim(),
                        HentaiNexusSearchMetadata.TAG_TYPE_DEFAULT,
                    )
                }
            }
            cell("Tags")?.select("span.tag a")?.mapTo(tags) {
                RaisedTag(
                    "tag",
                    it.text().replace(TAG_COUNT_REGEX, ""),
                    HentaiNexusSearchMetadata.TAG_TYPE_DEFAULT,
                )
            }
        }
    }

    override val matchingHosts = listOf(
        "hentainexus.com",
        "www.hentainexus.com",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val id = uri.pathSegments.takeIf { it.firstOrNull() in listOf("view", "read") }
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        return "/view/$id"
    }

    override suspend fun getPagePreviewList(manga: SManga, chapters: List<SChapter>, page: Int): PagePreviewPage {
        val metadata = fetchOrLoadMetadata(manga.id()) { fetchGalleryPage(manga) }
        return PagePreviewPage(
            page,
            metadata.pagePreviewUrls.mapIndexed { index, url ->
                PagePreviewInfo(index + 1, imageUrl = url)
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
        private val NAMESPACES = listOf("Artist", "Author", "Circle", "Event", "Magazine", "Parody", "Publisher")
        private val TAG_COUNT_REGEX = Regex("""\s*\([\d,]+\)$""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)
    }
}
