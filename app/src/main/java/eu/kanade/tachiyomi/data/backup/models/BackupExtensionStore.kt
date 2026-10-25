package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.domain.extension.model.ExtensionStore

@Serializable
class BackupExtensionStore(
    @ProtoNumber(1) var indexUrl: String /* SY --> */ = "" /* SY <-- */,
    @ProtoNumber(2) var name: String /* SY --> */ = "" /* SY <-- */,
    @ProtoNumber(3) var badgeLabel: String? /* SY --> */ = null /* SY <-- */,
    @ProtoNumber(5) var signingKey: String /* SY --> */ = "" /* SY <-- */,
    @ProtoNumber(4) var contactWebsite: String /* SY --> */ = "" /* SY <-- */,
    @ProtoNumber(6) var contactDiscord: String? /* SY --> */ = null /* SY <-- */,
    @ProtoNumber(7) var isLegacy: Boolean? /* SY --> */ = null /* SY <-- */,
    @ProtoNumber(8) var extensionListUrl: String? /* SY --> */ = null /* SY <-- */,
)

val backupExtensionStoreMapper = { store: ExtensionStore ->
    BackupExtensionStore(
        indexUrl = store.indexUrl,
        name = store.name,
        badgeLabel = store.badgeLabel,
        signingKey = store.signingKey,
        contactWebsite = store.contact.website,
        contactDiscord = store.contact.discord,
        isLegacy = store.isLegacy,
        extensionListUrl = store.extensionListUrl,
    )
}
