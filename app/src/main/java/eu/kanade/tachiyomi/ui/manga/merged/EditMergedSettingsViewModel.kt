package eu.kanade.tachiyomi.ui.manga.merged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.DeleteMergeById
import tachiyomi.domain.manga.interactor.GetMergeSettings
import tachiyomi.domain.manga.interactor.GetMergedEntriesById
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR

/**
 * SY-specific file
 */
@AssistedInject
class EditMergedSettingsViewModel(
    @Assisted private val mangaId: Long,
    private val getMergedMangaById: GetMergedMangaById,
    private val getMergedEntriesById: GetMergedEntriesById,
    private val getMergeSettings: GetMergeSettings,
    private val sourceManager: SourceManager,
    private val updateMergedSettings: UpdateMergedSettings,
    private val deleteMergeById: DeleteMergeById,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): EditMergedSettingsViewModel
    }

    sealed interface State {
        data object Loading : State

        data class Ready(
            val sortMode: Int,
            val infoMangaId: Long?,
            val entries: List<Entry>,
            val pendingConfirm: PendingConfirm?,
        ) : State {
            val isReorderEnabled: Boolean
                get() = MergeSettings.isReorderEnabled(sortMode)

            val infoIndex: Int
                get() = entries.indexOfFirst { it.entry.mangaId == infoMangaId }.coerceAtLeast(0)

            // A merge needs at least one underlying entry
            val isInvalid: Boolean
                get() = entries.isEmpty()
        }
    }

    data class Entry(
        val manga: Manga?,
        val entry: MergedEntry,
        val source: Source,
    )

    sealed class PendingConfirm(val title: StringResource, val text: StringResource) {
        data class Delete(val entry: MergedEntry) :
            PendingConfirm(SYMR.strings.delete_merged_entry, SYMR.strings.delete_merged_entry_desc)

        data class Downloads(val id: Long) :
            PendingConfirm(SYMR.strings.download_merged_entry, SYMR.strings.download_merged_entry_desc)

        data class Updates(val id: Long) :
            PendingConfirm(SYMR.strings.chapter_updates_merged_entry, SYMR.strings.chapter_updates_merged_entry_desc)
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Loads the merge's entries and underlying entries. Triggered by the dialog while
     * [state] is [State.Loading] — on first show and after a dismiss/save.
     */
    fun reload() {
        viewModelScope.launch {
            val manga = getMergedMangaById.await(mangaId)
            val entries = getMergedEntriesById.await(mangaId)
            val settings = getMergeSettings.await(mangaId)
            _state.value = buildReady(manga, entries, settings)
        }
    }

    /**
     * Marks the cached data as stale so the next show reloads it.
     */
    fun markStale() {
        _state.value = State.Loading
    }

    private suspend fun buildReady(
        manga: List<Manga>,
        entries: List<MergedEntry>,
        settings: MergeSettings?,
    ): State.Ready {
        val entries = entries
            .sortedBy { it.chapterPriority }
            .map { entry ->
                val entryManga = manga.firstOrNull { it.id == entry.mangaId }
                Entry(
                    manga = entryManga,
                    entry = entry,
                    source = sourceManager.getOrStub(entryManga?.source ?: -1),
                )
            }
        return State.Ready(
            sortMode = settings?.chapterSortMode ?: MergeSettings.CHAPTER_SORT_NONE,
            infoMangaId = settings?.infoMangaId,
            entries = entries,
            pendingConfirm = null,
        )
    }

    fun onSortModeChange(mode: Int) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            ready.copy(sortMode = mode)
        }
    }

    fun onInfoIndexChange(index: Int) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            ready.copy(infoMangaId = ready.entries.getOrNull(index)?.entry?.mangaId)
        }
    }

    /**
     * Moves an entry and rewrites the priority of every entry. [from] and [to] are indices into
     * [State.Ready.entries] (the list header is already excluded by the caller).
     */
    fun reorder(from: Int, to: Int) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            val moved = ready.entries.toMutableList()
            moved.add(to, moved.removeAt(from))
            ready.copy(
                entries = moved.mapIndexed { index, entry ->
                    entry.copy(entry = entry.entry.copy(chapterPriority = index))
                },
            )
        }
    }

    fun requestDelete(entry: MergedEntry) {
        setPendingConfirm(PendingConfirm.Delete(entry))
    }

    fun requestToggleDownloads(id: Long) {
        setPendingConfirm(PendingConfirm.Downloads(id))
    }

    fun requestToggleUpdates(id: Long) {
        setPendingConfirm(PendingConfirm.Updates(id))
    }

    fun dismissPending() {
        setPendingConfirm(null)
    }

    fun confirmPending() {
        val ready = _state.value as? State.Ready ?: return
        val confirm = ready.pendingConfirm ?: return
        when (confirm) {
            is PendingConfirm.Delete -> {
                setPendingConfirm(null)
                viewModelScope.launch {
                    deleteMergeById.await(confirm.entry.id)
                }
            }
            is PendingConfirm.Downloads -> {
                setPendingConfirm(null)
                updateEntry(confirm.id) { it.copy(downloadChapters = !it.downloadChapters) }
            }
            is PendingConfirm.Updates -> {
                setPendingConfirm(null)
                updateEntry(confirm.id) { it.copy(fetchChapterUpdates = !it.fetchChapterUpdates) }
            }
        }
    }

    fun save() {
        val ready = _state.value as? State.Ready ?: return
        viewModelScope.launch {
            updateMergedSettings.awaitMerge(mangaId, ready.sortMode, ready.infoMangaId)
            if (ready.entries.isNotEmpty()) {
                updateMergedSettings.awaitAll(ready.entries.map { it.entry })
            }
            _state.value = State.Loading
        }
    }

    private fun setPendingConfirm(confirm: PendingConfirm?) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            ready.copy(pendingConfirm = confirm)
        }
    }

    private fun updateEntry(id: Long, transform: (MergedEntry) -> MergedEntry) {
        _state.update { current ->
            val ready = current as? State.Ready ?: return@update current
            ready.copy(
                entries = ready.entries.map {
                    if (it.entry.id == id) it.copy(entry = transform(it.entry)) else it
                },
            )
        }
    }
}
