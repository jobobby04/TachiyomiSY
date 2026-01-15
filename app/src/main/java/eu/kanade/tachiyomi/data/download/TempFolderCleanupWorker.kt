package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class TempFolderCleanupWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val downloadPreferences: DownloadPreferences = Injekt.get()

    /**
     * Triggers orphaned temporary folder cleanup when the worker runs and the "cleanup on startup" preference is enabled.
     *
     * The worker always reports success to WorkManager regardless of whether cleanup was performed or any items were removed.
     *
     * @return `Result.success()` indicating the work completed.
     */
    override suspend fun doWork(): Result {
        if (!downloadPreferences.cleanupOrphanedFoldersOnStartup().get()) return Result.success()
        val cleanedCount = cleanupOrphanedTempFolders()
        logcat(LogPriority.DEBUG) { "TempFolderCleanup: removed $cleanedCount orphaned temp folders" }
        return Result.success()
    }

    companion object {
        private const val TAG = "TempFolderCleanup"

        /**
         * Removes orphaned temporary download folders that are older than the specified age.
         *
         * @param maxAgeMillis Age threshold in milliseconds; folders last modified earlier than
         *                     (current time - maxAgeMillis) are considered orphaned and eligible for deletion.
         * @return The number of temporary folders deleted.
         */
        suspend fun cleanupOrphanedTempFolders(
            maxAgeMillis: Long = TimeUnit.HOURS.toMillis(1),
        ): Int = withContext(Dispatchers.IO) {
            val storageManager: StorageManager = Injekt.get()
            val downloadsDir = storageManager.getDownloadsDirectory() ?: return@withContext 0
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            return@withContext cleanupInDirectory(downloadsDir, cutoff)
        }

        /**
         * Schedules or cancels a periodic background task that cleans up orphaned temporary download folders.
         *
         * When enabled (either via the optional `enabled` parameter or the user's preference), enqueues a daily
         * PeriodicWorkRequest (with a 2-hour flex window and exponential backoff) for TempFolderCleanupWorker;
         * when not enabled, cancels any existing periodic work with the worker's unique tag.
         *
         * @param enabled If non-null, overrides the stored preference and controls whether the periodic cleanup is scheduled.
         */
        fun setupPeriodicWork(context: Context, enabled: Boolean? = null) {
            val preferences = Injekt.get<DownloadPreferences>()
            val isEnabled = enabled ?: preferences.cleanupOrphanedFoldersOnStartup().get()
            if (!isEnabled) {
                WorkManager.getInstance(context).cancelUniqueWork(TAG)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<TempFolderCleanupWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
                flexTimeInterval = 2,
                flexTimeIntervalUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.MINUTES,
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Recursively deletes orphaned temporary download folders under the given directory that are older than the cutoff.
         *
         * @param dir The directory to scan for temporary folders.
         * @param cutoffMillis Millisecond timestamp; folders with a positive `lastModified` earlier than this value will be removed.
         * @return The number of temporary folders successfully deleted.
         */
        private fun cleanupInDirectory(dir: UniFile, cutoffMillis: Long): Int {
            var cleaned = 0
            dir.listFiles().orEmpty().forEach { file ->
                val name = file.name.orEmpty()
                val lastMod = file.lastModified()
                if (name.endsWith(Downloader.TMP_DIR_SUFFIX) && lastMod > 0 && lastMod < cutoffMillis) {
                    // For directories, delete contents first before removing the directory itself
                    val deleted = if (file.isDirectory) {
                        deleteDirectoryRecursively(file)
                    } else {
                        file.delete()
                    }
                    if (deleted) {
                        cleaned += 1
                    }
                } else if (file.isDirectory) {
                    cleaned += cleanupInDirectory(file, cutoffMillis)
                }
            }
            return cleaned
        }

        /**
         * Recursively deletes all contents of a directory, then deletes the directory itself.
         *
         * @param dir The directory to delete.
         * @return `true` if the directory and all its contents were successfully deleted, `false` otherwise.
         */
        private fun deleteDirectoryRecursively(dir: UniFile): Boolean {
            var allDeleted = true
            // * Delete all files and subdirectories inside
            dir.listFiles().orEmpty().forEach { child ->
                if (child.isDirectory) {
                    if (!deleteDirectoryRecursively(child)) {
                        allDeleted = false
                        logcat(LogPriority.WARN, tag = TAG) {
                            "Failed to recursively delete directory: ${child.name}"
                        }
                    }
                } else {
                    if (!child.delete()) {
                        allDeleted = false
                        logcat(LogPriority.WARN, tag = TAG) {
                            "Failed to delete file: ${child.name}"
                        }
                    }
                }
            }
            // * Now the directory should be empty, so we can delete it
            val dirDeleted = dir.delete()
            if (!dirDeleted && allDeleted) {
                logcat(LogPriority.WARN, tag = TAG) {
                    "Failed to delete directory (contents were deleted): ${dir.name}"
                }
            }
            return allDeleted && dirDeleted
        }
    }
}
