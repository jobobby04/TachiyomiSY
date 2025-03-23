package tachiyomi.domain.chapter.interactor


import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetChapterReadByMangaId(
    private val chapterRepository: ChapterRepository,
) {
    suspend fun await(mangaId: Long): Map<Double, Boolean> {
        return chapterRepository.getChapterReadByMangaId(mangaId)
    }

    suspend fun subscribe(mangaId: Long): Flow<Map<Double, Boolean>> {
        return chapterRepository.getChapterReadByMangaIdAsFlow(mangaId)
    }
}
