package tachiyomi.domain.download.model

data class DownloadQueueEntry(
    val id: Long,
    val mangaId: Long,
    val chapterId: Long,
    val priority: Int,
    val addedAt: Long,
    val retryCount: Int,
    val lastAttemptAt: Long?,
    val lastErrorMessage: String?,
    val status: DownloadQueueStatus,
)

enum class DownloadQueueStatus(val value: String) {
    PENDING("PENDING"),
    DOWNLOADING("DOWNLOADING"),
    FAILED("FAILED"),
    COMPLETED("COMPLETED"),
    ;

    companion object {
        /**
         * Converts a string into the corresponding DownloadQueueStatus.
         *
         * @param value The string representation of a status.
         * @return The matching DownloadQueueStatus, or `PENDING` if no match is found.
         */
        fun fromString(value: String): DownloadQueueStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}

enum class DownloadPriority(val value: Int) {
    LOW(-1), // New chapters from library updates
    NORMAL(0), // Auto-download from reading history
    HIGH(1), // Next chapter of currently reading manga
    URGENT(2), // User clicked "Download Now" or reading in reader
    ;

    companion object {
        /**
         * Maps an integer value to the corresponding DownloadPriority.
         *
         * @param value Numeric priority value to map.
         * @return The matching DownloadPriority, or `NORMAL` if no match is found.
         */
        fun fromInt(value: Int): DownloadPriority {
            return entries.find { it.value == value } ?: NORMAL
        }
    }
}

enum class DownloadErrorType {
    NETWORK_ERROR, // Retry with backoff
    SOURCE_ERROR, // Retry with longer backoff
    DISK_FULL, // Don't retry (user action needed)
    CHAPTER_NOT_FOUND, // Don't retry (404/deleted)
    UNKNOWN, // Retry with max backoff
    ;

    val canRetry: Boolean
        get() = this in arrayOf(NETWORK_ERROR, SOURCE_ERROR, UNKNOWN)

    val backoffMultiplier: Double
        get() = when (this) {
            NETWORK_ERROR -> 1.0
            SOURCE_ERROR -> 1.5
            UNKNOWN -> 2.0
            else -> 0.0
        }
}
