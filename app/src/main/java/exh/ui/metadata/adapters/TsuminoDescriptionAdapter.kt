package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.TsuminoSearchMetadata
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Date

@Composable
fun TsuminoDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? TsuminoSearchMetadata ?: return
    val context = LocalContext.current
    val unknown = stringResource(MR.strings.unknown)

    MetadataDescription {
        MetadataRow(openMetadataViewer = openMetadataViewer) {
            val genre = remember(meta.category) { meta.category?.let { MetadataUIUtil.getGenreAndColour(context, it) } }
            MetadataGenre(
                text = genre?.second ?: meta.category ?: unknown,
                color = genre?.first,
            )
        }

        MetadataFacts {
            fact {
                MetadataRating(
                    rating = meta.averageRating ?: 0F,
                    score = meta.averageRating,
                    label = MetadataUIUtil.getRatingString(context, meta.averageRating?.times(2)),
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
                MetadataUploader(uploader = meta.uploader ?: unknown)
            }
            fact {
                val whenPosted = remember(meta.uploadDate) {
                    TsuminoSearchMetadata.TSUMINO_DATE_FORMAT.format(Date(meta.uploadDate ?: 0))
                }
                MetadataText(text = whenPosted)
            }
        }
    }
}
