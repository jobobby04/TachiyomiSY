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
    // Proto number 3 is skipped because its a 1.x value that is not used in 0.x
    @ProtoNumber(4) var flags: Int = 0,
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
