package eu.kanade.translation.model

import kotlinx.serialization.Serializable

@Serializable
data class ChapterTranslationMetadata(
    val mangaId: Long,
    val chapterId: Long,
    val sourceId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val chapterScanlator: String? = null,
    val chapterUrl: String,
    val pageCount: Int,
    val translatedPageFiles: List<String>,
    val status: ChapterTranslationStatus = ChapterTranslationStatus.TRANSLATED,
    val serverBaseUrl: String,
    val endpointPath: String,
    val usedConfigJson: String,
    val translatedAt: Long,
    val errorMessage: String? = null,
)

@Serializable
enum class ChapterTranslationStatus {
    TRANSLATED,
    ERROR,
}
