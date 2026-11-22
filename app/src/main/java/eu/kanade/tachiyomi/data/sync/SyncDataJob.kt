package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.sync.repository.SyncRepository
import java.util.concurrent.TimeUnit

/**
 * SY-specific file
 */
class SyncDataJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var mangaRepository: MangaRepository

    @Inject private lateinit var chapterRepository: ChapterRepository

    @Inject private lateinit var historyRepository: HistoryRepository

    @Inject private lateinit var categoryRepository: CategoryRepository

    @Inject private lateinit var syncRepository: SyncRepository

    @Inject private lateinit var syncPreferences: SyncPreferences

    @Inject private lateinit var json: Json

    @Inject private lateinit var protoBuf: ProtoBuf

    @Inject private lateinit var getCategories: GetCategories

    @Inject private lateinit var backupCreatorFactory: BackupCreator.Factory

    @Inject private lateinit var notifier: SyncNotifier

    @Inject private lateinit var mangaRestorerFactory: MangaRestorer.Factory

    override suspend fun doWork(): Result {
        graph.inject(this)

        if (tags.contains(TAG_AUTO)) {
            if (!context.isOnline()) {
                return Result.retry()
            }
            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(TAG_MANUAL)) {
                return Result.retry()
            }
        }

        setForegroundSafely()

        return try {
            SyncManager(context, mangaRepository, chapterRepository, historyRepository, categoryRepository, syncRepository, syncPreferences, json, protoBuf, getCategories, backupCreatorFactory, notifier, mangaRestorerFactory).syncData()
            Result.success()
        } catch (e: OutOfMemoryError) {
            logcat(LogPriority.ERROR, e)
            notifier.showSyncError("Not enough memory to sync this library")
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showSyncError(e.message)
            Result.success() // try again next time
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showSyncProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG_JOB = "SyncDataJob"
        private const val TAG_AUTO = "$TAG_JOB:auto"
        const val TAG_MANUAL = "$TAG_JOB:manual"

        suspend fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_JOB)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val syncPreferences = context.appGraph.syncPreferences
            val interval = prefInterval ?: syncPreferences.syncInterval.get()

            if (interval > 0) {
                val request = PeriodicWorkRequestBuilder<SyncDataJob>(
                    interval.toLong(),
                    TimeUnit.MINUTES,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG_JOB)
                    .addTag(TAG_AUTO)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        fun startNow(context: Context, manual: Boolean = false) {
            // Checking the running work queries WorkManager's database
            context.appGraph.appScope.launch {
                val wm = context.workManager
                if (wm.isRunning(TAG_JOB)) {
                    // Already running either as a scheduled or manual job
                    return@launch
                }
                val tag = if (manual) TAG_MANUAL else TAG_AUTO
                val request = OneTimeWorkRequestBuilder<SyncDataJob>()
                    .addTag(TAG_JOB)
                    .addTag(tag)
                    .build()
                wm.enqueueUniqueWork(tag, ExistingWorkPolicy.KEEP, request)
            }
        }

        suspend fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG_JOB, TAG_AUTO, TAG_MANUAL))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfosFlow(workQuery).first()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(TAG_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
