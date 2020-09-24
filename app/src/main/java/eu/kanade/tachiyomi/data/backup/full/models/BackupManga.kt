package eu.kanade.tachiyomi.data.backup.full.models

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
    // in 1.x some of these values have different names, they are listed here
    // url is called key
    // thumbnailUrl is called cover
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    // 10 is skipped because custom covers are not saved to a new value like in 1.x
    // 11 is skipped because there is no last update value in 0.x
    // 12 is skipped because there is no last init value in 0.x
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    // 15 is skipped because there is no flags value in 0.x
    @ProtoNumber(15) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(14) var categories: List<Int> = emptyList(),
    @ProtoNumber(16) var tracking: List<BackupTracking> = emptyList(),
    // Values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(17) var favorite: Boolean = true,
    @ProtoNumber(18) var chapterFlags: Int = 0,
    @ProtoNumber(19) var history: List<BackupHistory> = emptyList(),
    // SY specific values
    @ProtoNumber(600) var mergedMangaReferences: List<BackupMergedMangaReference> = emptyList()
) {
    fun getMangaImpl(): MangaImpl {
        return MangaImpl().apply {
            url = this@BackupManga.url
            title = this@BackupManga.title
            artist = this@BackupManga.artist
            author = this@BackupManga.author
            description = this@BackupManga.description
            genre = this@BackupManga.genre.joinToString()
            status = this@BackupManga.status
            thumbnail_url = this@BackupManga.thumbnailUrl
            favorite = this@BackupManga.favorite
            source = this@BackupManga.source
            date_added = this@BackupManga.dateAdded
            viewer = this@BackupManga.viewer
            chapter_flags = this@BackupManga.chapterFlags
        }
    }

    fun getChaptersImpl(): List<ChapterImpl> {
        return chapters.map {
            it.toChapterImpl()
        }
    }

    fun getTrackingImpl(): List<TrackImpl> {
        return tracking.map {
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
                genre = manga.getGenres() ?: emptyList(),
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
