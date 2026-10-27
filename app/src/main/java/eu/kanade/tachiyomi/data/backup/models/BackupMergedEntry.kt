package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * SY-specific file
 *
 * [isInfoManga] and [chapterSortMode] are legacy fields kept only so old backups can still be
 * read; new backups store these in [BackupMergeSettings] instead. [mangaUrl] and [mangaSourceId]
 * are the portable identity of the underlying entry (the table stores a manga id).
 */
@Serializable
data class BackupMergedEntry(
    @ProtoNumber(1) var isInfoManga: Boolean = false,
    @ProtoNumber(2) var fetchChapterUpdates: Boolean = false,
    @ProtoNumber(3) var chapterSortMode: Int = 0,
    @ProtoNumber(4) var chapterPriority: Int = 0,
    @ProtoNumber(5) var downloadChapters: Boolean = false,
    @ProtoNumber(7) var mangaUrl: String = "",
    @ProtoNumber(8) var mangaSourceId: Long = 0,
)

/*
 * SY merge-level settings backup class. The info entry is identified by url + source so it can be
 * resolved to a manga id on restore.
 */
@Serializable
data class BackupMergeSettings(
    @ProtoNumber(1) var chapterSortMode: Int = 0,
    @ProtoNumber(2) var infoMangaUrl: String = "",
    @ProtoNumber(3) var infoMangaSourceId: Long = 0,
)
