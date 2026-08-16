package eu.kanade.tachiyomi.ui.category.sources

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.source.interactor.CreateSourceCategory
import eu.kanade.domain.source.interactor.DeleteSourceCategory
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.RenameSourceCategory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SourceCategoryViewModel(
    private val getSourceCategories: GetSourceCategories,
    private val createSourceCategory: CreateSourceCategory,
    private val renameSourceCategory: RenameSourceCategory,
    private val deleteSourceCategory: DeleteSourceCategory,
) : ViewModel() {

    val state: StateFlow<SourceCategoryScreenState>
        field = MutableStateFlow<SourceCategoryScreenState>(SourceCategoryScreenState.Loading)

    private val _events: Channel<SourceCategoryEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            getSourceCategories.subscribe()
                .collectLatest { categories ->
                    state.update {
                        SourceCategoryScreenState.Success(
                            categories = categories,
                        )
                    }
                }
        }
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createCategory(name: String) {
        viewModelScope.launchIO {
            when (createSourceCategory.await(name)) {
                is CreateSourceCategory.Result.InvalidName -> _events.send(SourceCategoryEvent.InvalidName)
                else -> {}
            }
        }
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param categories The list of categories to delete.
     */
    fun deleteCategory(categories: String) {
        viewModelScope.launchIO {
            deleteSourceCategory.await(categories)
        }
    }

    /**
     * Renames a category.
     *
     * @param categoryOld The category to rename.
     * @param categoryNew The new name of the category.
     */
    fun renameCategory(categoryOld: String, categoryNew: String) {
        viewModelScope.launchIO {
            when (renameSourceCategory.await(categoryOld, categoryNew)) {
                is CreateSourceCategory.Result.InvalidName -> _events.send(SourceCategoryEvent.InvalidName)
                else -> {}
            }
        }
    }

    fun showDialog(dialog: SourceCategoryDialog) {
        state.update {
            when (it) {
                SourceCategoryScreenState.Loading -> it
                is SourceCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        state.update {
            when (it) {
                SourceCategoryScreenState.Loading -> it
                is SourceCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class SourceCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : SourceCategoryEvent()
    data object InvalidName : LocalizedMessage(SYMR.strings.invalid_category_name)
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed class SourceCategoryDialog {
    data object Create : SourceCategoryDialog()
    data class Rename(val category: String) : SourceCategoryDialog()
    data class Delete(val category: String) : SourceCategoryDialog()
}

sealed class SourceCategoryScreenState {

    @Immutable
    data object Loading : SourceCategoryScreenState()

    @Immutable
    data class Success(
        val categories: List<String>,
        val dialog: SourceCategoryDialog? = null,
    ) : SourceCategoryScreenState() {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
