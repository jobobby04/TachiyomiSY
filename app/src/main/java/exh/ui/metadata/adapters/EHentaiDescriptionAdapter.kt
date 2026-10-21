package exh.ui.metadata.adapters

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.MetadataUtil
import exh.metadata.metadata.EHentaiSearchMetadata
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.SdCard
import mihon.icons.sy.materialsymbols.rounded.Translate
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EHentaiDescription(state: State.Success, openMetadataViewer: () -> Unit, search: (String) -> Unit) {
    val meta = state.meta as? EHentaiSearchMetadata ?: return
    val context = LocalContext.current
    val unknown = stringResource(MR.strings.unknown)

    MetadataDescription {
        MetadataRow(openMetadataViewer = openMetadataViewer) {
            val genre = remember(meta.genre) { meta.genre?.let { MetadataUIUtil.getGenreAndColour(context, it) } }
            MetadataGenre(
                text = genre?.second ?: meta.genre ?: unknown,
                color = genre?.first,
            )
            val language = meta.language ?: unknown
            MetadataChip(
                text = if (meta.translated == true) {
                    stringResource(SYMR.strings.language_translated, language)
                } else {
                    language
                },
                icon = SYMaterialSymbols.Rounded.Translate,
            )
            val hiddenReason = remember(meta.visible) { hiddenReason(meta.visible) }
            if (hiddenReason != null) {
                MetadataChip(
                    text = hiddenReason,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        MetadataFacts {
            fact {
                val ratingFloat = meta.averageRating?.toFloat()
                MetadataRating(
                    rating = ratingFloat ?: 0F,
                    score = ratingFloat,
                    label = MetadataUIUtil.getRatingString(context, ratingFloat?.times(2)),
                )
            }
            fact {
                MetadataText(
                    text = (meta.favorites ?: 0).toString(),
                    icon = MaterialSymbols.Rounded.Bookmark,
                )
            }
        }

        MetadataFacts {
            fact {
                val length = meta.length ?: 0
                MetadataText(
                    text = pluralStringResource(SYMR.plurals.num_pages, length, length),
                    icon = SYMaterialSymbols.RoundedFilled.MenuBook,
                )
            }
            fact {
                MetadataText(
                    text = MetadataUtil.humanReadableByteCount(meta.size ?: 0, true),
                    icon = SYMaterialSymbols.Rounded.SdCard,
                    copyable = false,
                )
            }
            fact {
                val uploader = meta.uploader
                MetadataUploader(
                    uploader = uploader ?: unknown,
                    disowned = uploader.equals(DISOWNED_UPLOADER, ignoreCase = true),
                    onClick = uploader?.let { { search("uploader:\"$it\"") } },
                )
            }
        }
    }
}

/**
 * E-Hentai stores visibility as "Yes", or "No (reason)" (e.g. "No (Expunged)") for hidden galleries.
 *
 * @return the reason a gallery is hidden, the raw value if it has no reason, or null when it is visible.
 */
private fun hiddenReason(visible: String?): String? {
    val value = visible?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value.startsWith("Yes", ignoreCase = true)) return null
    return value.substringAfter('(', "").substringBeforeLast(')').trim().ifEmpty { value }
}

/** Uploader reported by E-Hentai when the uploader has disowned the gallery. */
private const val DISOWNED_UPLOADER = "(Disowned)"
