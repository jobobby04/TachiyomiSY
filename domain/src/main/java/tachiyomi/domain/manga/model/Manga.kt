package tachiyomi.domain.manga.model

import android.annotation.SuppressLint
import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import tachiyomi.core.common.preference.TriState
import java.io.ObjectStreamException
import kotlin.time.Instant
import java.io.Serializable as JavaSerializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
@Immutable
data class Manga(
    val id: Long,
    val source: Long,
    val lastUpdate: Instant?,
    val nextUpdate: Instant?,
    val fetchInterval: Int,
    val favoriteAt: Instant?,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Instant?,
    val url: String,
    // SY -->
    val ogTitle: String,
    val ogArtist: String?,
    val ogAuthor: String?,
    val ogThumbnailUrl: String?,
    val ogDescription: String?,
    val ogGenre: List<String>?,
    val ogStatus: Long,
    // SY <--
    val updateStrategy: UpdateStrategy,
    val initialized: Boolean,
    // SY -->
    val lastModifiedAt: Long,
    val version: Long,
    // SY <--
    val notes: String,
    val memo: JsonObject,
    // SY -->
    val userTitle: String? = null,
    val userAuthor: String? = null,
    val userArtist: String? = null,
    val userThumbnailUrl: String? = null,
    val userDescription: String? = null,
    val userGenre: List<String>? = null,
    val userStatus: Long? = null,
    // SY <--
) : JavaSerializable {

    val favorite: Boolean
        get() = favoriteAt != null

    // SY -->
    val title: String
        get() = userTitle ?: ogTitle

    val author: String?
        get() = userAuthor ?: ogAuthor

    val artist: String?
        get() = userArtist ?: ogArtist

    val thumbnailUrl: String?
        get() = userThumbnailUrl ?: ogThumbnailUrl

    val description: String?
        get() = userDescription ?: ogDescription

    val genre: List<String>?
        get() = userGenre ?: ogGenre

    val status: Long
        get() = userStatus ?: ogStatus
    // SY <--

    val expectedNextUpdate: Instant?
        get() = nextUpdate?.takeIf { status != SManga.COMPLETED.toLong() }

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    val displayMode: Long
        get() = chapterFlags and CHAPTER_DISPLAY_MASK

    val unreadFilterRaw: Long
        get() = chapterFlags and CHAPTER_UNREAD_MASK

    val downloadedFilterRaw: Long
        get() = chapterFlags and CHAPTER_DOWNLOADED_MASK

    val bookmarkedFilterRaw: Long
        get() = chapterFlags and CHAPTER_BOOKMARKED_MASK

    val unreadFilter: TriState
        get() = when (unreadFilterRaw) {
            CHAPTER_SHOW_UNREAD -> TriState.ENABLED_IS
            CHAPTER_SHOW_READ -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val bookmarkedFilter: TriState
        get() = when (bookmarkedFilterRaw) {
            CHAPTER_SHOW_BOOKMARKED -> TriState.ENABLED_IS
            CHAPTER_SHOW_NOT_BOOKMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    fun sortDescending(): Boolean {
        return chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_DESC
    }

    companion object {
        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORT_DESC = 0x00000000L
        const val CHAPTER_SORT_ASC = 0x00000001L
        const val CHAPTER_SORT_DIR_MASK = 0x00000001L

        const val CHAPTER_SHOW_UNREAD = 0x00000002L
        const val CHAPTER_SHOW_READ = 0x00000004L
        const val CHAPTER_UNREAD_MASK = 0x00000006L

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_ALPHABET = 0x00000300L
        const val CHAPTER_SORTING_MASK = 0x00000300L

        const val CHAPTER_DISPLAY_NAME = 0x00000000L
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000L
        const val CHAPTER_DISPLAY_MASK = 0x00100000L

        /**
         * [favoriteAt] of an entry added to the library before the time it was added was recorded.
         */
        val UNKNOWN_FAVORITE_AT = Instant.fromEpochMilliseconds(0)

        fun create() = Manga(
            id = -1L,
            url = "",
            // SY -->
            ogTitle = "",
            // SY <--
            source = -1L,
            lastUpdate = null,
            nextUpdate = null,
            fetchInterval = 0,
            favoriteAt = null,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = null,
            // SY -->
            ogArtist = null,
            ogAuthor = null,
            ogThumbnailUrl = null,
            ogDescription = null,
            ogGenre = null,
            ogStatus = 0L,
            // SY <--
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = false,
            // SY -->
            lastModifiedAt = 0L,
            version = 0L,
            // SY <--
            notes = "",
            memo = JsonObject.EMPTY,
        )
    }

    @Throws(ObjectStreamException::class)
    private fun writeReplace(): Any {
        return JavaToKotlinXSerializable(Json.encodeToString<Manga>(this))
    }

    class JavaToKotlinXSerializable(private val data: String) : JavaSerializable {

        @Throws(ObjectStreamException::class)
        private fun readResolve(): Any {
            return Json.decodeFromString<Manga>(data)
        }
    }
}
