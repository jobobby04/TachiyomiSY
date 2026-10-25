package tachiyomi.domain.history.model

import kotlin.time.Instant

data class HistoryUpdate(
    val chapterId: Long,
    val readAt: Instant,
    val sessionReadDuration: Long,
)

// SY -->

/**
 * History the user removed has no read time and is left out.
 */
fun History.toHistoryUpdate(): HistoryUpdate? {
    return HistoryUpdate(
        chapterId,
        readAt ?: return null,
        readDuration,
    )
}
// SY <--
