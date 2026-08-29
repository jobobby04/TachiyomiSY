package tachiyomi.data.category

import tachiyomi.domain.category.model.Category

object CategoryMapper {
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        version: Long,
        uid: Long,
        lastModifiedAt: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            version = version,
            uid = uid,
            lastModifiedAt = lastModifiedAt,
        )
    }
}
