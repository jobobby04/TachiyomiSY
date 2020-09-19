package eu.kanade.tachiyomi.data.backup.offline.models

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
data class BackupManga(
    @ProtoNumber(0) var url: String,
    @ProtoNumber(1) var title: String = "",
    @ProtoNumber(2) var artist: String? = null,
    @ProtoNumber(3) var author: String? = null,
    @ProtoNumber(4) var description: String? = null,
    @ProtoNumber(5) var genre: String? = null,
    @ProtoNumber(6) var status: Int = 0,
    @ProtoNumber(7) var thumbnailUrl: String? = null,
    @ProtoNumber(8) var favorite: Boolean = true,
    @ProtoNumber(9) var source: Long = -1,
    @ProtoNumber(10) var dateAdded: Long = 0,
    @ProtoNumber(11) var viewer: Int = 0,
    @ProtoNumber(12) var chapterFlags: Int = 0,
    @ProtoNumber(13) var chapters: List<BackupChapter>? = null,
    @ProtoNumber(14) var categories: List<String>? = null,
    @ProtoNumber(16) var tracking: List<BackupTracking>? = null,
    @ProtoNumber(17) var history: List<BackupHistory>? = null,
    @ProtoNumber(18) var mergedMangaReferences: List<BackupMergedMangaReference>? = null
) {
    fun getMangaImpl(): MangaImpl {
        return MangaImpl().apply {
            url = this@BackupManga.url
            title = this@BackupManga.title
            artist = this@BackupManga.artist
            author = this@BackupManga.author
            description = this@BackupManga.description
            genre = this@BackupManga.genre
            status = this@BackupManga.status
            thumbnail_url = this@BackupManga.thumbnailUrl
            favorite = this@BackupManga.favorite
            source = this@BackupManga.source
            date_added = this@BackupManga.dateAdded
            viewer = this@BackupManga.viewer
            chapter_flags = this@BackupManga.chapterFlags
        }
    }

    fun getChaptersImpl(): List<ChapterImpl>? {
        return chapters?.map {
            it.toChapterImpl()
        }
    }

    fun getTrackingImpl(): List<TrackImpl>? {
        return tracking?.map {
            it.getTrackingImpl()
        }
    }

    companion object {
        fun copyFrom(manga: Manga): BackupManga {
            return BackupManga(
                url = manga.url,
                title = manga.title,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                status = manga.status,
                thumbnailUrl = manga.thumbnail_url,
                favorite = manga.favorite,
                source = manga.source,
                dateAdded = manga.date_added,
                viewer = manga.viewer,
                chapterFlags = manga.chapter_flags
            )
        }
    }
}
