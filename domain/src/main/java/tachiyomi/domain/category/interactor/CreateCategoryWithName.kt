package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class CreateCategoryWithName(
    private val categoryRepository: CategoryRepository,
    private val preferences: LibraryPreferences,
) {

    private val initialFlags: Long
        get() {
            val sort = preferences.sortingMode.get()
            return sort.type.flag or sort.direction.flag
        }

    suspend fun await(name: String): Result {
        return try {
            categoryRepository.insert(NewCategory(name = name, flags = initialFlags))
            // SY -->
            val category = categoryRepository.getAll().lastOrNull()
            Result.Success(category ?: Category(id = 0, name = name, order = 0, flags = initialFlags))
            // SY <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        // SY -->
        data class Success(val category: Category) : Result

        // SY <--
        data class InternalError(val error: Throwable) : Result
    }
}
