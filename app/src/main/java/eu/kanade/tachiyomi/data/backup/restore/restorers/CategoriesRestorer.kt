package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.model.SyncedCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

@AssistedInject
class CategoriesRestorer(
// SY -->
    @Assisted private val isSync: Boolean,
    private val categoryRepository: CategoryRepository,
    // SY <--
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
        if (backupCategories.isEmpty()) return

        val dbCategories = getCategories.await()

        // SY -->
        if (isSync) {
            categoryRepository.applySynced(
                backupCategories.map {
                    SyncedCategory(
                        name = it.name,
                        order = it.order,
                        flags = it.flags,
                        version = it.version,
                        uid = it.uid,
                        lastModifiedAt = it.lastModifiedAt,
                    )
                },
            )
            categoryRepository.resetIsSyncing()
            return
        }
        // SY <--

        val dbCategoryNames = dbCategories.mapTo(HashSet()) { it.name }

        val newCategories = backupCategories
            .filter { it.name !in dbCategoryNames }
            .sortedBy { it.order }
        categoryRepository.insertAll(newCategories.map { NewCategory(name = it.name, flags = it.flags) })

        val flags = buildSet {
            dbCategories.mapTo(this) { it.flags }
            newCategories.mapTo(this) { it.flags }
        }
        libraryPreferences.categorizedDisplaySettings.set(flags.size > 1)
    }
}
