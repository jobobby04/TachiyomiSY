package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupSortedScanlator(
    @ProtoNumber(1) val scanlator: String,
    @ProtoNumber(2) val rank: Long,
)
