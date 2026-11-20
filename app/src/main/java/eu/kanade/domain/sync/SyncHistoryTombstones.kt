package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.sync.historyTombstoneKey
import tachiyomi.domain.history.model.HistoryTombstoneKey
import tachiyomi.domain.history.service.HistoryTombstones

@Inject
@ContributesBinding(AppScope::class)
class SyncHistoryTombstones(
    private val syncPreferences: SyncPreferences,
) : HistoryTombstones {
    override suspend fun remember(keys: Collection<HistoryTombstoneKey>) {
        syncPreferences.rememberDeletedHistory(
            keys.map { historyTombstoneKey(it.mangaSourceId, it.mangaUrl, it.chapterUrl) },
        )
    }
}
