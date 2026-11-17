package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * SY-specific file
 *
 * One explicit tombstone a SyncYomi v2 delta carries in `Backup.deletions` (field 11000): the kind of sync
 * item, its merge key and the `seq` at which the server recorded the deletion.
 */
@Serializable
data class BackupDeletedItem(
    @ProtoNumber(1) val kind: String = "",
    @ProtoNumber(2) val key: String = "",
    @ProtoNumber(3) val seq: Long = 0,
)
