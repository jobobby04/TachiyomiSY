package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.HBrowseSearchMetadata
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource

@Composable
fun HBrowseDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? HBrowseSearchMetadata ?: return

    MetadataRow(openMetadataViewer = openMetadataViewer) {
        val length = meta.length ?: 0
        MetadataText(
            text = pluralStringResource(SYMR.plurals.num_pages, length, length),
            icon = SYMaterialSymbols.RoundedFilled.MenuBook,
        )
    }
}
