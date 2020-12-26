package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.PervEdenSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.PervEdenDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import rx.Observable

class PervEden(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<PervEdenSearchMetadata, Document>,
    UrlImportableSource {
    override val metaClass = PervEdenSearchMetadata::class
    override val lang = delegate.lang

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .flatMap {
                parseToManga(manga, it.asJsoup()).andThen(Observable.just(manga))
            }
    }

    override fun parseIntoMetadata(metadata: PervEdenSearchMetadata, input: Document) {
        with(metadata) {
            url = input.location().toUri().path

            pvId = PervEdenSearchMetadata.pvIdFromUrl(url!!)

            lang = this@PervEden.lang

            title = input.getElementsByClass("manga-title").first()?.text()

            thumbnailUrl = "http:" + input.getElementsByClass("mangaImage2").first()?.child(0)?.attr("src")

            val rightBoxElement = input.select(".rightBox:not(.info)").first()

            val newAltTitles = mutableListOf<String>()
            tags.clear()
            var inStatus: String? = null
            rightBoxElement.childNodes().forEach {
                if (it is Element && it.tagName().toLowerCase() == "h4") {
                    inStatus = it.text().trim()
                } else {
                    when (inStatus) {
                        "Alternative name(s)" -> {
                            if (it is TextNode) {
                                val text = it.text().trim()
                                if (!text.isBlank()) {
                                    newAltTitles += text
                                }
                            }
                        }
                        "Artist" -> {
                            if (it is Element && it.tagName() == "a") {
                                artist = it.text()
                                tags += RaisedTag(
                                    "artist",
                                    it.text().toLowerCase(),
                                    RaisedSearchMetadata.TAG_TYPE_VIRTUAL
                                )
                            }
                        }
                        "Genres" -> {
                            if (it is Element && it.tagName() == "a") {
                                tags += RaisedTag(
                                    null,
                                    it.text().toLowerCase(),
                                    PervEdenSearchMetadata.TAG_TYPE_DEFAULT
                                )
                            }
                        }
                        "Type" -> {
                            if (it is TextNode) {
                                val text = it.text().trim()
                                if (!text.isBlank()) {
                                    genre = text
                                }
                            }
                        }
                        "Status" -> {
                            if (it is TextNode) {
                                val text = it.text().trim()
                                if (!text.isBlank()) {
                                    status = text
                                }
                            }
                        }
                    }
                }
            }

            altTitles = newAltTitles

            rating = input.getElementById("rating-score")?.attr("value")?.toFloat()
        }
    }

    override val matchingHosts = listOf("www.perveden.com")

    override fun matchesUri(uri: Uri): Boolean {
        return super.matchesUri(uri) && uri.pathSegments.firstOrNull()?.toLowerCase() == when (lang) {
            "en" -> "en-manga"
            "it" -> "it-manga"
            else -> false
        }
    }

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val newUri = "http://www.perveden.com/".toUri().buildUpon()
        uri.pathSegments.take(3).forEach {
            newUri.appendPath(it)
        }
        return newUri.toString()
    }

    override fun getDescriptionAdapter(controller: MangaController): PervEdenDescriptionAdapter {
        return PervEdenDescriptionAdapter(controller)
    }
}
