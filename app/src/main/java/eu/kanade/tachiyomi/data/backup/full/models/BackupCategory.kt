package eu.kanade.tachiyomi.data.backup.full.models

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Int = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(100) var flags: Int = 0,
    // SY specific values
    @ProtoNumber(600) var mangaOrder: List<Long> = emptyList()
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
