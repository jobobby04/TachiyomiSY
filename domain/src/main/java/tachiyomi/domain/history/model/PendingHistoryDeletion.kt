package tachiyomi.domain.history.model

/**
 * SY-specific file
 *
 * One entry of the history tombstone queue: a removal this device made whose SyncYomi `history`
 * tombstone the server has not confirmed yet, together with the urls that tombstone's key is built
 * from (the server keys a history entry by its chapter).
 *
 * [id] is the queue row's own id — the tombstone outlives the history row it came from, so nothing
 * here points at one. [removedAtMs] is the moment of the removal in epoch milliseconds, like
 * `read_at`; the wire field derived from it is `at`, in Unix seconds.
 */
data class PendingHistoryDeletion(
    val id: Long,
    val sourceId: Long,
    val mangaUrl: String,
    val chapterUrl: String,
    val removedAtMs: Long,
)
