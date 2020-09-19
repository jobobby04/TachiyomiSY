package eu.kanade.tachiyomi.data.backup.full.models

import exh.merged.sql.models.MergedMangaReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
data class BackupMergedMangaReference(
    @ProtoNumber(0) var isInfoManga: Boolean,
    @ProtoNumber(1) var getChapterUpdates: Boolean,
    @ProtoNumber(2) var chapterSortMode: Int,
    @ProtoNumber(3) var chapterPriority: Int,
    @ProtoNumber(4) var downloadChapters: Boolean,
    @ProtoNumber(5) var mergeUrl: String,
    @ProtoNumber(6) var mangaUrl: String,
    @ProtoNumber(7) var mangaSourceId: Long
) {
    fun getMergedMangaReference(): MergedMangaReference {
        return MergedMangaReference(
            isInfoManga = isInfoManga,
            getChapterUpdates = getChapterUpdates,
            chapterSortMode = chapterSortMode,
            chapterPriority = chapterPriority,
            downloadChapters = downloadChapters,
            mergeUrl = mergeUrl,
            mangaUrl = mangaUrl,
            mangaSourceId = mangaSourceId,
            mergeId = null,
            mangaId = null,
            id = null
        )
    }

    companion object {
        fun copyFrom(mergedMangaReference: MergedMangaReference): BackupMergedMangaReference {
            return BackupMergedMangaReference(
                isInfoManga = mergedMangaReference.isInfoManga,
                getChapterUpdates = mergedMangaReference.getChapterUpdates,
                chapterSortMode = mergedMangaReference.chapterSortMode,
                chapterPriority = mergedMangaReference.chapterPriority,
                downloadChapters = mergedMangaReference.downloadChapters,
                mergeUrl = mergedMangaReference.mergeUrl,
                mangaUrl = mergedMangaReference.mangaUrl,
                mangaSourceId = mergedMangaReference.mangaSourceId
            )
        }
    }
}
