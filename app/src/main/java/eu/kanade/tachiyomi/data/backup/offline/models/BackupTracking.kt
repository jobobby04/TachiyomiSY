package eu.kanade.tachiyomi.data.backup.offline.models

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
data class BackupTracking(
    @ProtoNumber(0) var syncId: Int = 0,
    @ProtoNumber(1) var mediaId: Int = 0,
    @ProtoNumber(2) var libraryId: Long? = null,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var lastChapterRead: Int = 0,
    @ProtoNumber(5) var totalChapters: Int = 0,
    @ProtoNumber(6) var score: Float = 0F,
    @ProtoNumber(7) var status: Int = 0,
    @ProtoNumber(8) var startedReadingDate: Long = 0,
    @ProtoNumber(9) var finishedReadingDate: Long = 0,
    @ProtoNumber(10) var trackingUrl: String
) {
    fun getTrackingImpl(): TrackImpl {
        return TrackImpl().apply {
            sync_id = this@BackupTracking.syncId
            media_id = this@BackupTracking.mediaId
            library_id = this@BackupTracking.libraryId
            title = this@BackupTracking.title
            last_chapter_read = this@BackupTracking.lastChapterRead
            total_chapters = this@BackupTracking.totalChapters
            score = this@BackupTracking.score
            status = this@BackupTracking.status
            started_reading_date = this@BackupTracking.startedReadingDate
            finished_reading_date = this@BackupTracking.finishedReadingDate
            tracking_url = this@BackupTracking.trackingUrl
        }
    }

    companion object {
        fun copyFrom(track: Track): BackupTracking {
            return BackupTracking(
                syncId = track.sync_id,
                mediaId = track.media_id,
                libraryId = track.library_id,
                title = track.title,
                lastChapterRead = track.last_chapter_read,
                totalChapters = track.total_chapters,
                score = track.score,
                status = track.status,
                startedReadingDate = track.started_reading_date,
                finishedReadingDate = track.finished_reading_date,
                trackingUrl = track.tracking_url
            )
        }
    }
}
