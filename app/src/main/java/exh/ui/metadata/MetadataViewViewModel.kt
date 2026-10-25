package exh.ui.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.getMainSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * SY-specific file
 */
@AssistedInject
class MetadataViewViewModel(
    @Assisted val mangaId: Long,
    @Assisted val sourceId: Long,
    private val getFlatMetadataById: GetFlatMetadataById,
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
) : ViewModel() {

    val state: StateFlow<MetadataViewState>
        field = MutableStateFlow<MetadataViewState>(MetadataViewState.Loading)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long, sourceId: Long): MetadataViewViewModel
    }

    private val _manga = MutableStateFlow<Manga?>(null)
    val manga = _manga.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _manga.value = getManga.await(mangaId)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val metadataSource = sourceManager.get(sourceId)?.getMainSource<MetadataSource<*, *>>()
            if (metadataSource == null) {
                state.value = MetadataViewState.SourceNotFound
                return@launch
            }

            state.value = when (val flatMetadata = getFlatMetadataById.await(mangaId)) {
                null -> MetadataViewState.MetadataNotFound
                else -> MetadataViewState.Success(flatMetadata.raise(metadataSource.metaClass))
            }
        }
    }
}

sealed class MetadataViewState {
    data object Loading : MetadataViewState()
    data class Success(val meta: RaisedSearchMetadata) : MetadataViewState()
    data object MetadataNotFound : MetadataViewState()
    data object SourceNotFound : MetadataViewState()
}
