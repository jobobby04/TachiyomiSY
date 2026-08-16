package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.similar.MangaDexSimilarPagingSource
import exh.pref.DelegateSourcePreferences
import exh.source.getMainSource
import exh.source.isMdBasedSource
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.BaseSourcePagingSource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.sy.SYMR

/**
 * General class for recommendation sources.
 */
abstract class RecommendationPagingSource(
    protected val manga: Manga,
    source: Source? = null,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    // Display name
    abstract val name: String

    // Localized category name
    open val category: StringResource = SYMR.strings.similar_titles

    /**
     * Recommendation sources that display results from a source extension,
     * can override this property to associate results with a specific source.
     * This is used to redirect the user directly to the corresponding MangaScreen.
     * If null, the user will be prompted to choose a source via SmartSearch when clicking on a recommendation.
     */
    open val associatedSourceId: Long? = null

    companion object {
        fun createSources(
            manga: Manga,
            source: Source,
            networkToLocalManga: NetworkToLocalManga,
            getTracks: GetTracks,
            trackerManager: TrackerManager,
            client: OkHttpClient,
            json: Json,
            delegateSourcePreferences: DelegateSourcePreferences,
        ): List<RecommendationPagingSource> {
            return buildList {
                add(AniListPagingSource(manga, networkToLocalManga, getTracks, trackerManager, client, json))
                add(MangaUpdatesCommunityPagingSource(manga, networkToLocalManga, getTracks, trackerManager, client, json))
                add(MangaUpdatesSimilarPagingSource(manga, networkToLocalManga, getTracks, trackerManager, client, json))
                add(MyAnimeListPagingSource(manga, networkToLocalManga, getTracks, trackerManager, client, json))

                // Only include MangaDex if the delegate sources are enabled and the source is MD-based
                if (source.isMdBasedSource() && delegateSourcePreferences.delegateSources.get()) {
                    add(MangaDexSimilarPagingSource(manga, source.getMainSource() as MangaDex, networkToLocalManga))
                }

                // Only include Comick if the source manga is from there
                if (source.isComickSource()) {
                    add(ComickPagingSource(manga, source, networkToLocalManga, client, json))
                }
            }.sortedWith(compareBy({ it.name }, { it.category.resourceId }))
        }
    }
}

/**
 * General class for recommendation sources backed by trackers.
 */
abstract class TrackerRecommendationPagingSource(
    protected val endpoint: String,
    manga: Manga,
    networkToLocalManga: NetworkToLocalManga,
    private val getTracks: GetTracks,
    protected val trackerManager: TrackerManager,
    protected val client: OkHttpClient,
    protected val json: Json,
) : RecommendationPagingSource(manga, null, networkToLocalManga) {

    /**
     * Tracker id associated with the recommendation source.
     *
     * If not null and the tracker is attached to the source manga,
     * the remote id will be used to directly identify the manga on the tracker.
     * Otherwise, a search will be performed using the manga title.
     */
    abstract val associatedTrackerId: Long?

    abstract suspend fun getRecsBySearch(search: String): List<SManga>
    abstract suspend fun getRecsById(id: String): List<SManga>

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val tracks = getTracks.await(manga.id)

        val recs = try {
            val id = tracks.find { it.trackerId == associatedTrackerId }?.remoteId
            val results = if (id != null) {
                getRecsById(id.toString())
            } else {
                getRecsBySearch(manga.ogTitle)
            }
            logcat { name + " > Results: " + results.size }

            results.ifEmpty { throw NoResultsException() }
        } catch (e: Exception) {
            // 'No results' should not be logged as it happens frequently and is expected
            if (e !is NoResultsException) {
                logcat(LogPriority.ERROR, e) { name }
            }
            throw e
        }

        return MangasPage(recs, false)
    }
}
