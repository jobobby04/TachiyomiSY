package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.sync.historyTombstoneKey
import eu.kanade.tachiyomi.data.sync.queuedHistoryTombstone
import tachiyomi.domain.history.model.HistoryTombstoneKey
import tachiyomi.domain.history.service.HistoryTombstones
import kotlin.time.Clock

@Inject
@ContributesBinding(AppScope::class)
class SyncHistoryTombstones(
    private val syncPreferences: SyncPreferences,
) : HistoryTombstones {
    override suspend fun remember(keys: Collection<HistoryTombstoneKey>) {
        val at = Clock.System.now().toEpochMilliseconds() / 1000
        syncPreferences.rememberDeletedHistory(
            keys.map { queuedHistoryTombstone(historyTombstoneKey(it.mangaSourceId, it.mangaUrl, it.chapterUrl), at) },
        )
    }
}
