package exh.pagepreview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * SY-specific file
 */
@AssistedInject
class PagePreviewViewModel(
    @Assisted private val mangaId: Long,
    private val getPagePreviews: GetPagePreviews,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val sourceManager: SourceManager,
) : ViewModel() {

    val state: StateFlow<PagePreviewState>
        field = MutableStateFlow<PagePreviewState>(PagePreviewState.Loading)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): PagePreviewViewModel
    }

    private val page = MutableStateFlow(1)

    var pageDialogOpen by mutableStateOf(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val manga = getManga.await(mangaId)!!
            val chapter = getChaptersByMangaId.await(mangaId).minByOrNull { it.sourceOrder }
            if (chapter == null) {
                state.update {
                    PagePreviewState.Error(Exception("No chapters found"))
                }
                return@launch
            }
            val source = sourceManager.getOrStub(manga.source)
            page
                .onEach { page ->
                    when (
                        val previews = getPagePreviews.await(manga, source, page)
                    ) {
                        is GetPagePreviews.Result.Error -> state.update {
                            PagePreviewState.Error(previews.error)
                        }
                        is GetPagePreviews.Result.Success -> state.update {
                            when (it) {
                                PagePreviewState.Loading, is PagePreviewState.Error -> {
                                    PagePreviewState.Success(
                                        page,
                                        previews.pagePreviews,
                                        previews.hasNextPage,
                                        previews.pageCount,
                                        manga,
                                        chapter,
                                        source,
                                    )
                                }
                                is PagePreviewState.Success -> it.copy(
                                    page = page,
                                    pagePreviews = previews.pagePreviews,
                                    hasNextPage = previews.hasNextPage,
                                    pageCount = previews.pageCount,
                                )
                            }
                        }
                        GetPagePreviews.Result.Unused -> Unit
                    }
                }
                .catch { e ->
                    state.update {
                        PagePreviewState.Error(e)
                    }
                }
                .collect()
        }
    }

    fun moveToPage(page: Int) {
        this.page.value = page
    }
}

sealed class PagePreviewState {
    data object Loading : PagePreviewState()

    data class Success(
        val page: Int,
        val pagePreviews: List<PagePreview>,
        val hasNextPage: Boolean,
        val pageCount: Int?,
        val manga: Manga,
        val chapter: Chapter,
        val source: Source,
    ) : PagePreviewState()

    data class Error(val error: Throwable) : PagePreviewState()
}
