package tachiyomi.domain.download.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class DownloadQueueEntryTest {

    @Test
    fun `DownloadQueueEntry can be created with all fields`() {
        val entry = DownloadQueueEntry(
            id = 1L,
            mangaId = 100L,
            chapterId = 200L,
            priority = 1,
            addedAt = System.currentTimeMillis(),
            retryCount = 0,
            lastAttemptAt = null,
            lastErrorMessage = null,
            status = DownloadQueueStatus.PENDING,
        )

        entry.id shouldBe 1L
        entry.mangaId shouldBe 100L
        entry.chapterId shouldBe 200L
        entry.priority shouldBe 1
        entry.retryCount shouldBe 0
        entry.status shouldBe DownloadQueueStatus.PENDING
    }

    @Test
    fun `DownloadQueueEntry can track retry attempts`() {
        val entry = DownloadQueueEntry(
            id = 1L,
            mangaId = 100L,
            chapterId = 200L,
            priority = 0,
            addedAt = System.currentTimeMillis(),
            retryCount = 3,
            lastAttemptAt = System.currentTimeMillis(),
            lastErrorMessage = "Network error",
            status = DownloadQueueStatus.FAILED,
        )

        entry.retryCount shouldBe 3
        entry.lastErrorMessage shouldBe "Network error"
        entry.status shouldBe DownloadQueueStatus.FAILED
    }
}

@Execution(ExecutionMode.CONCURRENT)
class DownloadQueueStatusTest {

    @Test
    fun `fromString returns correct status for valid values`() {
        DownloadQueueStatus.fromString("PENDING") shouldBe DownloadQueueStatus.PENDING
        DownloadQueueStatus.fromString("DOWNLOADING") shouldBe DownloadQueueStatus.DOWNLOADING
        DownloadQueueStatus.fromString("FAILED") shouldBe DownloadQueueStatus.FAILED
        DownloadQueueStatus.fromString("COMPLETED") shouldBe DownloadQueueStatus.COMPLETED
    }

    @Test
    fun `fromString returns PENDING for invalid values`() {
        DownloadQueueStatus.fromString("INVALID") shouldBe DownloadQueueStatus.PENDING
        DownloadQueueStatus.fromString("") shouldBe DownloadQueueStatus.PENDING
        DownloadQueueStatus.fromString("pending") shouldBe DownloadQueueStatus.PENDING
    }

    @Test
    fun `all status values have correct string representation`() {
        DownloadQueueStatus.PENDING.value shouldBe "PENDING"
        DownloadQueueStatus.DOWNLOADING.value shouldBe "DOWNLOADING"
        DownloadQueueStatus.FAILED.value shouldBe "FAILED"
        DownloadQueueStatus.COMPLETED.value shouldBe "COMPLETED"
    }
}

@Execution(ExecutionMode.CONCURRENT)
class DownloadPriorityTest {

    @Test
    fun `fromInt returns correct priority for valid values`() {
        DownloadPriority.fromInt(-1) shouldBe DownloadPriority.LOW
        DownloadPriority.fromInt(0) shouldBe DownloadPriority.NORMAL
        DownloadPriority.fromInt(1) shouldBe DownloadPriority.HIGH
        DownloadPriority.fromInt(2) shouldBe DownloadPriority.URGENT
    }

    @Test
    fun `fromInt returns NORMAL for invalid values`() {
        DownloadPriority.fromInt(100) shouldBe DownloadPriority.NORMAL
        DownloadPriority.fromInt(-100) shouldBe DownloadPriority.NORMAL
    }

    @Test
    fun `priorities are ordered correctly by value`() {
        DownloadPriority.LOW.value shouldBe -1
        DownloadPriority.NORMAL.value shouldBe 0
        DownloadPriority.HIGH.value shouldBe 1
        DownloadPriority.URGENT.value shouldBe 2

        // Verify ordering
        (DownloadPriority.LOW.value < DownloadPriority.NORMAL.value) shouldBe true
        (DownloadPriority.NORMAL.value < DownloadPriority.HIGH.value) shouldBe true
        (DownloadPriority.HIGH.value < DownloadPriority.URGENT.value) shouldBe true
    }
}

@Execution(ExecutionMode.CONCURRENT)
class DownloadErrorTypeTest {

    @Test
    fun `network error can be retried`() {
        DownloadErrorType.NETWORK_ERROR.canRetry shouldBe true
    }

    @Test
    fun `source error can be retried`() {
        DownloadErrorType.SOURCE_ERROR.canRetry shouldBe true
    }

    @Test
    fun `unknown error can be retried`() {
        DownloadErrorType.UNKNOWN.canRetry shouldBe true
    }

    @Test
    fun `disk full error cannot be retried`() {
        DownloadErrorType.DISK_FULL.canRetry shouldBe false
    }

    @Test
    fun `chapter not found error cannot be retried`() {
        DownloadErrorType.CHAPTER_NOT_FOUND.canRetry shouldBe false
    }

    @Test
    fun `backoff multipliers are correct`() {
        DownloadErrorType.NETWORK_ERROR.backoffMultiplier shouldBe 1.0
        DownloadErrorType.SOURCE_ERROR.backoffMultiplier shouldBe 1.5
        DownloadErrorType.UNKNOWN.backoffMultiplier shouldBe 2.0
        DownloadErrorType.DISK_FULL.backoffMultiplier shouldBe 0.0
        DownloadErrorType.CHAPTER_NOT_FOUND.backoffMultiplier shouldBe 0.0
    }

    @Test
    fun `retryable errors have positive backoff multipliers`() {
        DownloadErrorType.entries
            .filter { it.canRetry }
            .forEach { errorType ->
                (errorType.backoffMultiplier > 0.0) shouldBe true
            }
    }

    @Test
    fun `non-retryable errors have zero backoff multipliers`() {
        DownloadErrorType.entries
            .filter { !it.canRetry }
            .forEach { errorType ->
                errorType.backoffMultiplier shouldBe 0.0
            }
    }
}
