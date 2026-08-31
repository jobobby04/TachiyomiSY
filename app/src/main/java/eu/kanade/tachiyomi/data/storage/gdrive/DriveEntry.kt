package eu.kanade.tachiyomi.data.storage.gdrive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single file or folder as returned by the Drive v3 `files` resource. */
@Serializable
data class DriveEntry(
    val id: String,
    val name: String? = null,
    val mimeType: String? = null,
    val size: String? = null,
    @SerialName("modifiedTime") val modifiedTime: String? = null,
    val trashed: Boolean = false,
    val parents: List<String>? = null,
) {
    val isFolder: Boolean get() = mimeType == FOLDER_MIME

    val lengthBytes: Long get() = size?.toLongOrNull() ?: 0L

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}

@Serializable
data class DriveFileList(
    val files: List<DriveEntry> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class DriveAbout(
    val storageQuota: DriveStorageQuota? = null,
    val user: DriveUser? = null,
)

@Serializable
data class DriveStorageQuota(
    val limit: String? = null,
    val usage: String? = null,
    val usageInDrive: String? = null,
    val usageInDriveTrash: String? = null,
) {
    val totalBytes: Long get() = limit?.toLongOrNull() ?: 0L
    val usedBytes: Long get() = usage?.toLongOrNull() ?: 0L
    val availableBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
}

@Serializable
data class DriveUser(
    val displayName: String? = null,
    val emailAddress: String? = null,
)
