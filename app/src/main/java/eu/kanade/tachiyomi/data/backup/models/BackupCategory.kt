package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.category.model.Category

@Serializable
class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    @ProtoNumber(3) var id: Long = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    @ProtoNumber(100) var flags: Long = 0,
    @ProtoNumber(101) var version: Long = 0,
    // SY specific values
    /*@ProtoNumber(600) var mangaOrder: List<Long> = emptyList(),*/
    @ProtoNumber(601) var uid: Long = 0,
    @ProtoNumber(602) var lastModifiedAt: Long = 0,
) {
    fun toCategory(id: Long) = Category(
        id = id,
        name = this@BackupCategory.name,
        flags = this@BackupCategory.flags,
        order = this@BackupCategory.order,
        version = this@BackupCategory.version,
        uid = this@BackupCategory.uid,
        lastModifiedAt = this@BackupCategory.lastModifiedAt,
        /*mangaOrder = this@BackupCategory.mangaOrder*/
    )
}

val backupCategoryMapper = { category: Category ->
    BackupCategory(
        id = category.id,
        name = category.name,
        order = category.order,
        flags = category.flags,
        version = category.version,
        uid = category.uid,
        lastModifiedAt = category.lastModifiedAt,
    )
}
