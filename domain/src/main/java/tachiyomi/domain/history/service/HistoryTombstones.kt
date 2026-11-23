package tachiyomi.domain.history.service

/**
 * SY-specific file
 *
 * Answers whether a history removal made here can reach a server, and with which moment to stamp it,
 * so the removal queues a tombstone instead of the domain layer reading sync preferences.
 */
fun interface HistoryTombstones {
    /**
     * The moment of a removal being queued now, in epoch milliseconds, or 0 when the configured sync
     * cannot deliver a tombstone — the removal then queues nothing.
     */
    suspend fun removalTimeMs(): Long
}
