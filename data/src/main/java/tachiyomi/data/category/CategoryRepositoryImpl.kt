package tachiyomi.data.category

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.category.CategoryMapper.mapCategory
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.model.SyncedCategory
import tachiyomi.domain.category.repository.CategoryRepository
import kotlin.math.max

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CategoryRepositoryImpl(
    private val database: Database,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return database.categoryQueries
            .getCategory(id, ::mapCategory)
            .awaitAsOneOrNull()
    }

    override suspend fun getAll(): List<Category> {
        return database.categoryQueries
            .getCategories(::mapCategory)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return database.categoryQueries
            .getCategories(::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaIds(mangaIds: List<Long>): Map<Long, List<Category>> {
        return database.categoryQueries
            .getCategoriesByMangaIds(
                mangaIds,
            ) { mangaId, id, name, order, flags, /* SY --> */ version, uid, lastModifiedAt, isSyncing /* SY <-- */ ->
                mangaId to mapCategory(id, name, order, flags, /* SY --> */ version, uid, lastModifiedAt, isSyncing /* SY <-- */)
            }
            .awaitAsList()
            .groupBy({ it.first }, { it.second })
    }

    override suspend fun insert(category: NewCategory) {
        database.categoryQueries.insert(name = category.name, flags = category.flags)
    }

    // SY -->
    override suspend fun applySynced(categories: List<SyncedCategory>) {
        database.transaction {
            val local = database.categoryQueries.getCategories(::mapCategory).awaitAsList()
                .filterNot { it.isSystemCategory }
            local.forEachIndexed { index, category ->
                database.categoryQueries.updateOrderWhileSyncing(order = PARKED_ORDER_START - index, id = category.id)
            }
            val localByUid = local.associateBy { it.uid }
            val localByName = local.associateBy { it.name }
            val syncedIds = mutableSetOf<Long>()
            var nextOrder = 0L
            categories.sortedBy { it.order }.forEach { category ->
                val order = max(category.order, nextOrder)
                nextOrder = order + 1
                val match = localByUid[category.uid].takeIf { category.uid != 0L } ?: localByName[category.name]
                if (match != null) {
                    syncedIds += match.id
                    database.categoryQueries.updateSync(
                        name = category.name,
                        order = order,
                        flags = category.flags,
                        stateVersion = category.version,
                        stateUid = category.uid.takeIf { it != 0L } ?: match.uid,
                        stateLastModifiedAt = category.lastModifiedAt,
                        id = match.id,
                    )
                } else {
                    database.categoryQueries.insertWithOrder(
                        name = category.name,
                        order = order,
                        flags = category.flags,
                        stateVersion = category.version,
                        stateUid = category.uid,
                        stateLastModifiedAt = category.lastModifiedAt,
                    ).awaitAsOne()
                }
            }
            local.filterNot { it.id in syncedIds }.forEach { category ->
                database.categoryQueries.updateOrderWhileSyncing(order = nextOrder++, id = category.id)
            }
        }
    }

    override suspend fun resetIsSyncing() {
        database.categoryQueries.resetIsSyncing()
    }

    override suspend fun deleteAll(categoryIds: List<Long>) {
        database.transaction {
            categoryIds.forEach { database.categoryQueries.delete(it) }
        }
    }
    // SY <--

    override suspend fun insertAll(categories: List<NewCategory>) {
        database.transaction {
            categories.forEach { insert(it) }
        }
    }

    override suspend fun updateName(categoryId: Long, name: String) {
        database.categoryQueries.updateName(name = name, id = categoryId)
    }

    override suspend fun updateFlags(categoryId: Long, flags: Long) {
        database.categoryQueries.updateFlags(flags = flags, id = categoryId)
    }

    override suspend fun updateAllFlags(flags: Long) {
        database.categoryQueries.updateAllFlags(flags = flags)
    }

    override suspend fun updateAllOrders(orderedIds: List<Long>) {
        database.transaction {
            val current = database.categoryQueries.getUserCategoryIds().awaitAsList()
            val ids = orderedIds.filter { it in current } + current.filterNot { it in orderedIds }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = -index - 2L, id = categoryId)
            }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = index.toLong(), id = categoryId)
            }
        }
    }

    override suspend fun delete(categoryId: Long) {
        database.categoryQueries.delete(id = categoryId)
    }
}

// SY -->
private const val PARKED_ORDER_START = -2L
// SY <--
