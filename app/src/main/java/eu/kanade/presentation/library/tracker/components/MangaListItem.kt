package eu.kanade.presentation.library.tracker.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata

fun LazyListScope.mangaListItem(
    items: List<TrackMangaMetadata>,
    onClick: (TrackMangaMetadata) -> Unit,
) {
    items(
        items = items,
        key = { it.remoteId!! },
    ) { item ->
        Box(modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(300))) {
            MangaListItem(
                modifier = Modifier,
                manga = item,
                onClick = { onClick(item) },
            )
        }
    }
}

@Composable
fun MangaListItem(
    modifier: Modifier,
    manga: TrackMangaMetadata,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .combinedClickable(
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .fillMaxHeight(),
            data = manga.thumbnailUrl,
        )
        Text(
            text = manga.title!!,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
