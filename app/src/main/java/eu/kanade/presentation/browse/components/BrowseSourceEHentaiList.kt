package eu.kanade.presentation.browse.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.gowtham.ratingbar.ComposeStars
import com.gowtham.ratingbar.RatingBarStyle
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.relativeTimeSpanString
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.ui.metadata.adapters.DISOWNED_UPLOADER
import exh.ui.metadata.adapters.MetadataChip
import exh.ui.metadata.adapters.MetadataGenre
import exh.ui.metadata.adapters.MetadataText
import exh.ui.metadata.adapters.MetadataUIUtil
import exh.ui.metadata.adapters.MetadataUploader
import kotlinx.coroutines.flow.StateFlow
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Group
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.Translate
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.round

@Composable
fun BrowseSourceEHentaiList(
    mangaList: LazyPagingItems<StateFlow</* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */>>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        item {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = mangaList.itemCount) { index ->
            val pair by mangaList[index]?.collectAsState() ?: return@items
            val manga = pair.first
            val metadata = pair.second

            BrowseSourceEHentaiListItem(
                manga = manga,
                // SY -->
                metadata = metadata,
                // SY <--
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        item {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

/**
 * A gallery laid out like a list display item, with the same facts as its metadata description: title, artist and
 * circle on top, then category, language and age, then rating, pages and uploader.
 */
@Composable
fun BrowseSourceEHentaiListItem(
    manga: Manga,
    // SY -->
    metadata: RaisedSearchMetadata?,
    // SY <--
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    if (metadata !is EHentaiSearchMetadata) return
    val context = LocalContext.current
    val unknown = stringResource(MR.strings.unknown)
    val genre = remember(metadata.genre) { metadata.genre?.let { MetadataUIUtil.getGenreAndColour(context, it) } }
    val artist = remember(metadata) { metadata.tagNames(ARTIST_NAMESPACE) }
    val circle = remember(metadata) { metadata.tagNames(GROUP_NAMESPACE) }
    val language = remember(metadata) {
        metadata.tagNames(EHentaiSearchMetadata.EH_LANGUAGE_NAMESPACE, LANGUAGE_FLAGS, capitalize = true)
    }
    val translated = remember(metadata) {
        metadata.tags.any { it.namespace == EHentaiSearchMetadata.EH_LANGUAGE_NAMESPACE && it.name == TRANSLATED }
    }

    Row(
        modifier = Modifier
            .height(156.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            MangaCover.Book(
                modifier = Modifier
                    .fillMaxHeight()
                    .alpha(if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f),
                data = manga,
            )
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                InLibraryBadge(enabled = manga.favorite)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = manga.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (artist != null || circle != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        artist?.let {
                            MetadataText(
                                text = it,
                                modifier = Modifier.weight(1f, fill = false),
                                icon = MaterialSymbols.Rounded.Palette,
                                style = MaterialTheme.typography.bodySmall,
                                copyable = false,
                            )
                        }
                        circle?.let {
                            MetadataText(
                                text = it,
                                modifier = Modifier.weight(1f, fill = false),
                                icon = MaterialSymbols.Rounded.Group,
                                style = MaterialTheme.typography.bodySmall,
                                copyable = false,
                            )
                        }
                    }
                }
                val uploader = metadata.uploader
                MetadataUploader(
                    uploader = uploader ?: unknown,
                    disowned = uploader.equals(DISOWNED_UPLOADER, ignoreCase = true),
                    emphasized = uploader != null,
                    copyable = false,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetadataGenre(
                        text = genre?.second ?: metadata.genre ?: unknown,
                        color = genre?.first,
                        small = true,
                        copyable = false,
                    )
                    language?.let {
                        MetadataChip(
                            text = if (translated) stringResource(SYMR.strings.language_translated, it) else it,
                            icon = SYMaterialSymbols.Rounded.Translate,
                            small = true,
                            copyable = false,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metadata.averageRating?.let { rating ->
                        GalleryRating(rating)
                        if (metadata.length != null || metadata.datePosted != null) FactSeparator()
                    }
                    metadata.length?.let { length ->
                        MetadataText(
                            text = pluralStringResource(SYMR.plurals.num_pages, length, length),
                            icon = SYMaterialSymbols.RoundedFilled.MenuBook,
                            copyable = false,
                        )
                    }
                    metadata.datePosted?.let {
                        if (metadata.length != null) FactSeparator()
                        MetadataText(
                            text = relativeTimeSpanString(it),
                            copyable = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryRating(rating: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposeStars(
            value = 1f,
            numOfStars = 1,
            size = 16.dp,
            spaceBetween = 0.dp,
            hideInactiveStars = false,
            style = RatingBarStyle.Fill(
                activeColor = MaterialTheme.colorScheme.primary,
                inActiveColor = MaterialTheme.colorScheme.primary,
            ),
            painterEmpty = null,
            painterFilled = null,
        )
        Text(
            text = (round(rating * 10.0) / 10.0).toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FactSeparator() {
    Text(
        text = "·",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The names of the tags in [namespace], skipping [excluded] ones, joined like the gallery's author and artist, or null
 * when it has none.
 */
private fun EHentaiSearchMetadata.tagNames(
    namespace: String,
    excluded: Set<String> = emptySet(),
    capitalize: Boolean = false,
): String? {
    return tags
        .filter { it.namespace == namespace && it.name !in excluded }
        .ifEmpty { null }
        ?.joinToString { tag -> if (capitalize) tag.name.replaceFirstChar { it.uppercase() } else tag.name }
}

private const val ARTIST_NAMESPACE = "artist"
private const val GROUP_NAMESPACE = "group"
private const val TRANSLATED = "translated"

/** Language tags that describe the translation rather than name a language. */
private val LANGUAGE_FLAGS = setOf(TRANSLATED, "rewrite", "speechless", "text cleaned")
