package tachiyomi.domain.history.service

/**
 * SY-specific file
 *
 * Whether a history removal can reach a server right now.
 *
 * [DELIVERABLE] and [UNDELIVERABLE] are verdicts. [UNKNOWN] is the third answer a cached capability
 * probe forces: SyncYomi is selected, but its current host has not answered the v2 probe yet. The two
 * readers need different answers for it —
 *
 * - a *removal* may not queue a tombstone on [UNKNOWN]: nothing confirms that a server will send it,
 *   and the queue is only useful to one that will;
 * - a *sweep* of already queued tombstones must not act on it: this run is about to ask, and dropping
 *   the queue first would discard tombstones the answer would deliver.
 */
enum class HistoryTombstoneDeliverability {
    /** The current configuration can send a tombstone: SyncYomi v2, history sync on. */
    DELIVERABLE,

    /** Nothing can send it: another sync service is selected, or history sync is off. */
    UNDELIVERABLE,

    /** SyncYomi is selected, but its current host has not been confirmed to speak v2 (yet). */
    UNKNOWN,
}

/**
 * Whether a local history removal can currently reach a server.
 *
 * Only the SyncYomi v2 deletions request consumes the queue (`pending_history_deletion`), so a removal
 * on a device whose sync is off, belongs to another service, or points at a host that does not speak
 * v2 queues nothing. A stranded entry is not harmless even so: it makes the restore skip its entry,
 * so no payload could ever restore over that removal.
 */
fun interface HistoryTombstoneDelivery {
    fun deliverability(): HistoryTombstoneDeliverability

    /**
     * The answer a removal path needs: only a [HistoryTombstoneDeliverability.DELIVERABLE] verdict
     * justifies a queue entry, so an unprobed host is undeliverable here.
     */
    fun canDeliver(): Boolean = deliverability() == HistoryTombstoneDeliverability.DELIVERABLE
}
