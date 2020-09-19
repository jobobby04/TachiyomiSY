package eu.kanade.tachiyomi.data.backup.offline.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Backup json model
 */
@ExperimentalSerializationApi
@Serializable
data class Backup(
    @ProtoNumber(0) val backupManga: List<BackupManga>,
    @ProtoNumber(1) var backupCategories: List<BackupCategory>? = null,
    @ProtoNumber(2) var backupExtensions: List<BackupSource> = emptyList(),
    @ProtoNumber(3) var backupSavedSearches: List<BackupSavedSearch>? = null
)
