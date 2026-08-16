package eu.kanade.tachiyomi.data.track

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
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
    context: Context,
) {

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L

        // SY --> Mangadex from Neko
        const val MDLIST = 60L
        // SY <--

        const val MANGABAKA = 11L
    }

    val mdList = MdList(context, MDLIST)

    val myAnimeList = MyAnimeList(context, 1L)
    val aniList = Anilist(context, ANILIST)
    val kitsu = Kitsu(context, KITSU)
    val shikimori = Shikimori(context, 4L)
    val bangumi = Bangumi(context, 5L)
    val komga = Komga(context, 6L)
    val mangaUpdates = MangaUpdates(context, 7L)
    val kavita = Kavita(context, KAVITA)
    val suwayomi = Suwayomi(context, 9L)
    val hikka = Hikka(context, 10L)
    val mangaBaka = MangaBaka(context, MANGABAKA)

    val trackers = listOf(
        // SY -->
        mdList,
        // SY <--
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
        mangaBaka,
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
