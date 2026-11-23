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
        // Every row is about to go, so its identity has to be read first — but only a removal a
        // server can be told about needs it.
        val at = historyTombstones.removalTimeMs()
        val keys = if (at == 0L) emptyList() else repository.getHistoryTombstoneKeys()
        return repository.deleteAllHistory(keys, at)
        // SY <--
    }

    suspend fun await(history: HistoryWithRelations) {
        // SY -->
        val at = historyTombstones.removalTimeMs()
        val keys = if (at == 0L) {
            emptyList()
        } else {
            repository.getHistoryTombstoneKeys(history.mangaId).filter { it.chapterId == history.chapterId }
        }
        repository.resetHistory(history.id, keys, at)
        // SY <--
    }

    suspend fun await(mangaId: Long) {
        // SY -->
        val at = historyTombstones.removalTimeMs()
        val keys = if (at == 0L) emptyList() else repository.getHistoryTombstoneKeys(mangaId)
        repository.resetHistoryByMangaId(mangaId, keys, at)
        // SY <--
    }

    // SY -->
    suspend fun awaitById(historyId: Long) {
        repository.resetHistory(historyId)
    }
    // SY <--
}
