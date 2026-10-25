package exh.source

import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.Lanraragi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.Pururin
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HentaiNexus
import eu.kanade.tachiyomi.source.online.english.NineHentai

/**
 * SY-specific file
 *
 * Source helpers
 */

private val DELEGATED_METADATA_SOURCES by lazy {
    listOf(
        Pururin::class,
        EightMuses::class,
        NHentai::class,
        Hitomi::class,
        Lanraragi::class,
        HentaiNexus::class,
        NineHentai::class,
    )
}

fun handleSourceLibrary() {
    metadataDelegatedSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass in DELEGATED_METADATA_SOURCES
        }
        .flatMap { it.value.sourceIds }
        .distinct()
        .sorted()

    nHentaiSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == NHentai::class
        }
        .flatMap { it.value.sourceIds }
        .toSet()

    hitomiSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == Hitomi::class
        }
        .flatMap { it.value.sourceIds }
        .toSet()

    pururinSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter { it.value.newSourceClass == Pururin::class }
        .flatMap { it.value.sourceIds }
        .toSet()

    mangaDexSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == MangaDex::class
        }
        .flatMap { it.value.sourceIds }
        .toSet()

    lanraragiSourceIds = AndroidSourceManager.currentDelegatedSources
        .filter {
            it.value.newSourceClass == Lanraragi::class
        }
        .flatMap { it.value.sourceIds }
        .toSet()

    LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
        EH_SOURCE_ID,
        EXH_SOURCE_ID,
    ) + nHentaiSourceIds + pururinSourceIds + hitomiSourceIds
}
