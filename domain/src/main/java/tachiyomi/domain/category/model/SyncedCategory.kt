package tachiyomi.domain.category.model

data class SyncedCategory(
    val name: String,
    val order: Long,
    val flags: Long,
    val version: Long,
    val uid: Long,
    val lastModifiedAt: Long,
)
