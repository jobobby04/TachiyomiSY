package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.Uri
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreWorker
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.sync.service.BackupRequestBody
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncResult
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okio.buffer
import okio.sink
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.sync.repository.SyncRepository
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis
import kotlin.time.Clock

/**
 * SY-specific file.
 *
 * A manager to handle synchronization tasks in the app, such as updating
 * sync preferences and performing synchronization with a remote server.
 *
 * @property context The application context.
 */
class SyncManager(
    private val context: Context,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
    private val categoryRepository: CategoryRepository,
    private val syncRepository: SyncRepository,
    private val syncPreferences: SyncPreferences,
    private var json: Json,
    private val protoBuf: ProtoBuf,
    private val getCategories: GetCategories,
    private val backupCreatorFactory: BackupCreator.Factory,
    private val notifier: SyncNotifier,
    private val mangaRestorerFactory: MangaRestorer.Factory,
) {
    private val backupCreator: BackupCreator = backupCreatorFactory.create(false)
    private val mangaRestorer: MangaRestorer = mangaRestorerFactory.create(true)

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        GOOGLE_DRIVE(2),
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with a sync service.
     *
     * This function retrieves local data (favorites, manga, extensions, and categories)
     * from the database using the BackupManager, then synchronizes the data with a sync service.
     */
    suspend fun syncData() {
        // Epoch seconds. Everything modified before this instant is in this upload; the next delta starts here.
        val syncStart = Clock.System.now().epochSeconds

        // Reset isSyncing in case it was left over or failed syncing during restore.
        syncRepository.resetAllSyncing()

        val syncOptions = syncPreferences.getSyncSettings()

        val backupOptions = BackupOptions(
            libraryEntries = syncOptions.libraryEntries,
            categories = syncOptions.categories,
            chapters = syncOptions.chapters,
            tracking = syncOptions.tracking,
            history = syncOptions.history,
            extensionStores = syncOptions.extensionStores,
            appSettings = syncOptions.appSettings,
            sourceSettings = syncOptions.sourceSettings,
            privateSettings = syncOptions.privateSettings,

            // SY -->
            customInfo = syncOptions.customInfo,
            readEntries = syncOptions.readEntries,
            savedSearches = syncOptions.savedSearches,
            // SY <--
        )

        // Handle sync based on the selected service
        val syncService = when (val syncService = SyncService.fromInt(syncPreferences.syncService.get())) {
            SyncService.SYNCYOMI -> {
                SyncYomiSyncService(
                    context,
                    json,
                    syncPreferences,
                    notifier,
                    protoBuf,
                )
            }

            SyncService.GOOGLE_DRIVE -> {
                GoogleDriveSyncService(context, json, syncPreferences, protoBuf)
            }

            else -> {
                logcat(LogPriority.ERROR) { "Invalid sync service type: $syncService" }
                null
            }
        }

        // Whether to upload the whole library or only what changed since the last successful sync (SyncYomi v2).
        val full = try {
            syncService?.needsFullSync() ?: true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to probe sync server" }
            notifier.showSyncError(e.message)
            return
        }

        val databaseManga = if (full) {
            getAllMangaThatNeedsSync()
        } else {
            getMangasModifiedSince(syncPreferences.lastPushedAt.get())
        }

        logcat(LogPriority.DEBUG) { "Begin create backup (full=$full)" }
        val backupManga = backupCreator.backupMangas(databaseManga, backupOptions)
            .toList()
            .let { if (full) it else changedSince(it, syncPreferences.lastPushedAt.get()) }
        val backup = Backup(
            backupManga = backupManga,
            backupCategories = backupCreator.backupCategories(backupOptions),
            backupSources = backupCreator.backupSources(backupManga.mapTo(mutableSetOf()) { it.source }),
            backupPreferences = backupCreator.backupAppPreferences(backupOptions),
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
            backupExtensionStores = backupCreator.backupExtensionStores(backupOptions),

            // SY -->
            backupSavedSearches = backupCreator.backupSavedSearches(backupOptions),
            // SY <--
        )
        logcat(LogPriority.DEBUG) { "End create backup" }

        // Create the SyncData object
        val syncData = SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = backup,
        )

        val result: SyncResult = syncService?.doSync(syncData, full) ?: return
        val remoteBackup = result.backup

        if (remoteBackup == null) {
            logcat(LogPriority.DEBUG) { "Skip restore due to network issues" }
            // should we call showSyncError?
            return
        }

        if (remoteBackup === syncData.backup || (result.protocolV2 && !result.changed)) {
            // nothing changed
            logcat(LogPriority.DEBUG) { "Skip restore, nothing new on the remote" }
            finishWithSuccess(syncStart, "Sync completed successfully")
            return
        }

        if (!result.protocolV2) {
            // Stop the sync early if the remote backup is null or empty
            if (remoteBackup.backupManga.isEmpty() &&
                remoteBackup.backupCategories.isEmpty() &&
                remoteBackup.backupSources.isEmpty()
            ) {
                notifier.showSyncError("No data found on remote server.")
                return
            }

            // Check if it's first sync based on lastSyncTimestamp
            if (syncPreferences.lastSyncTimestamp.get() == 0L && databaseManga.isNotEmpty()) {
                // It's first sync no need to restore data. (just update remote data)
                finishWithSuccess(syncStart, "Updated remote data successfully")
                return
            }
        }

        val changedManga = mangaToRestore(remoteBackup)
        // the restore below may not run, so its reset of the sync flag cannot be relied on
        syncRepository.resetMangaSyncing()

        val newSyncData = backup.copy(
            backupManga = changedManga,
            backupCategories = remoteBackup.backupCategories,
            // a v2 delta only carries changed sources
            backupSources = remoteBackup.backupSources.ifEmpty { backup.backupSources },
            backupPreferences = remoteBackup.backupPreferences,
            backupSourcePreferences = remoteBackup.backupSourcePreferences,
            backupExtensionStores = remoteBackup.backupExtensionStores,

            // SY -->
            backupSavedSearches = remoteBackup.backupSavedSearches,
            // SY <--
        )

        val hasMangaChanges = changedManga.isNotEmpty()
        val hasCategoryChanges = categoriesDiffer(backup.backupCategories, remoteBackup.backupCategories)
        // a v2 delta leaves out the sections nothing changed in; an absent section is not a change
        val hasSourceChanges = newSyncData.backupSources != backup.backupSources
        val hasPreferenceChanges = remoteBackup.backupPreferences.isNotEmpty() &&
            remoteBackup.backupPreferences != backup.backupPreferences
        val hasSourcePreferenceChanges = remoteBackup.backupSourcePreferences.isNotEmpty() &&
            remoteBackup.backupSourcePreferences != backup.backupSourcePreferences
        val hasExtensionRepoChanges = remoteBackup.backupExtensionStores.isNotEmpty() &&
            extensionStoresDiffer(backup.backupExtensionStores, remoteBackup.backupExtensionStores)
        val hasSavedSearchChanges = remoteBackup.backupSavedSearches.isNotEmpty() &&
            remoteBackup.backupSavedSearches != backup.backupSavedSearches

        if (!hasMangaChanges && !hasCategoryChanges && !hasSourceChanges &&
            !hasPreferenceChanges && !hasSourcePreferenceChanges &&
            !hasExtensionRepoChanges && !hasSavedSearchChanges
        ) {
            // update the sync timestamp
            finishWithSuccess(syncStart, "Sync completed successfully")
            return
        }

        if (syncOptions.categories) {
            val mergedUids = newSyncData.backupCategories.map { it.uid }.toSet()
            val mergedNames = newSyncData.backupCategories.map { it.name }.toSet()
            val localCategories = getCategories.await().filterNot { it.id == 0L } // Exclude system category
            val categoriesToDelete = localCategories.filter {
                it.uid !in mergedUids && it.name !in mergedNames
            }
            if (categoriesToDelete.isNotEmpty()) {
                categoryRepository.deleteAll(categoriesToDelete.map { it.id })
                categoriesToDelete.forEach { syncPreferences.rememberDeletedCategory(it.uid) }
            }
        }

        if (newSyncData.hasNothingToRestore()) {
            // the category deletions above were the whole change; an empty backup cannot be decoded
            finishWithSuccess(syncStart, "Sync completed successfully")
            return
        }

        val backupUri = writeSyncDataToCache(context, newSyncData)
        logcat(LogPriority.DEBUG) { "Got Backup Uri: $backupUri" }
        if (backupUri != null) {
            // Await the apply so a failed restore fails the sync instead of
            // silently reporting success with nothing written.
            val applied = BackupRestoreWorker.startAndAwaitSuccess(
                context.workManager,
                backupUri,
                sync = true,
                options = RestoreOptions(
                    appSettings = syncOptions.appSettings,
                    sourceSettings = syncOptions.sourceSettings,
                    libraryEntries = syncOptions.libraryEntries,
                    categories = syncOptions.categories,
                    extensionStores = syncOptions.extensionStores,
                    // SY -->
                    savedSearches = syncOptions.savedSearches,
                    // SY <--
                ),
            )
            if (applied) {
                // update the sync timestamp
                syncPreferences.lastSyncTimestamp.set(Date().time)
                syncPreferences.lastPushedAt.set(syncStart)
            } else {
                // The cursor already advanced past the data that failed to apply, so
                // ask the server for everything on the next run to self-heal.
                syncPreferences.fullSyncRequested.set(true)
                notifier.showSyncError("Failed to apply synced data; will retry with a full sync")
                logcat(LogPriority.ERROR) { "Sync restore failed; requesting full sync on next run" }
            }
        } else {
            logcat(LogPriority.ERROR) { "Failed to write sync data to file" }
        }
    }

    private fun finishWithSuccess(syncStart: Long, message: String) {
        syncPreferences.lastSyncTimestamp.set(Date().time)
        // everything modified before this instant reached the server; the next delta starts here
        syncPreferences.lastPushedAt.set(syncStart)
        notifier.showSyncSuccess(message)
    }

    private fun Backup.hasNothingToRestore() = backupManga.isEmpty() &&
        backupCategories.isEmpty() &&
        backupSources.isEmpty() &&
        backupPreferences.isEmpty() &&
        backupSourcePreferences.isEmpty() &&
        backupExtensionStores.isEmpty() &&
        backupSavedSearches.isEmpty()

    private fun writeSyncDataToCache(context: Context, backup: Backup): Uri? {
        val cacheFile = File(context.cacheDir, "tachiyomi_sync_data.proto.gz")
        return try {
            cacheFile.sink().buffer().use { BackupRequestBody(backup, protoBuf).writeTo(it) }
            Uri.fromFile(cacheFile)
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to write sync data to cache" }
            null
        }
    }

    /**
     * Retrieves all manga from the local database.
     *
     * @return a list of all manga stored in the database
     */
    private suspend fun getAllMangaFromDB(): List<Manga> {
        return mangaRepository.getAll()
    }

    private suspend fun getAllMangaThatNeedsSync(): List<Manga> {
        return mangaRepository.getMangasToSync(syncPreferences.lastPushedAt.get())
    }

    private suspend fun getMangasModifiedSince(since: Long): List<Manga> {
        return mangaRepository.getMangasModifiedSince(since)
    }

    private suspend fun localStateOf(manga: Manga): LocalMangaState {
        val chapters = chapterRepository.getChapterByMangaId(manga.id)
        val chapterUrlById = chapters.associate { it.id to it.url }
        return LocalMangaState(
            manga = manga,
            chapters = chapters,
            categoryOrders = getCategories.await(manga.id).mapTo(mutableSetOf()) { it.order },
            lastReadByChapterUrl = historyRepository.getHistoryByMangaId(manga.id)
                .mapNotNull { history ->
                    val url = chapterUrlById[history.chapterId] ?: return@mapNotNull null
                    history.readAt?.let { url to it.toEpochMilliseconds() }
                }
                .toMap(),
        )
    }

    private suspend fun mangaToRestore(backup: Backup): List<BackupManga> {
        val localMangaByKey = getAllMangaFromDB().associateBy { Pair(it.source, it.url) }
        val toRestore = mutableListOf<BackupManga>()

        val elapsedTimeMillis = measureTimeMillis {
            backup.backupManga.forEach { remoteManga ->
                val localManga = localMangaByKey[Pair(remoteManga.source, remoteManga.url)]
                val restore = when (localManga) {
                    null -> remoteManga.favorite
                    else -> needsRestore(localStateOf(localManga), remoteManga)
                }
                if (restore) toRestore.add(remoteManga)
            }
        }

        logcat(LogPriority.DEBUG, "mangaToRestore") {
            "Found ${toRestore.size} of ${backup.backupManga.size} manga to restore in ${elapsedTimeMillis}ms"
        }
        return toRestore
    }
}
