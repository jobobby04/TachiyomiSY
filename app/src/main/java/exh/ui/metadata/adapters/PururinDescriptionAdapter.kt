package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.PururinSearchMetadata
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.SdCard
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PururinDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? PururinSearchMetadata ?: return
    val context = LocalContext.current
    val unknown = stringResource(MR.strings.unknown)

    MetadataDescription {
        MetadataRow(openMetadataViewer = openMetadataViewer) {
            val category = remember(meta.tags) {
                meta.tags.find { it.namespace == PururinSearchMetadata.TAG_NAMESPACE_CATEGORY }
            }
            val genre = remember(category) { category?.let { MetadataUIUtil.getGenreAndColour(context, it.name) } }
            MetadataGenre(
                text = genre?.second ?: category?.name ?: unknown,
                color = genre?.first,
            )
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
        }

        MetadataFacts {
            fact {
                val pages = meta.pages ?: 0
                MetadataText(
                    text = pluralStringResource(SYMR.plurals.num_pages, pages, pages),
                    icon = SYMaterialSymbols.RoundedFilled.MenuBook,
                )
            }
            fact {
                MetadataText(
                    text = meta.fileSize ?: unknown,
                    icon = SYMaterialSymbols.Rounded.SdCard,
                )
            }
            fact {
                MetadataUploader(uploader = meta.uploaderDisp ?: meta.uploader ?: unknown)
            }
        }
    }
}
