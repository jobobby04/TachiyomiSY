package eu.kanade.tachiyomi.data.backup.full.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Backup json model
 */
@ExperimentalSerializationApi
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(3) var backupExtensions: List<BackupSource> = emptyList(),
    // SY specific values
    @ProtoNumber(60) var backupSavedSearches: List<BackupSavedSearch> = emptyList()
)
