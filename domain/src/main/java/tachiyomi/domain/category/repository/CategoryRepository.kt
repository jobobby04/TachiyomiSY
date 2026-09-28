package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory

interface CategoryRepository {

    suspend fun get(id: Long): Category?

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByMangaId(mangaId: Long): List<Category>

    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    suspend fun insert(category: NewCategory)

    // SY -->
    suspend fun insertWithSync(
        name: String,
        order: Long,
        flags: Long,
        version: Long,
        uid: Long,
        lastModifiedAt: Long,
    ): Long

    suspend fun updateSync(
        name: String,
        order: Long,
        flags: Long,
        version: Long,
        uid: Long,
        lastModifiedAt: Long,
        categoryId: Long,
    )

    suspend fun resetIsSyncing()

    suspend fun deleteAll(categoryIds: List<Long>)
    // SY <--

    suspend fun insertAll(categories: List<NewCategory>)

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateFlags(categoryId: Long, flags: Long)

    suspend fun updateAllFlags(flags: Long)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    suspend fun delete(categoryId: Long)
}
