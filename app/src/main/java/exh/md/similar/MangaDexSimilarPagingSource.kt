package exh.md.similar

import cafe.adriel.voyager.navigator.Navigator
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import exh.recs.sources.RecommendationPagingSource
import exh.source.getMainSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR

/**
 * MangaDexSimilarPagingSource inherited from the general Pager.
 */
class MangaDexSimilarPagingSource(
    manga: Manga,
    val mangaDex: MangaDex,
) : RecommendationPagingSource(mangaDex, manga) {

    override val name: String
        get() = "MangaDex"

    override val category: StringResource
        get() = SYMR.strings.similar_titles

    override val associatedSourceId: Long
        get() = mangaDex.getMainSource().id

    override fun onMangaClick(navigator: Navigator, manga: Manga) =
        navigator.push(MangaScreen(manga.id, true))

    override fun onMangaLongClick(navigator: Navigator, manga: Manga) =
        onMangaClick(navigator, manga)

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val mangasPage = coroutineScope {
            val similarPageDef = async { mangaDex.getMangaSimilar(manga.toSManga()) }
            val relatedPageDef = async { mangaDex.getMangaRelated(manga.toSManga()) }
            val similarPage = similarPageDef.await()
            val relatedPage = relatedPageDef.await()

            MetadataMangasPage(
                relatedPage.mangas + similarPage.mangas,
                false,
                relatedPage.mangasMetadata + similarPage.mangasMetadata,
            )
        }

        return mangasPage.takeIf { it.mangas.isNotEmpty() } ?: throw NoResultsException()
    }
}
