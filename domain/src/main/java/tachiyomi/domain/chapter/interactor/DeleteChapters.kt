package tachiyomi.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.chapter.repository.ChapterRepository

@Inject
class DeleteChapters(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapters: List<Long>) {
        chapterRepository.removeChaptersWithIds(chapters)
    }
}
