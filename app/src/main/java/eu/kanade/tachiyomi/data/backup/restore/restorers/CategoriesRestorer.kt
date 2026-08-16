package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.async.coroutines.awaitAsOne
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.Database
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.service.LibraryPreferences

@AssistedInject
class CategoriesRestorer(
    // SY -->
    @Assisted private val isSync: Boolean,
    // SY <--
    private val database: Database,
    private val getCategories: GetCategories,
    private val libraryPreferences: LibraryPreferences,
) {
    // SY -->
    @AssistedFactory
    fun interface Factory {
        fun create(isSync: Boolean): CategoriesRestorer
    }
    // SY <--

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            // SY -->
            val dbCategoriesByUid = dbCategories.associateBy { it.uid } // Map by UID
            // SY <--

            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val categories = backupCategories
                .sortedBy { it.order }
                // SY -->
                .map { backupCategory ->
                    var dbCategory = if (backupCategory.uid != 0L) {
                        dbCategoriesByUid[backupCategory.uid]
                    } else {
                        null
                    }

                    if (dbCategory == null) {
                        dbCategory = dbCategoriesByName[backupCategory.name]
                    }

                    if (dbCategory != null) {
                        database.categoriesQueries.updateSync(
                            name = backupCategory.name,
                            order = backupCategory.order,
                            flags = backupCategory.flags,
                            version = backupCategory.version,
                            uid = if (backupCategory.uid != 0L) backupCategory.uid else dbCategory.uid,
                            lastModifiedAt = backupCategory.lastModifiedAt,
                            categoryId = dbCategory.id,
                        )
                        return@map dbCategory
                    }

                    // SY -->
                    // A sync delta is a converging replica, not an import: new
                    // categories must land at the position the server holds or
                    // reorders never converge across devices.
                    val order = if (isSync) backupCategory.order else nextOrder++
                    // SY <--
                    database.categoriesQueries.insert(
                        backupCategory.name,
                        order,
                        backupCategory.flags,
                        backupCategory.version,
                        backupCategory.uid,
                        backupCategory.lastModifiedAt,
                    ).awaitAsOne()
                        .let { id -> backupCategory.toCategory(id).copy(order = order) }
                }
            // SY <--

            // SY -->
            database.categoriesQueries.resetIsSyncing()
            // SY <--

            libraryPreferences.categorizedDisplaySettings.set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
