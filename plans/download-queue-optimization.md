# TachiyomiSY Download Queue Optimization - Implementation Plan

## Executive Summary

This plan addresses four critical download system issues in TachiyomiSY with optimized, production-ready solutions that go beyond the pseudocode suggestions in GitHub issues #1434 and #1496.

**Problems Being Solved:**
1. **Queue Resets** - Downloads lost on app restart/crash
2. **Unreliable Auto-Download** - Only works with downloaded chapters, stops on errors
3. **Orphaned _tmp Folders** - Can accumulate to 1GB+ of wasted storage
4. **Background Download Failures** - Requires foreground, dies on network issues

**Solution Approach:**
- Database-backed queue with retry tracking (not just SharedPreferences)
- WorkManager periodic workers with exponential backoff
- Smart polling based on reading history (not just reader triggers)
- Automatic cleanup with multiple safety checkpoints
- Priority-based queue system for optimal UX

---

## Problem Analysis

### Issue #1: Queue Gets Reset/Cleared

**Root Causes Identified:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadStore.kt:112` - Calls `clear()` immediately after `restore()`
- SharedPreferences-only persistence (no database backing)
- Lines 102-107 silently drop entries when manga/chapter deleted from DB
- No tracking of: retry count, failure reason, priority, timestamps

**Current Behavior:**
```kotlin
fun restore(): List<Download> {
    val downloads = /* load from SharedPreferences */
    clear() // ← PROBLEM: Clears queue immediately!
    return downloads
}
```

### Issue #2: Auto-Download Unreliable

**Root Causes Identified:**
- `/ui/reader/ReaderViewModel.kt:623` - Requires current chapter to be downloaded
- `/ui/reader/ReaderViewModel.kt:610` - Hardcoded 25% trigger point
- No background polling mechanism
- Only triggered by reader activity

**Current Behavior:**
```kotlin
// Line 623 - BLOCKS auto-download if chapter not downloaded!
if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
```

### Issue #3: Orphaned _tmp Folders

**Root Causes Identified:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt:366` - Creates `_tmp` folder
- Line 437 only deletes on successful completion
- No cleanup on crash/cancellation/error
- DownloadCache.kt:412 ignores them (invisible to app)

**Accumulation Pattern:**
- Each failed download = 1 temp folder (5-50MB average)
- Over months: 20+ failures = 200MB - 1GB+ wasted

### Issue #4: Background Downloads Fail

**Root Causes Identified:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadJob.kt` - One-time WorkManager job
- Lines 96-98 stop completely on network loss
- No exponential backoff or retry logic
- Dies when app killed

---

## Optimized Solution Architecture

### 1. Database-Backed Download Queue ⭐ CRITICAL

**Why Database Over SharedPreferences?**
- Survives app uninstalls (Android backup)
- ACID transactions prevent corruption
- Foreign key constraints auto-cleanup deleted manga/chapters
- Efficient querying with indexes
- Track rich metadata (retries, errors, priorities)

**Schema Design:**
```sql
-- /data/src/main/sqldelight/tachiyomi/data/downloads.sq
CREATE TABLE download_queue (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL,
    chapter_id INTEGER NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    added_at INTEGER NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at INTEGER,
    last_error_message TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',

    -- Foreign keys auto-remove when manga/chapter deleted
    FOREIGN KEY(manga_id) REFERENCES manga(_id) ON DELETE CASCADE,
    FOREIGN KEY(chapter_id) REFERENCES chapters(_id) ON DELETE CASCADE,

    -- Prevent duplicate entries
    UNIQUE(chapter_id)
);

-- Optimized for queue processing
CREATE INDEX download_queue_processing_idx
ON download_queue(status, priority DESC, added_at ASC);
```

**Status Enum:**
- `PENDING` - Waiting to download (with backoff if retried)
- `DOWNLOADING` - Currently being downloaded
- `FAILED` - Max retries exceeded or non-retryable error
- `COMPLETED` - Successfully downloaded (cleaned up after 24h)

**Priority Levels:**
```kotlin
enum class DownloadPriority(val value: Int) {
    URGENT(2),   // User clicked "Download Now" or reading in reader
    HIGH(1),     // Next chapter of currently reading manga
    NORMAL(0),   // Auto-download from reading history
    LOW(-1)      // New chapters from library updates
}
```

**Migration Strategy:**
```kotlin
// DownloadStore.kt - Keep for backward compatibility
fun restore(): List<Download> {
    val prefs = preferences.all.mapNotNull { /* ... */ }

    if (prefs.isNotEmpty() && !migrationCompleted) {
        // Migrate to database
        scope.launch {
            prefs.forEach { obj ->
                downloadQueueRepository.add(
                    mangaId = obj.mangaId,
                    chapterId = obj.chapterId,
                    priority = DownloadPriority.NORMAL.value
                )
            }
            // Mark migration complete
            preferences.edit {
                putBoolean("queue_migrated_to_db", true)
                clear()
            }
        }
    }

    // Load from database going forward
    return downloadQueueRepository.getPending().map { /* to Download */ }
}
```

### 2. Persistent Periodic Download Worker ⭐ CRITICAL

**Why Periodic Over One-Time?**
- Survives app kills and system reboots
- WorkManager automatically reschedules
- Handles network changes gracefully
- Respects battery and storage constraints
- Doze mode compatible

**Implementation:**
```kotlin
// /app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadWorker.kt
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloadManager = Injekt.get<DownloadManager>()
        val queueRepo = Injekt.get<DownloadQueueRepository>()

        // Get pending downloads (with backoff applied)
        val pending = queueRepo.getPendingWithBackoff()
        if (pending.isEmpty()) {
            return Result.success()
        }

        // Check network constraints
        if (!checkNetworkConstraints()) {
            return Result.retry() // WorkManager will retry when network available
        }

        // Run as foreground service for reliability
        setForegroundSafely()

        // Start downloads
        downloadManager.downloaderStart()

        // Monitor until complete or stopped
        while (downloadManager.isRunning && !isStopped) {
            delay(1000)
        }

        return Result.success()
    }

    companion object {
        fun setupPeriodicWork(context: Context) {
            val prefs = Injekt.get<DownloadPreferences>()
            val interval = prefs.downloadWorkerInterval().get()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (prefs.downloadOnlyOverWifi().get())
                        NetworkType.UNMETERED
                    else
                        NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DownloadWorker>(
                repeatInterval = interval.toLong(),
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 5,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    2, TimeUnit.MINUTES // Start with 2 min backoff
                )
                .addTag("DownloadWorker")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DownloadWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
```

**Exponential Backoff Strategy:**
```kotlin
// DownloadQueueRepository.kt
suspend fun getPendingWithBackoff(): List<DownloadQueueEntry> {
    val now = System.currentTimeMillis()

    return database.downloadQueries
        .getPendingByPriority() // ORDER BY priority DESC, added_at ASC
        .executeAsList()
        .filter { entry ->
            if (entry.status != "PENDING") return@filter false

            // Calculate backoff delay based on retry count
            val backoffDelay = calculateBackoffDelay(entry.retryCount)
            val timeSinceLastAttempt = now - (entry.lastAttemptAt ?: 0)

            // Only include if backoff period has elapsed
            timeSinceLastAttempt >= backoffDelay
        }
}

private fun calculateBackoffDelay(retryCount: Int): Long {
    // Progressive backoff: 2min, 4min, 8min, 16min, 32min, 1hr, 2hr, 6hr (cap)
    val minutes = (2.0.pow(retryCount.coerceAtMost(7))).toLong() * 2
    return minutes.coerceAtMost(360) * 60 * 1000 // Max 6 hours
}
```

### 3. Smart Auto-Download Polling ⭐ HIGH PRIORITY

**Key Optimization:** Remove dependency on current chapter being downloaded!

**Reading History-Based Approach:**
```kotlin
// /app/src/main/java/eu/kanade/tachiyomi/data/download/AutoDownloadPollingWorker.kt
class AutoDownloadPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Injekt.get<DownloadPreferences>()
        if (!prefs.autoDownloadFromReadingHistory().get()) {
            return Result.success()
        }

        val getChaptersForAutoDownload = Injekt.get<GetChaptersForAutoDownload>()
        val downloadManager = Injekt.get<DownloadManager>()

        // Get chapters to download based on reading history
        val toDownload = getChaptersForAutoDownload.await()

        toDownload.forEach { (manga, chapters) ->
            downloadManager.downloadChaptersWithPriority(
                manga,
                chapters,
                priority = DownloadPriority.NORMAL
            )
        }

        return Result.success()
    }
}
```

**Smart Chapter Selection:**
```kotlin
// /domain/src/main/java/tachiyomi/domain/download/interactor/GetChaptersForAutoDownload.kt
class GetChaptersForAutoDownload(
    private val getHistory: GetHistory,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getNextChapters: GetNextChapters,
    private val downloadManager: DownloadManager,
    private val downloadPreferences: DownloadPreferences,
) {
    suspend fun await(): Map<Manga, List<Chapter>> {
        val daysBack = downloadPreferences.autoDownloadReadingHistoryDays().get()
        val downloadAhead = downloadPreferences.autoDownloadWhileReading().get()

        if (downloadAhead == 0) return emptyMap()

        // Get manga read in last N days
        val cutoffTime = System.currentTimeMillis() - daysBack.days.inWholeMilliseconds
        val recentHistory = getHistory.await()
            .filter { it.readAt!! >= cutoffTime }
            .groupBy { it.mangaId }

        val result = mutableMapOf<Manga, List<Chapter>>()

        for ((mangaId, historyList) in recentHistory) {
            val manga = getManga.await(mangaId) ?: continue
            if (!manga.favorite) continue

            // Get last read chapter
            val lastReadChapter = historyList
                .maxByOrNull { it.readAt!! }
                ?.chapterId
                ?: continue

            // Get next N unread/undownloaded chapters
            val nextChapters = getNextChapters.await(mangaId, lastReadChapter)
                .filter { !it.read }
                .filter { !downloadManager.isChapterDownloaded(it, manga) }
                .take(downloadAhead)

            if (nextChapters.isNotEmpty()) {
                result[manga] = nextChapters
            }
        }

        return result
    }
}
```

**Fix Reader Auto-Download:**
```kotlin
// /ui/reader/ReaderViewModel.kt - Line 618-651
private fun downloadNextChapters() {
    if (downloadAheadAmount == 0) return
    val manga = manga ?: return

    viewModelScope.launchIO {
        val currentChapter = getCurrentChapter()?.chapter ?: return@launchIO

        // OPTIMIZATION: Remove the downloaded chapter requirement!
        // OLD CODE (LINE 623): if (pageLoader !is DownloadPageLoader) return
        // NEW CODE: Always queue next chapters

        val nextChapters = getNextChapters.await(manga.id, currentChapter.id!!)
            .run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(currentChapter.toDomainChapter()!!)
                } else {
                    this
                }
            }
            .filter { !downloadManager.isChapterDownloaded(it, manga) }
            .take(downloadAheadAmount)

        if (nextChapters.isNotEmpty()) {
            // Use HIGH priority for reader-triggered downloads
            downloadQueueRepository.addAll(
                nextChapters.map { chapter ->
                    DownloadQueueEntry(
                        mangaId = manga.id,
                        chapterId = chapter.id,
                        priority = DownloadPriority.HIGH.value // Reader context = high priority
                    )
                }
            )
            downloadManager.startDownloads()
        }
    }
}
```

### 4. Automatic Temp Folder Cleanup ⭐ MEDIUM PRIORITY

**Multi-Point Cleanup Strategy:**

**Point 1: On Downloader Init (App Startup)**
```kotlin
// /app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
init {
    launchNow {
        val chapters = async { store.restore() }
        addAllToQueue(chapters.await())

        // NEW: Cleanup orphaned temp folders on startup
        if (downloadPreferences.cleanupOrphanedFoldersOnStartup().get()) {
            cleanupOrphanedTempFolders()
        }
    }
}

private suspend fun cleanupOrphanedTempFolders() = withIOContext {
    val rootDir = provider.getDownloadsDirectory() ?: return@withIOContext
    val now = System.currentTimeMillis()
    var cleanedCount = 0
    var freedBytes = 0L

    // Walk all source/manga directories
    rootDir.listFiles()?.forEach { sourceDir ->
        if (!sourceDir.isDirectory) return@forEach

        sourceDir.listFiles()?.forEach { mangaDir ->
            if (!mangaDir.isDirectory) return@forEach

            mangaDir.listFiles()?.forEach { file ->
                if (file.name?.endsWith(TMP_DIR_SUFFIX) != true) return@forEach

                // Safety: Only delete folders older than 1 hour
                val ageMillis = now - (file.lastModified() ?: now)
                if (ageMillis > 1.hours.inWholeMilliseconds) {
                    val size = file.length() ?: 0
                    if (file.delete()) {
                        cleanedCount++
                        freedBytes += size
                        logcat { "Cleaned orphaned temp: ${file.name}" }
                    }
                }
            }
        }
    }

    if (cleanedCount > 0) {
        logcat {
            "Cleanup: removed $cleanedCount temp folders, " +
            "freed ${freedBytes / 1024 / 1024}MB"
        }
    }
}
```

**Point 2: Before Creating New Temp Folder**
```kotlin
// /app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt:366
private suspend fun downloadChapter(download: Download): Download {
    // ... existing code ...

    val chapterDirname = provider.getChapterDirName(
        download.chapter.name,
        download.chapter.scanlator,
        download.chapter.url,
    )

    // NEW: Delete stale temp folder before creating new one
    val existingTmp = mangaDir.findFile("$chapterDirname$TMP_DIR_SUFFIX")
    if (existingTmp != null) {
        logcat { "Removing stale temp folder for retry: ${existingTmp.name}" }
        existingTmp.delete()
    }

    val tmpDir = mangaDir.createDirectory("$chapterDirname$TMP_DIR_SUFFIX")!!

    // ... rest of existing code ...
}
```

**Point 3: Periodic Cleanup Worker**
```kotlin
// /app/src/main/java/eu/kanade/tachiyomi/data/download/TempFolderCleanupWorker.kt
class TempFolderCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloader = Injekt.get<Downloader>()
        downloader.cleanupOrphanedTempFolders() // Reuse existing cleanup logic
        return Result.success()
    }

    companion object {
        fun setupDailyCleanup(context: Context) {
            val request = PeriodicWorkRequestBuilder<TempFolderCleanupWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(3, TimeUnit.HOURS) // Run 3 hours after app start
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "TempFolderCleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

### 5. Enhanced Error Handling & Recovery

**Error Classification System:**
```kotlin
enum class DownloadErrorType {
    NETWORK_ERROR,        // Retry with backoff
    SOURCE_ERROR,         // Retry with longer backoff
    DISK_FULL,           // Don't retry (user action needed)
    CHAPTER_NOT_FOUND,   // Don't retry (404/deleted)
    UNKNOWN;             // Retry with max backoff

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

fun classifyError(error: Throwable): DownloadErrorType {
    return when {
        error.message?.contains("No space left", ignoreCase = true) == true ->
            DownloadErrorType.DISK_FULL

        error.message?.contains("404", ignoreCase = true) == true ||
        error.message?.contains("not found", ignoreCase = true) == true ->
            DownloadErrorType.CHAPTER_NOT_FOUND

        error is IOException ||
        error.message?.contains("network", ignoreCase = true) == true ->
            DownloadErrorType.NETWORK_ERROR

        error.message?.contains("HTTP", ignoreCase = true) == true ->
            DownloadErrorType.SOURCE_ERROR

        else ->
            DownloadErrorType.UNKNOWN
    }
}
```

**Download Job Error Handling:**
```kotlin
// /app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
private fun CoroutineScope.launchDownloadJob(download: Download) = launchIO {
    try {
        downloadChapter(download)

        when (download.status) {
            Download.State.DOWNLOADED -> {
                // Success - remove from queue
                downloadQueueRepository.markCompleted(download.chapter.id)
                removeFromQueue(download)
            }
            Download.State.ERROR -> {
                // Error occurred - record failure
                val errorType = classifyError(download.errorThrowable)
                downloadQueueRepository.recordFailure(
                    chapterId = download.chapter.id,
                    errorMessage = download.errorThrowable?.message ?: "Unknown error",
                    errorType = errorType
                )
            }
        }

        if (areAllDownloadsFinished()) {
            stop()
        }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e

        // Classify error
        val errorType = classifyError(e)

        logcat(LogPriority.ERROR, e) { "Download failed: ${download.chapter.name}" }

        // Record in database
        downloadQueueRepository.recordFailure(
            chapterId = download.chapter.id,
            errorMessage = e.message ?: "Unknown error",
            errorType = errorType
        )

        // Remove from queue if can't retry
        if (!errorType.canRetry) {
            downloadQueueRepository.remove(download.chapter.id)
            notifier.onError("Can't retry: ${e.message}")
        }

        // Continue with next download (don't stop entire queue)
        if (queueState.value.isNotEmpty()) {
            // Continue processing
        } else {
            stop()
        }
    }
}
```

**Repository Failure Tracking:**
```kotlin
// DownloadQueueRepository.kt
suspend fun recordFailure(
    chapterId: Long,
    errorMessage: String,
    errorType: DownloadErrorType
) {
    val entry = getByChapterId(chapterId) ?: return
    val newRetryCount = entry.retryCount + 1
    val maxRetries = downloadPreferences.autoDownloadMaxRetries().get()

    if (!errorType.canRetry || newRetryCount > maxRetries) {
        // Max retries exceeded or non-retryable error
        database.downloadQueries.updateStatus(
            id = entry.id,
            status = "FAILED",
            lastAttemptAt = System.currentTimeMillis(),
            lastErrorMessage = errorMessage,
            retryCount = newRetryCount
        )
    } else {
        // Can retry - update for exponential backoff
        database.downloadQueries.updateForRetry(
            id = entry.id,
            status = "PENDING",
            lastAttemptAt = System.currentTimeMillis(),
            lastErrorMessage = errorMessage,
            retryCount = newRetryCount
        )
    }
}
```

### 6. User Preferences & Configuration

**New Preferences:**
```kotlin
// /domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt

// Download worker interval
fun downloadWorkerInterval() = preferenceStore.getInt(
    "download_worker_interval",
    15 // minutes - default 15min
)

// Auto-download from reading history
fun autoDownloadFromReadingHistory() = preferenceStore.getBoolean(
    "auto_download_from_reading_history",
    false // Opt-in initially
)

fun autoDownloadReadingHistoryDays() = preferenceStore.getInt(
    "auto_download_reading_history_days",
    7 // Look back 7 days
)

// Retry configuration
fun autoDownloadMaxRetries() = preferenceStore.getInt(
    "auto_download_max_retries",
    5
)

// Cleanup configuration
fun cleanupOrphanedFoldersOnStartup() = preferenceStore.getBoolean(
    "cleanup_orphaned_folders_on_startup",
    true
)
```

**Settings UI:**
```kotlin
// /app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDownloadScreen.kt

// Add new section after existing download settings
Preference.PreferenceGroup(
    title = stringResource(MR.strings.pref_category_download_queue),
) {
    ListPreference(
        preference = downloadPreferences.downloadWorkerInterval(),
        title = stringResource(MR.strings.pref_download_worker_interval),
        subtitle = stringResource(MR.strings.pref_download_worker_interval_summary),
        entries = mapOf(
            15 to stringResource(MR.strings.update_15min),
            30 to stringResource(MR.strings.update_30min),
            60 to stringResource(MR.strings.update_60min),
            180 to stringResource(MR.strings.update_3hour),
            360 to stringResource(MR.strings.update_6hour),
        )
    )

    ListPreference(
        preference = downloadPreferences.autoDownloadMaxRetries(),
        title = stringResource(MR.strings.pref_max_download_retries),
        entries = mapOf(
            3 to "3",
            5 to "5 (Recommended)",
            10 to "10",
            999 to stringResource(MR.strings.unlimited)
        )
    )
}

Preference.PreferenceGroup(
    title = stringResource(MR.strings.pref_category_auto_download_advanced),
) {
    SwitchPreference(
        preference = downloadPreferences.autoDownloadFromReadingHistory(),
        title = stringResource(MR.strings.pref_auto_download_reading_history),
        subtitle = stringResource(MR.strings.pref_auto_download_reading_history_summary)
    )

    SliderPreference(
        preference = downloadPreferences.autoDownloadReadingHistoryDays(),
        title = stringResource(MR.strings.pref_reading_history_lookback),
        subtitle = stringResource(MR.strings.pref_reading_history_lookback_summary),
        min = 3,
        max = 30,
        enabled = downloadPreferences.autoDownloadFromReadingHistory().get()
    )
}

Preference.PreferenceGroup(
    title = stringResource(MR.strings.pref_category_storage_cleanup),
) {
    SwitchPreference(
        preference = downloadPreferences.cleanupOrphanedFoldersOnStartup(),
        title = stringResource(MR.strings.pref_cleanup_on_startup),
        subtitle = stringResource(MR.strings.pref_cleanup_on_startup_summary)
    )

    TextPreference(
        title = stringResource(MR.strings.pref_cleanup_now),
        subtitle = stringResource(MR.strings.pref_cleanup_now_summary),
        onClick = {
            scope.launch {
                TempFolderCleanupWorker.runOnce(context)
                // Show toast with result
            }
        }
    )
}
```

---

## Implementation Phases

### Phase 1: Database Foundation ⭐ CRITICAL (Week 1)

**Files to Create:**
- `/data/src/main/sqldelight/tachiyomi/data/downloads.sq` - SQL schema
- `/domain/src/main/java/tachiyomi/domain/download/model/DownloadQueueEntry.kt`
- `/domain/src/main/java/tachiyomi/domain/download/repository/DownloadQueueRepository.kt`
- `/domain/src/main/java/tachiyomi/domain/download/interactor/ValidateDownloadQueue.kt`

**Files to Modify:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadStore.kt` - Add migration
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt` - Load from database
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt` - Use repository

**Testing:**
- Add downloads → kill app → restart → verify queue restored
- Delete manga → verify queue entries auto-removed (foreign key)
- Stress test with 100+ downloads

### Phase 2: Persistent Worker ⭐ CRITICAL (Week 2)

**Files to Modify:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadJob.kt` - Refactor to periodic
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt` - Update scheduling
- `/app/src/main/java/eu/kanade/tachiyomi/App.kt` - Setup periodic worker

**Testing:**
- Queue downloads → turn off WiFi → turn on → verify resume
- Queue downloads → kill app → verify continues in background
- Monitor battery drain over 24 hours

### Phase 3: Smart Auto-Download (Week 3)

**Files to Create:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/AutoDownloadPollingWorker.kt`
- `/domain/src/main/java/tachiyomi/domain/download/interactor/GetChaptersForAutoDownload.kt`

**Files to Modify:**
- `/ui/reader/ReaderViewModel.kt:618-651` - Remove download requirement
- `/app/src/main/java/eu/kanade/tachiyomi/App.kt` - Setup polling worker

**Testing:**
- Read chapter → verify next chapters queued (without needing download)
- Set reading history to 7 days → verify polling downloads recent manga
- Disable auto-download → verify worker cancelled

### Phase 4: Temp Cleanup (Week 4)

**Files to Create:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/TempFolderCleanupWorker.kt`

**Files to Modify:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt:123` - Add startup cleanup
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt:366` - Delete stale temps
- `/app/src/main/java/eu/kanade/tachiyomi/App.kt` - Setup daily cleanup

**Testing:**
- Create temp folders → wait 1 hour → verify cleaned
- Crash during download → restart → verify temp cleaned
- Monitor storage before/after cleanup

### Phase 5: Error Handling (Week 5)

**Files to Modify:**
- `/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt` - Add error classification
- `/domain/src/main/java/tachiyomi/domain/download/repository/DownloadQueueRepository.kt` - Track failures

**Testing:**
- Fill storage → verify "Disk Full" error, no retries
- 404 error → verify removed from queue
- Network error → verify exponential backoff

### Phase 6: Preferences & UI (Week 6)

**Files to Modify:**
- `/domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt`
- `/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDownloadScreen.kt`

**Testing:**
- Toggle each preference → verify behavior changes
- Disable auto-download → verify worker cancelled
- Run manual cleanup → verify UI feedback

---

## Critical Files Reference

| Priority | File Path | Changes |
|----------|-----------|---------|
| ⭐ Critical | `/data/src/main/sqldelight/tachiyomi/data/downloads.sq` | **NEW** - Database schema |
| ⭐ Critical | `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadStore.kt` | Line 112: Remove `clear()`, add migration |
| ⭐ Critical | `/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt` | Line 123: Load from DB; Line 366: Delete stale temps; Add cleanup |
| ⭐ Critical | `/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadJob.kt` | Complete refactor to periodic worker |
| 🔥 High | `/ui/reader/ReaderViewModel.kt` | Line 623: Remove download requirement; Add priority |
| 🔥 High | `/domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt` | Add new preferences |

---

## Verification & Testing Strategy

### Automated Tests
```kotlin
// DownloadQueueRepositoryTest.kt
@Test
fun testQueuePersistence() {
    // Add downloads
    repository.add(mangaId = 1, chapterId = 1)
    repository.add(mangaId = 1, chapterId = 2)

    // Simulate app restart
    recreateRepository()

    // Verify queue restored
    val queue = repository.getPending()
    assertEquals(2, queue.size)
}

@Test
fun testExponentialBackoff() {
    val entry = repository.add(mangaId = 1, chapterId = 1)

    // Record failures
    repeat(3) {
        repository.recordFailure(entry.chapterId, "Error", NETWORK_ERROR)
    }

    // Verify backoff delays increase
    val delays = (0..3).map { calculateBackoffDelay(it) }
    assertTrue(delays[1] > delays[0])
    assertTrue(delays[2] > delays[1])
    assertTrue(delays[3] > delays[2])
}
```

### Manual Test Plan
1. **Queue Persistence**
   - Add 10 downloads → Force stop app → Restart → Verify all 10 restored

2. **Auto-Download Without Downloads**
   - Read online chapter → Verify next chapters queued (key improvement!)

3. **Background Resilience**
   - Queue downloads → Turn off WiFi → Wait → Turn on WiFi → Verify resume

4. **Temp Cleanup**
   - Check storage before cleanup → Run cleanup → Check storage after → Verify freed space

5. **Error Handling**
   - Fill storage → Attempt download → Verify no infinite retries

---

## Success Metrics

| Issue | Before | After | Target |
|-------|--------|-------|--------|
| Queue Reset | 100% loss on restart | 0% loss | 0% |
| Auto-Download | ~10% success (requires download) | ~90% success | >80% |
| Temp Folders | Can grow to 1GB+ | <10MB at any time | <50MB |
| Background Downloads | Requires foreground | Works in background | >95% |
| Retry Logic | Infinite retries on error | Exponential backoff | Max 6hr delay |

---

## Risk Assessment

### High Risk Items
1. **Database Migration** - Mitigate: Keep SharedPreferences fallback for 2 versions
2. **Battery Drain** - Mitigate: Conservative 15min intervals, battery constraints
3. **Performance Impact** - Mitigate: Indexed queries, cleanup old entries

### Medium Risk Items
1. **Backoff Too Aggressive** - Mitigate: User-configurable max retries
2. **Auto-Download Too Aggressive** - Mitigate: Opt-in, configurable days back

### Low Risk Items
1. **Temp Cleanup** - Safe, only deletes folders >1 hour old
2. **Priority System** - Additive feature, no breaking changes

---

## Next Steps After Plan Approval

1. Create `/plans` folder in codebase and copy this plan there
2. Start with Phase 1 (Database Foundation)
3. Write unit tests alongside implementation
4. Test each phase thoroughly before moving to next
5. Monitor battery/performance metrics during development
6. Create feature flag for gradual rollout

---

## Summary

This optimized plan goes beyond the GitHub issue pseudocode by providing:
- **Production-ready database schema** with foreign keys and indexes
- **WorkManager integration** for reliable background execution
- **Smart reading history-based polling** (not just reader-triggered)
- **Multi-point cleanup strategy** with safety checks
- **Comprehensive error handling** with classification and backoff
- **User-configurable preferences** for all features
- **Phased implementation** with testing at each step

**Key Optimizations:**
1. Database > SharedPreferences for reliability
2. Periodic worker > One-time job for persistence
3. Reading history polling > Reader-only triggering for coverage
4. Multi-point cleanup > Single-point for completeness
5. Error classification > Generic handling for intelligence

The end result: A download system that actually works in the background, survives app restarts, cleans up after itself, and doesn't require constant babysitting.
