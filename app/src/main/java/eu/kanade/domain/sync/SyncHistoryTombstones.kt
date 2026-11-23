package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.service.HistoryTombstones
import kotlin.time.Clock

@Inject
@ContributesBinding(AppScope::class)
class SyncHistoryTombstones(
    private val syncPreferences: SyncPreferences,
) : HistoryTombstones {
    override suspend fun removalTimeMs(): Long =
        if (syncPreferences.deliversHistoryTombstones()) Clock.System.now().toEpochMilliseconds() else 0
}
