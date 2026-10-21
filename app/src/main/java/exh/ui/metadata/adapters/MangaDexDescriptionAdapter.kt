package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.ui.metadata.adapters.MetadataUIUtil.getRatingString

@Composable
fun MangaDexDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? MangaDexSearchMetadata ?: return
    val context = LocalContext.current

    MetadataRow(openMetadataViewer = openMetadataViewer) {
        val ratingFloat = meta.rating
        if (ratingFloat != null) {
            MetadataRating(
                rating = ratingFloat.div(2F),
                score = ratingFloat,
                label = getRatingString(context, ratingFloat),
            )
        }
    }
}
