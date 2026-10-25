package exh.source

import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga

/**
 * SY-specific file
 */
// Used to speed up isLewdSource
var metadataDelegatedSourceIds: List<Long> = emptyList()

var nHentaiSourceIds: Set<Long> = emptySet()

var hitomiSourceIds: Set<Long> = emptySet()

var pururinSourceIds: Set<Long> = emptySet()

var lanraragiSourceIds: Set<Long> = emptySet()

var mangaDexSourceIds: Set<Long> = emptySet()

var LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
    EH_SOURCE_ID,
    EXH_SOURCE_ID,
)

// This method MUST be fast!
fun isMetadataSource(source: Long) = source in 6900..6999 ||
    metadataDelegatedSourceIds.binarySearch(source) >= 0

fun Source.isEhBasedSource() = id == EH_SOURCE_ID || id == EXH_SOURCE_ID

fun Source.isMdBasedSource() = id in mangaDexSourceIds

fun Manga.isEhBasedManga() = source == EH_SOURCE_ID || source == EXH_SOURCE_ID

fun Source.getMainSource(): Source = if (this is EnhancedHttpSource) {
    this.source()
} else {
    this
}

@JvmName("getMainSourceInline")
inline fun <reified T : Source> Source.getMainSource(): T? = if (this is EnhancedHttpSource) {
    this.source() as? T
} else {
    this as? T
}

inline fun <reified T> Source.anyIs(): Boolean {
    return if (this is EnhancedHttpSource) {
        originalSource is T || enhancedSource is T
    } else {
        this is T
    }
}
