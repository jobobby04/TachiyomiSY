package tachiyomi.data.chapter

import kotlinx.serialization.json.JsonObject
import tachiyomi.domain.chapter.model.Chapter

object ChapterMapper {
    fun mapChapter(
        id: Long,
        mangaId: Long,
        remoteUrl: String,
        remoteName: String,
        remoteScanlator: String?,
        remoteChapterNumber: Double,
        remoteDateUpload: Long,
        remoteOrder: Long,
        remoteMemo: JsonObject,
        userRead: Boolean,
        userBookmark: Boolean,
        userLastPageRead: Long,
        stateDateFetch: Long,
        // SY -->
        stateLastModifiedAt: Long,
        stateVersion: Long,
        @Suppress("UNUSED_PARAMETER")
        stateIsSyncing: Long,
        // SY <--
    ): Chapter = Chapter(
        id = id,
        mangaId = mangaId,
        read = userRead,
        bookmark = userBookmark,
        lastPageRead = userLastPageRead,
        dateFetch = stateDateFetch,
        sourceOrder = remoteOrder,
        url = remoteUrl,
        name = remoteName,
        dateUpload = remoteDateUpload,
        chapterNumber = remoteChapterNumber,
        scanlator = remoteScanlator,
        // SY -->
        lastModifiedAt = stateLastModifiedAt,
        version = stateVersion,
        // SY <--
        memo = remoteMemo,
    )
}
