package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.GalleryAdder
import exh.metadata.metadata.PururinSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.dropBlank
import exh.util.trimAll
import exh.util.urlImportFetchSearchManga
import exh.util.urlImportFetchSearchMangaSuspend
import org.jsoup.nodes.Document
import rx.Observable
import tachiyomi.core.common.util.lang.runAsObservable

/**
 * SY-specific file
 */
@AssistedInject
class Pururin(
    @Assisted delegate: HttpSource,
    val context: Context,
    override val getMangaId: MetadataSource.GetMangaId,
    override val insertFlatMetadata: MetadataSource.InsertFlatMetadata,
    override val getFlatMetadataById: MetadataSource.GetFlatMetadataById,
    private val galleryAdder: GalleryAdder,
) :
    DelegatedHttpSource(delegate),
    MetadataSource<PururinSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource {
    @AssistedFactory
    fun interface Factory {
        fun create(delegate: HttpSource): Pururin
    }

    /**
     * The class of the metadata used by this source
     */
    override val metaClass = PururinSearchMetadata::class
    override fun newMetaInstance() = PururinSearchMetadata()

    // Support direct URL importing
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val trimmedIdQuery = query.trim().removePrefix("id:")
        val newQuery = if ((trimmedIdQuery.toIntOrNull() ?: -1) >= 0) {
            "$baseUrl/gallery/$trimmedIdQuery/-"
        } else {
            query
        }

        return urlImportFetchSearchManga(context, galleryAdder, newQuery) {
            @Suppress("DEPRECATION")
            super<DelegatedHttpSource>.fetchSearchManga(page, query, filters)
        }
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmedIdQuery = query.trim().removePrefix("id:")
        val newQuery = if ((trimmedIdQuery.toIntOrNull() ?: -1) >= 0) {
            "$baseUrl/gallery/$trimmedIdQuery/-"
        } else {
            query
        }
        return urlImportFetchSearchMangaSuspend(context, galleryAdder, newQuery) {
            super<DelegatedHttpSource>.getSearchManga(page, query, filters)
        }
    }

    override val overridesMangaDetails = true

    @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getMangaDetails"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return runAsObservable {
            val response = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
            parseToManga(manga, response.asJsoup())
        }
    }

    override suspend fun parseIntoMetadata(metadata: PururinSearchMetadata, input: Document) {
        val parsedSelfLink = input.selectFirst("meta[property=og:url]")!!.attr("content").toUri().pathSegments

        with(metadata) {
            prId = parsedSelfLink[parsedSelfLink.lastIndex - 1].toInt()
            prShortLink = parsedSelfLink.last()

            val contentWrapper = input.selectFirst(".content-wrapper")
            title = contentWrapper!!.selectFirst(".title h1")!!.text()
            altTitle = contentWrapper.selectFirst(".alt-title")?.text()

            thumbnailUrl = input.selectFirst(".cover-wrapper img")!!.absUrl("src")

            tags.clear()
            contentWrapper.select(".table-info > tbody > tr").forEach { ele ->
                val key = ele.child(0).text().lowercase()
                val value = ele.child(1)
                when (key) {
                    "pages" -> {
                        val split = value.text().split("(").trimAll().dropBlank()

                        pages = split.first().toIntOrNull()
                        fileSize = split.last().removeSuffix(")").trim()
                    }
                    "ratings" -> {
                        ratingCount = value.selectFirst("[itemprop=ratingCount]")!!.attr("content").toIntOrNull()
                        averageRating = value.selectFirst("[itemprop=ratingValue]")!!.attr("content").toDoubleOrNull()
                    }
                    "uploader" -> {
                        uploaderDisp = value.text()
                        uploader = value.selectFirst("a")!!.attr("href").toUri().lastPathSegment
                    }
                    else -> {
                        value.select("a").forEach { link ->
                            val searchUrl = link.attr("href").toUri()
                            val namespace = searchUrl.pathSegments[searchUrl.pathSegments.lastIndex - 2]
                            tags += RaisedTag(
                                namespace,
                                searchUrl.lastPathSegment!!.substringBefore("."),
                                if (namespace != PururinSearchMetadata.TAG_NAMESPACE_CATEGORY) {
                                    PururinSearchMetadata.TAG_TYPE_DEFAULT
                                } else {
                                    RaisedSearchMetadata.TAG_TYPE_VIRTUAL
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override val matchingHosts = listOf(
        "pururin.me",
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String {
        return "${PururinSearchMetadata.BASE_URL}/gallery/${uri.pathSegments.getOrNull(1)}/${uri.lastPathSegment}"
    }
}
