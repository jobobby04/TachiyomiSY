package eu.kanade.tachiyomi.data.backup.offline.models

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
class BackupCategory(
    @ProtoNumber(0) var name: String,
    @ProtoNumber(1) var order: Int,
    @ProtoNumber(2) var flags: Int,
    @ProtoNumber(3) var mangaOrder: List<Long> = emptyList()
) {
    fun getCategoryImpl(): CategoryImpl {
        return CategoryImpl().apply {
            name = this@BackupCategory.name
            flags = this@BackupCategory.flags
            order = this@BackupCategory.order
            mangaOrder = this@BackupCategory.mangaOrder
        }
    }

    companion object {
        fun copyFrom(category: Category): BackupCategory {
            return BackupCategory(
                name = category.name,
                order = category.order,
                flags = category.flags,
                mangaOrder = category.mangaOrder
            )
        }
    }
}
