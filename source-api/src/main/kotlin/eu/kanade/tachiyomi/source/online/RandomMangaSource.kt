package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source

/**
 * SY-specific file
 */
interface RandomMangaSource : Source {
    suspend fun fetchRandomMangaUrl(): String
}
