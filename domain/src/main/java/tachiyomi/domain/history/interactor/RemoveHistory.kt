package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.service.HistoryTombstones

@Inject
class RemoveHistory(
    private val repository: HistoryRepository,
    private val historyTombstones: HistoryTombstones,
) {

    suspend fun awaitAll(): Boolean {
        // SY -->
        // Every row is about to go, so its identity has to be read first
        val keys = repository.getHistoryTombstoneKeys()
        // SY <--
        val deleted = repository.deleteAllHistory()
        // SY -->
        if (deleted) historyTombstones.remember(keys)
        // SY <--
        return deleted
    }

    suspend fun await(history: HistoryWithRelations) {
        // SY -->
        historyTombstones.remember(
            repository.getHistoryTombstoneKeys(history.mangaId).filter { it.chapterId == history.chapterId },
        )
        // SY <--
        repository.resetHistory(history.id)
    }

    suspend fun await(mangaId: Long) {
        // SY -->
        historyTombstones.remember(repository.getHistoryTombstoneKeys(mangaId))
        // SY <--
        repository.resetHistoryByMangaId(mangaId)
    }

    // SY -->
    suspend fun awaitById(historyId: Long) {
        repository.resetHistory(historyId)
    }
    // SY <--
}
