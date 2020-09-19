package eu.kanade.tachiyomi.data.backup.offline

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.offline.models.BackupSerializer
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.serialization.ExperimentalSerializationApi
import uy.kohesive.injekt.injectLazy

@OptIn(ExperimentalSerializationApi::class)
object OfflineBackupRestoreValidator {

    private val sourceManager: SourceManager by injectLazy()
    private val trackManager: TrackManager by injectLazy()

    /**
     * Checks for critical backup file data.
     *
     * @throws Exception if version or manga cannot be found.
     * @return List of missing sources or missing trackers.
     */
    fun validate(context: Context, uri: Uri): Results {
        val backupManager = OfflineBackupManager(context)

        val backupString = context.contentResolver.openInputStream(uri)!!.readBytes()
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
            .filter { it.tracking != null }
            .flatMap { it.tracking!! }
            .map { it.syncId }
            .distinct()
        val missingTrackers = trackers
            .mapNotNull { trackManager.getService(it) }
            .filter { !it.isLogged }
            .map { it.name }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    data class Results(val missingSources: List<String>, val missingTrackers: List<String>)
}
