package tachiyomi.data.category

import tachiyomi.domain.category.model.Category

/**
 * SY-specific file
 */
object CategoryMapper {
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        // SY -->
        stateVersion: Long,
        stateUid: Long,
        stateLastModifiedAt: Long,
        @Suppress("UNUSED_PARAMETER")
        stateIsSyncing: Long,
        // SY <--
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            // SY -->
            version = stateVersion,
            uid = stateUid,
            lastModifiedAt = stateLastModifiedAt,
            // SY <--
        )
    }
}
