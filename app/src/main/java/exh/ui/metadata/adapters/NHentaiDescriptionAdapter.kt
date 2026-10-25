package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.MetadataUtil
import exh.metadata.metadata.NHentaiSearchMetadata
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * SY-specific file
 */
@Composable
fun NHentaiDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? NHentaiSearchMetadata ?: return
    val context = LocalContext.current

    MetadataDescription {
        MetadataRow(openMetadataViewer = openMetadataViewer) {
            val categories = remember(meta.tags) {
                meta.tags
                    .filter { it.namespace == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(transform = { it.name })
            }
            val genre = remember(categories) { categories?.let { MetadataUIUtil.getGenreAndColour(context, it) } }
            MetadataGenre(
                text = genre?.second ?: categories ?: stringResource(MR.strings.unknown),
                color = genre?.first,
            )
            MetadataChip(text = "#" + (meta.nhId ?: 0))
        }

        MetadataFacts {
            fact {
                val pages = meta.pageImagePreviewUrls.size
                MetadataText(
                    text = pluralStringResource(SYMR.plurals.num_pages, pages, pages),
                    icon = SYMaterialSymbols.RoundedFilled.MenuBook,
                )
            }
            meta.favoritesCount?.takeIf { it != 0L }?.let {
                fact {
                    MetadataText(
                        text = it.toString(),
                        icon = MaterialSymbols.Rounded.Bookmark,
                    )
                }
            }
            fact {
                val whenPosted = remember(meta.uploadDate) {
                    MetadataUtil.EX_DATE_FORMAT.format(
                        ZonedDateTime.ofInstant(
                            Instant.fromEpochSeconds(meta.uploadDate ?: 0).toJavaInstant(),
                            ZoneId.systemDefault(),
                        ),
                    )
                }
                MetadataText(text = whenPosted)
            }
        }
    }
}
