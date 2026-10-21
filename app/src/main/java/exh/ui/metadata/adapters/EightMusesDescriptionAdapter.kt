package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.EightMusesSearchMetadata
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EightMusesDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? EightMusesSearchMetadata ?: return

    MetadataRow(openMetadataViewer = openMetadataViewer) {
        MetadataText(text = meta.title ?: stringResource(MR.strings.unknown))
    }
}
