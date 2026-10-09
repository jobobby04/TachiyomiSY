package exh.ui.metadata.adapters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.tachiyomi.ui.manga.MangaViewModel.State
import exh.metadata.metadata.HentaiNexusSearchMetadata
import mihon.core.common.extensions.toInstantOrNull
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CalendarMonth
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.Group
import mihon.icons.materialsymbols.rounded.Newspaper
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.Domain
import mihon.icons.sy.materialsymbols.rounded.Festival
import mihon.icons.sy.materialsymbols.roundedfilled.MenuBook
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import java.text.NumberFormat

@Composable
fun HentaiNexusDescription(state: State.Success, openMetadataViewer: () -> Unit) {
    val meta = state.meta as? HentaiNexusSearchMetadata ?: return
    val namespaces = remember(meta.tags) {
        meta.tags.groupBy({ it.namespace }, { it.name }).mapValues { (_, names) -> names.joinToString() }
    }
    val publisher = namespaces[HentaiNexusSearchMetadata.NAMESPACE_PUBLISHER]
    val magazine = namespaces[HentaiNexusSearchMetadata.NAMESPACE_MAGAZINE]
    val circle = namespaces[HentaiNexusSearchMetadata.NAMESPACE_CIRCLE]
    val event = namespaces[HentaiNexusSearchMetadata.NAMESPACE_EVENT]

    MetadataDescription {
        MetadataFacts(openMetadataViewer = openMetadataViewer) {
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
            meta.publishedDate?.let { date ->
                fact {
                    MetadataText(text = relativeDateText(date.toInstantOrNull()), icon = MaterialSymbols.Rounded.CalendarMonth)
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
            publisher?.let { fact { MetadataText(text = it, icon = SYMaterialSymbols.Rounded.Domain) } }
        }

        if (magazine != null || circle != null || event != null) {
            MetadataFacts {
                magazine?.let { fact { MetadataText(text = it, icon = MaterialSymbols.Rounded.Newspaper) } }
                circle?.let { fact { MetadataText(text = it, icon = MaterialSymbols.Rounded.Group) } }
                event?.let { fact { MetadataText(text = it, icon = SYMaterialSymbols.Rounded.Festival) } }
            }
        }
    }
}
