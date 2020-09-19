package eu.kanade.tachiyomi.data.backup.full

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.serialization.ExperimentalSerializationApi
import okio.buffer
import okio.gzip
import okio.source
import uy.kohesive.injekt.injectLazy

@OptIn(ExperimentalSerializationApi::class)
object FullBackupRestoreValidator {

    private val sourceManager: SourceManager by injectLazy()
    private val trackManager: TrackManager by injectLazy()

    /**
     * Checks for critical backup file data.
     *
     * @throws Exception if version or manga cannot be found.
     * @return List of missing sources or missing trackers.
     */
    fun validate(context: Context, uri: Uri): BackupRestoreValidator.Results {
        val backupManager = FullBackupManager(context)

        val backupString = context.contentResolver.openInputStream(uri)!!.source().gzip().buffer().use { it.readByteArray() }
        val backup = backupManager.parser.decodeFromByteArray(BackupSerializer, backupString)

        if (backup.backupManga.isEmpty()) {
            throw Exception(context.getString(R.string.invalid_backup_file_missing_manga))
        }

        val sources = backup.backupExtensions.map { it.sourceId to it.name }.toMap()
        val missingSources = sources
            .filter { sourceManager.get(it.key) == null }
            .values
            .sorted()

        val trackers = backup.backupManga
            .flatMap { it.tracking }
            .map { it.syncId }
            .distinct()
        val missingTrackers = trackers
            .mapNotNull { trackManager.getService(it) }
            .filter { !it.isLogged }
            .map { it.name }
            .sorted()

        return BackupRestoreValidator.Results(missingSources, missingTrackers)
    }
}
