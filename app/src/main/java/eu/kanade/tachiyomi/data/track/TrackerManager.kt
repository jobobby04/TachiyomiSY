package eu.kanade.tachiyomi.data.track

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.comick.Comick
import eu.kanade.tachiyomi.data.track.hikka.Hikka
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import kotlinx.coroutines.flow.combine

@Inject
@SingleIn(AppScope::class)
class TrackerManager(
    // SY -->
    context: Context,
    // SY <--
) {

    // SY -->
    companion object {
        // Mangadex from Neko
        const val MDLIST = 60L
    }

    val mdList = MdList(context, MDLIST)
    val myAnimeList = MyAnimeList(context, 1L)
    val aniList = Anilist(context, 2L)
    val kitsu = Kitsu(context, 3L)
    val shikimori = Shikimori(context, 4L)
    val bangumi = Bangumi(context, 5L)
    val komga = Komga(context, 6L)
    val mangaUpdates = MangaUpdates(context, 7L)
    val kavita = Kavita(context, 8L)
    val suwayomi = Suwayomi(context, 9L)
    val hikka = Hikka(context, 10L)
    val mangaBaka = MangaBaka(context, 11L)
    val comick = Comick(context, 12L)
    // SY <--

    val trackers = listOf(
        // SY -->
        mdList,
        // SY <--
        mangaBaka,
        myAnimeList,
        aniList,
        kitsu,
        shikimori,
        bangumi,
        komga,
        mangaUpdates,
        kavita,
        suwayomi,
        hikka,
        comick,
    )

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }
}
