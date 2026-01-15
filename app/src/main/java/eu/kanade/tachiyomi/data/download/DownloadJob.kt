package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * This worker manages the downloader with periodic execution and exponential backoff.
 * Survives app restarts and automatically retries on network availability.
 */
class DownloadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val downloadManager: DownloadManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Executes the download job: verifies network requirements, starts the downloader, promotes the worker to foreground while active, and keeps it running until the downloader finishes or network conditions prevent progress.
     *
     * The worker will evaluate current network state against user preference (only-WiFi), attempt to start the downloader, and observe network/preference changes while running. If the job cannot start due to network conditions it will signal WorkManager to retry; otherwise it completes successfully when the downloader stops or the worker is stopped.
     *
     * @return `Result.retry()` if the job could not start because of network conditions, `Result.success()` otherwise.
     */
    override suspend fun doWork(): Result {
        var networkCheck = checkNetworkState(
            applicationContext.activeNetworkState(),
            downloadPreferences.downloadOnlyOverWifi().get(),
        )
        var active = networkCheck && downloadManager.downloaderStart()

        if (!active) {
            // Return retry for network issues so WorkManager can retry with backoff
            return if (!networkCheck) Result.retry() else Result.success()
        }

        setForegroundSafely()

        coroutineScope {
            combineTransform(
                applicationContext.networkStateFlow(),
                downloadPreferences.downloadOnlyOverWifi().changes(),
                transform = { a, b -> emit(checkNetworkState(a, b)) },
            )
                .onEach { networkCheck = it }
                .launchIn(this)
        }

        // Keep the worker running when needed
        while (active) {
            active = !isStopped && downloadManager.isRunning && networkCheck
        }

        return Result.success()
    }

    private fun checkNetworkState(state: NetworkState, requireWifi: Boolean): Boolean {
        return if (state.isOnline) {
            val noWifi = requireWifi && !state.isWifi
            if (noWifi) {
                downloadManager.downloaderStop(
                    applicationContext.getString(R.string.download_notifier_text_only_wifi),
                )
            }
            !noWifi
        } else {
            downloadManager.downloaderStop(applicationContext.getString(R.string.download_notifier_no_network))
            false
        }
    }

    companion object {
        private const val TAG = "Downloader"

        /**
         * Schedule or update the periodic DownloadJob using user preferences or an explicit override.
         *
         * If `intervalMinutes` is 0, any existing periodic download work is cancelled.
         *
         * @param intervalMinutes Optional override for the repeat interval in minutes; when null the preference value is used.
         */
        fun setupPeriodicWork(context: Context, intervalMinutes: Int? = null) {
            val preferences = Injekt.get<DownloadPreferences>()
            val interval = intervalMinutes ?: preferences.downloadWorkerInterval().get()

            if (interval == 0) {
                // User disabled periodic downloads
                WorkManager.getInstance(context).cancelUniqueWork(TAG)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (preferences.downloadOnlyOverWifi().get()) {
                        NetworkType.UNMETERED
                    } else {
                        NetworkType.CONNECTED
                    },
                )
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DownloadJob>(
                repeatInterval = interval.toLong(),
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 5,
                flexTimeIntervalUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    2, // Start with 2 minutes
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
         * Enqueues a one-time download job to run immediately.
         *
         * The job is scheduled with network constraints that reflect the user's "download over Wi‑Fi only" preference
         * and replaces any existing queued download job with the same unique tag.
         */
        fun start(context: Context) {
            val preferences = Injekt.get<DownloadPreferences>()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (preferences.downloadOnlyOverWifi().get()) {
                        NetworkType.UNMETERED
                    } else {
                        NetworkType.CONNECTED
                    },
                )
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadJob>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        /**
         * Exposes whether a DownloadJob is currently running as a Flow.
         *
         * Observes WorkManager's unique work infos for the downloader and emits `true` when exactly one work item is in the RUNNING state, `false` otherwise.
         *
         * @return `true` if a DownloadJob is running, `false` otherwise.
         */
        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
