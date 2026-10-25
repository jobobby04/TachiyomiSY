package eu.kanade.tachiyomi.ui.category.genre

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.manga.interactor.CreateSortTag
import eu.kanade.domain.manga.interactor.DeleteSortTag
import eu.kanade.domain.manga.interactor.GetSortTag
import eu.kanade.domain.manga.interactor.ReorderSortTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import kotlin.time.Duration.Companion.seconds

/**
 * SY-specific file
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SortTagViewModel(
    private val getSortTag: GetSortTag,
    private val createSortTag: CreateSortTag,
    private val deleteSortTag: DeleteSortTag,
    private val reorderSortTag: ReorderSortTag,
) : ViewModel() {

    private val _events: Channel<SortTagEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<SortTagDialog?>(null)

    val state: StateFlow<SortTagScreenState> = combine(
        getSortTag.subscribe(),
        dialog,
    ) { tags, dialog ->
        SortTagScreenState.Success(
            tags = tags,
            dialog = dialog,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), SortTagScreenState.Loading)

    fun createTag(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (createSortTag.await(name)) {
                is CreateSortTag.Result.TagExists -> _events.send(SortTagEvent.TagExists)
                else -> {}
            }
        }
    }

    fun delete(tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteSortTag.await(tag)
        }
    }

    fun moveUp(tag: String, index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            when (reorderSortTag.await(tag, index - 1)) {
                is ReorderSortTag.Result.InternalError -> _events.send(SortTagEvent.InternalError)
                else -> {}
            }
        }
    }

    fun moveDown(tag: String, index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            when (reorderSortTag.await(tag, index + 1)) {
                is ReorderSortTag.Result.InternalError -> _events.send(SortTagEvent.InternalError)
                else -> {}
            }
        }
    }

    fun showDialog(dialog: SortTagDialog) {
        this.dialog.value = dialog
    }

    fun dismissDialog() {
        this.dialog.value = null
    }
}

sealed class SortTagEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : SortTagEvent()
    data object TagExists : LocalizedMessage(SYMR.strings.error_tag_exists)
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed class SortTagDialog {
    data object Create : SortTagDialog()
    data class Delete(val tag: String) : SortTagDialog()
}

sealed class SortTagScreenState {

    @Immutable
    data object Loading : SortTagScreenState()

    @Immutable
    data class Success(
        val tags: List<String>,
        val dialog: SortTagDialog? = null,
    ) : SortTagScreenState() {

        val isEmpty: Boolean
            get() = tags.isEmpty()
    }
}
