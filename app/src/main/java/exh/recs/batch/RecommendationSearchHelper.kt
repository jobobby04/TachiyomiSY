package exh.recs.batch

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.annotation.StringRes
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import exh.log.xLog
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.TrackerRecommendationPagingSource
import exh.util.createPartialWakeLock
import exh.util.createWifiLock
import exh.util.ignore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import java.io.Serializable
import java.util.Collections
import kotlin.coroutines.coroutineContext

typealias RecommendationMap = Map<String, SearchResults>

class RecommendationSearchHelper(val context: Context) {
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    private val prefs: UnsortedPreferences by injectLazy()

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val logger by lazy { xLog() }

    val status: MutableStateFlow<SearchStatus> = MutableStateFlow(SearchStatus.Idle)

    @Synchronized
    fun runSearch(scope: CoroutineScope, mangaList: List<Manga>) {
        if (status.value !is SearchStatus.Idle) {
            return
        }

        status.value = SearchStatus.Initializing

        scope.launch(Dispatchers.IO) { beginSearch(mangaList) }
    }

    private suspend fun beginSearch(mangaList: List<Manga>) {
        val libraryManga = getLibraryManga.await()
        val flags = prefs.recommendationSearchFlags().get()

        try {
            // Take wake + wifi locks
            ignore { wakeLock?.release() }
            wakeLock = ignore { context.createPartialWakeLock("tsy:RecommendationSearchWakelock") }
            ignore { wifiLock?.release() }
            wifiLock = ignore { context.createWifiLock("tsy:RecommendationSearchWifiLock") }

            // Map of results grouped by recommendation source
            val resultsMap = Collections.synchronizedMap(mutableMapOf<String, SearchResults>())

            mangaList.forEachIndexed { index, sourceManga ->
                status.value = SearchStatus.Processing(sourceManga.toSManga(), index + 1, mangaList.size)

                val jobs = RecommendationPagingSource.createSources(
                    sourceManga,
                    sourceManager.get(sourceManga.source) as CatalogueSource
                ).mapNotNull { source ->

                    if (source is TrackerRecommendationPagingSource && !SearchFlags.hasIncludeTrackers(flags)) {
                        return@mapNotNull null
                    }

                    if (source.associatedSourceId != null && !SearchFlags.hasIncludeSources(flags)) {
                        return@mapNotNull null
                    }

                    // Parallelize fetching recommendations from all sources in the current context
                    CoroutineScope(coroutineContext).async(Dispatchers.IO) {
                        val recSourceId = source::class.qualifiedName!!

                        try {
                            val page = source.requestNextPage(1)

                            // Add or update the result collection for the current source
                            resultsMap.getOrPut(recSourceId) {
                                SearchResults(
                                    recSourceName = source.name,
                                    recSourceCategoryResId = source.category.resourceId,
                                    recAssociatedSourceId = source.associatedSourceId,
                                    recommendations = mutableListOf()
                                )
                            }.recommendations.addAll(page.mangas)

                        }
                        catch (_: NoResultsException) {}
                        catch (e: Exception) {
                            logger.e("Error while fetching recommendations for $recSourceId", e)
                        }
                    }
                }

                //TODO filter library manga
                jobs.awaitAll()
            }

            status.value = SearchStatus.Finished(resultsMap)
        } catch (e: Exception) {
            status.value = SearchStatus.Error(e.message.orEmpty())
            logger.e("Error during recommendation search", e)
            return
        } finally {
            // Release wake + wifi locks
            ignore {
                wakeLock?.release()
                wakeLock = null
            }
            ignore {
                wifiLock?.release()
                wifiLock = null
            }
        }
    }
}

data class SearchResults(
    val recSourceName: String,
    @StringRes val recSourceCategoryResId: Int,
    val recAssociatedSourceId: Long?,
    val recommendations: MutableList<SManga>
) : Serializable

sealed interface SearchStatus {
    data object Idle : SearchStatus
    data object Initializing : SearchStatus
    data class Processing(val manga: SManga, val current: Int, val total: Int) : SearchStatus
    data class Error(val message: String) : SearchStatus
    data class Finished(val results: RecommendationMap) : SearchStatus
}
