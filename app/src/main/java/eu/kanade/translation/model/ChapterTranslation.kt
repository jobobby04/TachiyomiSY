package eu.kanade.translation.model

import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

data class ChapterTranslation(
    val source: Source,
    val manga: Manga,
    val chapter: Chapter,
) {
    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_TRANSLATED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()

    var status: State
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    @Transient
    var translatedPages: Int = 0

    @Transient
    var totalPages: Int = 0

    @Transient
    var errorMessage: String? = null

    enum class State {
        NOT_TRANSLATED,
        QUEUE,
        WAKING_SERVER,
        WAITING_SERVER,
        TRANSLATING,
        TRANSLATED,
        ERROR,
    }
}
