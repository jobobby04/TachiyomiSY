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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SY-specific file
 */
@Serializable
class NineHentaiSearchMetadata : RaisedSearchMetadata() {
    var nhId: Int? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)
    var altTitle by titleDelegate(TITLE_TYPE_ALT)

    var imageBaseUrl: String? = null

    /** Approximate, the site only shows how long ago the gallery was uploaded. */
    var uploadDate: Long? = null

    var pageCount: Int? = null

    var favorites: Int? = null

    var views: Int? = null

    var downloads: Int? = null

    val thumbnailUrl get() = imageBaseUrl?.let { base -> nhId?.let { "$base/$it/cover.jpg" } }

    fun getPreviewUrl(page: Int) = imageBaseUrl?.let { base -> nhId?.let { "$base/$it/preview/${page}t.jpg" } }

    override fun createMangaInfo(manga: SManga): SManga {
        val artist = tags.ofNamespace(NAMESPACE_ARTIST).joinToString { it.name }.ifEmpty { null }
        val group = tags.ofNamespace(NAMESPACE_GROUP).joinToString { it.name }.ifEmpty { null }

        return manga.copy(
            url = nhId?.let { "/g/$it" } ?: manga.url,
            thumbnail_url = thumbnailUrl ?: manga.thumbnail_url,
            title = title ?: manga.title,
            artist = artist,
            author = group ?: artist,
            genre = tagsToGenreString(),
            status = SManga.COMPLETED,
            // Blank rather than null so the description written by the extension is replaced
            description = altTitle.orEmpty(),
        ).also {
            it.update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(nhId) { stringResource(SYMR.strings.id) },
                getItem(title) { stringResource(MR.strings.title) },
                getItem(altTitle) { stringResource(SYMR.strings.alt_title) },
                getItem(tags.ofNamespace(NAMESPACE_LANGUAGE).firstOrNull()?.name) {
                    stringResource(SYMR.strings.language)
                },
                getItem(thumbnailUrl) { stringResource(SYMR.strings.thumbnail_url) },
                getItem(
                    uploadDate,
                    {
                        DateTimeFormatter.ISO_LOCAL_DATE
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(it))
                    },
                ) {
                    stringResource(SYMR.strings.date_posted)
                },
                getItem(pageCount) { stringResource(SYMR.strings.page_count) },
                getItem(favorites) { stringResource(SYMR.strings.total_favorites) },
                getItem(views) { stringResource(SYMR.strings.total_views) },
                getItem(downloads) { stringResource(SYMR.strings.total_downloads) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0
        private const val TITLE_TYPE_ALT = 1

        const val TAG_TYPE_DEFAULT = 0

        const val NAMESPACE_ARTIST = "artist"
        const val NAMESPACE_GROUP = "group"
        const val NAMESPACE_CATEGORY = "category"
        const val NAMESPACE_LANGUAGE = "language"
    }
}
