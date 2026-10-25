package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.model.copy
import exh.metadata.MetadataUtil
import exh.util.nullIfEmpty
import kotlinx.serialization.Serializable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * SY-specific file
 */
@Serializable
class HitomiSearchMetadata : RaisedSearchMetadata() {
    var url get() = hlId?.let { urlFromHlId(it) }
        set(a) {
            a?.let {
                hlId = hlIdFromUrl(a)
            }
        }

    var hlId: String? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    var artists: List<String> = emptyList()

    var genre: String? = null

    var language: String? = null

    var uploadDate: Long? = null

    var pages: Int? = null

    var pageHashes: List<String> = emptyList()

    override fun createMangaInfo(manga: SManga): SManga {
        val cover = thumbnailUrl

        val title = title

        // Copy tags -> genres
        val genres = tagsToGenreString()

        val artist = artists.joinToString()

        return manga.copy(
            thumbnail_url = cover ?: manga.thumbnail_url,
            title = title ?: manga.title,
            genre = genres,
            artist = artist,
            status = SManga.COMPLETED,
            description = "",
            updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(hlId) { stringResource(SYMR.strings.id) },
                getItem(title) { stringResource(MR.strings.title) },
                getItem(thumbnailUrl) { stringResource(SYMR.strings.thumbnail_url) },
                getItem(artists.nullIfEmpty(), { it.joinToString() }) { stringResource(SYMR.strings.artist) },
                getItem(genre) { stringResource(SYMR.strings.genre) },
                getItem(language) { stringResource(SYMR.strings.language) },
                getItem(
                    uploadDate,
                    {
                        MetadataUtil.EX_DATE_FORMAT.format(
                            ZonedDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()),
                        )
                    },
                ) {
                    stringResource(SYMR.strings.date_posted)
                },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val BASE_URL = "https://hitomi.la"

        fun hlIdFromUrl(url: String) =
            url.split('/').last().split('-').last().substringBeforeLast('.')

        fun urlFromHlId(id: String) =
            "$BASE_URL/galleries/$id.html"
    }
}
