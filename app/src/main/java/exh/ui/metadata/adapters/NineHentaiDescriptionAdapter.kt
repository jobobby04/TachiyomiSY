package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.NineHentaiSearchMetadata
import mihon.core.common.extensions.toInstantOrNull
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.roundedfilled.Visibility
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import java.text.NumberFormat

/**
 * SY-specific file
 */
@Composable
fun NineHentaiDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? NineHentaiSearchMetadata ?: return
    val context = LocalContext.current
    val category = remember(meta.tags) {
        meta.tags.find { it.namespace == NineHentaiSearchMetadata.NAMESPACE_CATEGORY }?.name
    }
    val genre = remember(category) { category?.let { MetadataUIUtil.getGenreAndColour(context, it.lowercase()) } }

    MetadataDescription {
        MetadataRow(openMetadataViewer = openMetadataViewer) {
            category?.let {
                MetadataGenre(text = genre?.second ?: it, color = genre?.first)
            }
        }

        if (meta.favorites != null || meta.views != null) {
            MetadataFacts {
                meta.favorites?.let { favorites ->
                    fact {
                        MetadataText(
                            text = pluralStringResource(
                                SYMR.plurals.num_favorites,
                                favorites,
                                NumberFormat.getIntegerInstance().format(favorites),
                            ),
                            icon = MaterialSymbols.Rounded.Favorite,
                        )
                    }
                }
                meta.views?.let { views ->
                    fact {
                        MetadataText(
                            text = pluralStringResource(
                                SYMR.plurals.num_views,
                                views,
                                NumberFormat.getIntegerInstance().format(views),
                            ),
                            icon = MaterialSymbols.RoundedFilled.Visibility,
                        )
                    }
                }
            }
        }

        MetadataFacts {
            meta.pageCount?.let { pages ->
                fact {
                    MetadataText(
                        text = pluralStringResource(SYMR.plurals.num_pages, pages, pages),
                        icon = SYMaterialSymbols.RoundedFilled.MenuBook,
                    )
                }
            }
            meta.uploadDate?.let { date ->
                fact {
                    MetadataText(text = relativeDateText(date.toInstantOrNull()), icon = MaterialSymbols.Rounded.Schedule)
                }
            }
        }
    }
}
