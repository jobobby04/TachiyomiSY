package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    // SY -->
    @ProtoNumber(2) var sourceId: Long = 0,
    // SY <--
)
