package eu.kanade.tachiyomi.ui.recommendations

import android.content.Context
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import exh.recs.batch.RankedSearchResults
import exh.recs.batch.RecommendationSearchHelper
import exh.recs.batch.SearchStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RecommendationsScreenModel(
    private val context: Context,
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) : StateScreenModel<RecommendationsState>(RecommendationsState.Loading()) {

    private var helper: RecommendationSearchHelper = RecommendationSearchHelper(context)
    private var observerJob: Job? = null
    private var libraryObserverJob: Job? = null

    // Resultados brutos para refiltragem sem novo acesso à rede
    private var rawResults: List<RankedSearchResults>? = null

    init {
        setupObserver()
        setupLibraryObserver()
        refresh()
    }

    private fun setupObserver() {
        observerJob?.cancel()
        observerJob = helper.status.onEach { status ->
            val currentResults = (mutableState.value as? RecommendationsState.Success)?.results
                ?: (mutableState.value as? RecommendationsState.Loading)?.results

            when (status) {
                is SearchStatus.Idle,
                is SearchStatus.Initializing,
                is SearchStatus.Processing,
                is SearchStatus.Cancelling -> {
                    mutableState.value = RecommendationsState.Loading(currentResults)
                }

                is SearchStatus.Finished.WithResults -> {
                    rawResults = status.results
                    processAndPublishResults(status.results)
                }

                is SearchStatus.Finished.WithoutResults -> {
                    mutableState.value = RecommendationsState.Empty
                }

                is SearchStatus.Error -> {
                    mutableState.value = RecommendationsState.Error(status.message)
                }
            }
        }.launchIn(screenModelScope)
    }

    private fun setupLibraryObserver() {
        libraryObserverJob?.cancel()
        libraryObserverJob = getLibraryManga.subscribe().onEach {
            val raw = rawResults ?: return@onEach
            processAndPublishResults(raw)
        }.launchIn(screenModelScope)
    }

    private fun processAndPublishResults(results: List<RankedSearchResults>) {
        screenModelScope.launch {
            val filtered = filterOutLibraryMangas(results)
            mutableState.value = if (filtered.isEmpty()) {
                RecommendationsState.Empty
            } else {
                RecommendationsState.Success(filtered)
            }
        }
    }

    private suspend fun filterOutLibraryMangas(results: List<RankedSearchResults>): List<RankedSearchResults> {
        val libraryTitles = getLibraryManga.await()
            .map { it.manga.ogTitle.lowercase().replace("[^a-z0-9]".toRegex(), "") }
            .toSet()

        return results.map { ranked ->
            val filteredMap = ranked.results.filter { (sManga, _) ->
                val cleanTitle = sManga.title.lowercase().replace("[^a-z0-9]".toRegex(), "")
                cleanTitle !in libraryTitles
            }
            ranked.copy(results = filteredMap)
        }.filter { it.results.isNotEmpty() }
    }

    fun refresh() {
        screenModelScope.launch {
            val currentResults = (mutableState.value as? RecommendationsState.Success)?.results
            mutableState.value = RecommendationsState.Loading(currentResults)

            helper = RecommendationSearchHelper(context)
            setupObserver()

            val libraryManga = getLibraryManga.await().map { it.manga }
            if (libraryManga.isNotEmpty()) {
                helper.runSearch(screenModelScope, libraryManga)
            } else {
                mutableState.value = RecommendationsState.Empty
            }
        }
    }
}

sealed interface RecommendationsState {
    data class Loading(val results: List<RankedSearchResults>? = null) : RecommendationsState
    data object Empty : RecommendationsState
    data class Error(val message: String) : RecommendationsState
    data class Success(val results: List<RankedSearchResults>) : RecommendationsState
}
