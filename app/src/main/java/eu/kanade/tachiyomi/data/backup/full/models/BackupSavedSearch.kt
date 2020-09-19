package eu.kanade.tachiyomi.data.backup.full.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
data class BackupSavedSearch(
    @ProtoNumber(0) val name: String,
    @ProtoNumber(1) val query: String = "",
    @ProtoNumber(2) val filterList: String = "",
    @ProtoNumber(3) val source: Long = 0
)
