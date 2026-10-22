package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.model.copy
import kotlinx.serialization.Serializable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class HentaiNexusSearchMetadata : RaisedSearchMetadata() {
    var hnId: Int? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    var summary: String? = null

    var publishedDate: Long? = null

    var pageCount: Int? = null

    var favorites: Int? = null

    var pagePreviewUrls: List<String> = emptyList()

    override fun createMangaInfo(manga: SManga): SManga {
        val artist = tags.ofNamespace(NAMESPACE_ARTIST).joinToString { it.name }.ifEmpty { null }
        val author = tags.ofNamespace(NAMESPACE_AUTHOR).joinToString { it.name }.ifEmpty { artist }

        return manga.copy(
            url = hnId?.let { "/view/$it" } ?: manga.url,
            thumbnail_url = thumbnailUrl ?: manga.thumbnail_url,
            title = title ?: manga.title,
            artist = artist,
            author = author,
            genre = tagsToGenreString(),
            status = SManga.COMPLETED,
            // Blank rather than null so the description written by the extension is replaced
            description = summary.orEmpty(),
        ).also {
            it.update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(hnId) { stringResource(SYMR.strings.id) },
                getItem(title) { stringResource(MR.strings.title) },
                getItem(thumbnailUrl) { stringResource(SYMR.strings.thumbnail_url) },
                getItem(
                    publishedDate,
                    {
                        DateTimeFormatter.ISO_LOCAL_DATE
                            .withZone(ZoneOffset.UTC)
                            .format(Instant.ofEpochMilli(it))
                    },
                ) {
                    stringResource(SYMR.strings.date_posted)
                },
                getItem(pageCount) { stringResource(SYMR.strings.page_count) },
                getItem(favorites) { stringResource(SYMR.strings.total_favorites) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val NAMESPACE_ARTIST = "artist"
        const val NAMESPACE_AUTHOR = "author"
        const val NAMESPACE_CIRCLE = "circle"
        const val NAMESPACE_EVENT = "event"
        const val NAMESPACE_MAGAZINE = "magazine"
        const val NAMESPACE_PUBLISHER = "publisher"
    }
}
