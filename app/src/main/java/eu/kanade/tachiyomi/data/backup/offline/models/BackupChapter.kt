package eu.kanade.tachiyomi.data.backup.offline.models

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
data class BackupChapter(
    @ProtoNumber(0) var url: String,
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) var chapterNumber: Float = 0F,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    @ProtoNumber(6) var lastPageRead: Int = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    @ProtoNumber(9) var sourceOrder: Int = 0
) {
    fun toChapterImpl(): ChapterImpl {
        return ChapterImpl().apply {
            url = this@BackupChapter.url
            name = this@BackupChapter.name
            chapter_number = this@BackupChapter.chapterNumber
            scanlator = this@BackupChapter.scanlator
            read = this@BackupChapter.read
            bookmark = this@BackupChapter.bookmark
            last_page_read = this@BackupChapter.lastPageRead
            date_fetch = this@BackupChapter.dateFetch
            date_upload = this@BackupChapter.dateUpload
            source_order = this@BackupChapter.sourceOrder
        }
    }

    companion object {
        fun copyFrom(chapter: Chapter): BackupChapter {
            return BackupChapter(
                url = chapter.url,
                name = chapter.name,
                chapterNumber = chapter.chapter_number,
                scanlator = chapter.scanlator,
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastPageRead = chapter.last_page_read,
                dateFetch = chapter.date_fetch,
                dateUpload = chapter.date_upload,
                sourceOrder = chapter.source_order
            )
        }
    }
}
