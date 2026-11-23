package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Backup(
    // SY -->
    @EncodeDefault
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    // SY <--
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensionStores: List<BackupExtensionStore> = emptyList(),
    // SY -->
    // SY specific values
    @ProtoNumber(600) var backupSavedSearches: List<BackupSavedSearch> = emptyList(),
    @ProtoNumber(601) var backupFeeds: List<BackupFeed> = emptyList(),
    // SY <--
    // SyncYomi v2 delta tombstones; 11000 is in the root-envelope band SyncYomi reserves for itself.
    // A full snapshot and an old server never set it, so it stays empty there.
    @ProtoNumber(11000) var deletions: List<BackupDeletedItem> = emptyList(),
)
