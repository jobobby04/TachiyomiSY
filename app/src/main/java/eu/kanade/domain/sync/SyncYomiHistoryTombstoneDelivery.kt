package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.domain.history.service.HistoryTombstoneDeliverability
import tachiyomi.domain.history.service.HistoryTombstoneDelivery

/**
 * SY-specific file
 *
 * The single answer to "can a history removal be delivered as a server tombstone right now?". It lives
 * next to [SyncPreferences], which owns every fact it needs, so the removal paths and the sweep of
 * tombstones a configuration change stranded cannot disagree.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SyncYomiHistoryTombstoneDelivery(
    private val syncPreferences: SyncPreferences,
) : HistoryTombstoneDelivery {
    override fun deliverability(): HistoryTombstoneDeliverability =
        syncPreferences.historyTombstoneDeliverability()
}
