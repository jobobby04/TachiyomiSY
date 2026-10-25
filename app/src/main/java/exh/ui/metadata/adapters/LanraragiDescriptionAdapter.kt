package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.LanraragiSearchMetadata
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource

/**
 * SY-specific file
 */
@Composable
fun LanraragiDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? LanraragiSearchMetadata ?: return

    MetadataRow(openMetadataViewer = openMetadataViewer, spacing = 6.dp) {
        meta.extension?.takeIf { it.isNotBlank() }?.let {
            MetadataChip(text = it.uppercase(), small = true)
        }
        val pages = meta.pageCount ?: 1
        MetadataText(
            text = pluralStringResource(SYMR.plurals.num_pages, pages, pages),
            icon = SYMaterialSymbols.RoundedFilled.MenuBook,
        )
    }
}
