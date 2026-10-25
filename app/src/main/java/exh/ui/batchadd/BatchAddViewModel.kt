package exh.ui.batchadd

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.log.xLogE
import exh.source.ExhPreferences
import exh.util.trimOrNull
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.sy.SYMR

/**
 * SY-specific file
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class BatchAddViewModel(
    private val exhPreferences: ExhPreferences,
    private val galleryAdder: GalleryAdder,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    fun addGalleries(context: Context) {
        val galleries = state.value.galleries
        // Check text box has content
        if (galleries.isBlank()) {
            state.update { it.copy(dialog = Dialog.NoGalleriesSpecified) }
            return
        }

        addGalleries(context, galleries)
    }

    private fun addGalleries(context: Context, galleries: String) {
        val splitGalleries = if (ehVisitedRegex.containsMatchIn(galleries)) {
            val url = if (exhPreferences.enableExhentai.get()) {
                "https://exhentai.org/g/"
            } else {
                "https://e-hentai.org/g/"
            }
            ehVisitedRegex.findAll(galleries).map { galleryKeys ->
                val linkParts = galleryKeys.value.split(".")
                url + linkParts[0] + "/" + linkParts[1].replace(":", "")
            }.toList()
        } else {
            galleries.split("\n")
                .mapNotNull(String::trimOrNull)
        }

        state.update { state ->
            state.copy(
                progress = 0,
                progressTotal = splitGalleries.size,
                addingState = AddingState.PROGRESS,
            )
        }

        val handler = CoroutineExceptionHandler { _, throwable ->
            xLogE("Batch add error", throwable)
        }

        viewModelScope.launch(Dispatchers.IO + handler) {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<String>()

            splitGalleries.forEachIndexed { i, s ->
                ensureActive()
                val result = withContext(Dispatchers.IO) {
                    galleryAdder.addGallery(
                        context = context,
                        url = s,
                        fav = true,
                        retry = 2,
                    )
                }
                if (result is GalleryAddEvent.Success) {
                    succeeded.add(s)
                } else {
                    failed.add(s)
                }
                state.update { state ->
                    state.copy(
                        progress = i + 1,
                        events = state.events.plus(
                            when (result) {
                                is GalleryAddEvent.Success -> context.stringResource(SYMR.strings.batch_add_ok)
                                is GalleryAddEvent.Fail -> context.stringResource(SYMR.strings.batch_add_error)
                            } + " " + result.logMessage,
                        ),
                    )
                }
            }

            // Show report
            val summary = context.stringResource(SYMR.strings.batch_add_summary, succeeded.size, failed.size)
            state.update { state ->
                state.copy(
                    events = state.events + summary,
                )
            }
        }
    }

    fun finish() {
        state.update { state ->
            state.copy(
                progressTotal = 0,
                progress = 0,
                galleries = "",
                addingState = AddingState.INPUT,
                events = emptyList(),
            )
        }
    }

    fun updateGalleries(galleries: String) {
        state.update { it.copy(galleries = galleries) }
    }

    fun dismissDialog() {
        state.update { it.copy(dialog = null) }
    }

    enum class AddingState {
        INPUT,
        PROGRESS,
    }

    sealed class Dialog {
        object NoGalleriesSpecified : Dialog()
    }

    data class State(
        val progressTotal: Int = 0,
        val progress: Int = 0,
        val galleries: String = "",
        val addingState: AddingState = AddingState.INPUT,
        val events: List<String> = emptyList(),
        val dialog: Dialog? = null,
    )

    companion object {
        val ehVisitedRegex = """[0-9]*?\.[a-z0-9]*?:""".toRegex()
    }
}
