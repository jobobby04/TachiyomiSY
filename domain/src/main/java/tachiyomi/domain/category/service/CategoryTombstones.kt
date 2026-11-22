package tachiyomi.domain.category.service

fun interface CategoryTombstones {
    suspend fun remember(uid: Long)
}
