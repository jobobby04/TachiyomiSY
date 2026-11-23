package eu.kanade.domain.sync

import eu.kanade.tachiyomi.data.sync.queuedHistoryTombstoneOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.domain.history.model.HistoryTombstoneKey
import kotlin.time.Clock

class SyncPreferencesTest {

    private fun syncPreferences(syncService: Int) = SyncPreferences(
        InMemoryPreferenceStore(sequenceOf(InMemoryPreference("sync_service", syncService, 0))),
    )

    @Test
    fun `deleted chapters stay pending until a re-add forgets them`() = runTest {
        val prefs = syncPreferences(syncService = 1)

        prefs.rememberDeletedChapters(listOf("a", "b"))
        prefs.forgetDeletedChapters(listOf("a", "c"))

        assertEquals(setOf("b"), prefs.pendingDeletedChapterKeys.get())
    }

    @Test
    fun `deleted chapters are not tracked while sync is off`() = runTest {
        val prefs = syncPreferences(syncService = 0)

        prefs.rememberDeletedChapters(listOf("a"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedChapterKeys.get())
    }

    @Test
    fun `deleted chapters are not tracked for google drive`() = runTest {
        val prefs = syncPreferences(syncService = 2)

        prefs.rememberDeletedChapters(listOf("a"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedChapterKeys.get())
    }

    @Test
    fun `deleted categories are not tracked for google drive`() = runTest {
        val prefs = syncPreferences(syncService = 2)

        prefs.rememberDeletedCategory(7)

        assertEquals(emptySet<String>(), prefs.pendingDeletedCategoryUids.get())
    }

    @Test
    fun `deleted manga stay pending until the server acknowledges them`() = runTest {
        val prefs = syncPreferences(syncService = 1)

        prefs.rememberDeletedManga(listOf("1|/gone", "1|/also-gone"))
        prefs.forgetDeletedManga(listOf("1|/gone"))

        assertEquals(setOf("1|/also-gone"), prefs.pendingDeletedMangaKeys.get())
    }

    @Test
    fun `deleted history rows stay pending until the server acknowledges them`() = runTest {
        val prefs = syncPreferences(syncService = 1)

        prefs.rememberDeletedHistory(listOf("1|/m\u001f/c1", "1|/m\u001f/c2"))
        prefs.forgetDeletedHistory(listOf("1|/m\u001f/c1"))

        assertEquals(setOf("1|/m\u001f/c2"), prefs.pendingDeletedHistoryKeys.get())
    }

    @Test
    fun `a queued history removal records when it happened`() = runTest {
        val prefs = syncPreferences(syncService = 1)
        val tombstones = SyncHistoryTombstones(prefs)

        val before = Clock.System.now().toEpochMilliseconds() / 1000
        tombstones.remember(
            listOf(
                HistoryTombstoneKey(chapterId = 11, mangaSourceId = 1, mangaUrl = "/m", chapterUrl = "/m/ch1"),
                HistoryTombstoneKey(chapterId = 12, mangaSourceId = 1, mangaUrl = "/m", chapterUrl = "/m/ch2"),
            ),
        )
        val after = Clock.System.now().toEpochMilliseconds() / 1000

        val queued = prefs.pendingDeletedHistoryKeys.get().map { queuedHistoryTombstoneOf(it) }
        // the entry stays the server's merge key, with the moment of the removal appended as one more segment
        assertEquals(setOf("1|/m\u001f/m/ch1", "1|/m\u001f/m/ch2"), queued.map { it.key }.toSet())
        queued.forEach {
            // Unix seconds, the unit the server compares: a millisecond stamp sits three orders of magnitude
            // above this window, and no stamp at all reads as 0
            assertTrue(it.at in before..after, "${it.at} is not in $before..$after")
        }
        // one removal stamps all of its keys with the same moment
        assertEquals(1, queued.map { it.at }.toSet().size)
    }

    @Test
    fun `deleted manga are not tracked when library sync is off`() = runTest {
        val prefs = SyncPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreference("sync_service", 1, 0),
                    InMemoryPreference("library_entries", false, true),
                ),
            ),
        )

        prefs.rememberDeletedManga(listOf("1|/gone"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedMangaKeys.get())
    }

    @Test
    fun `deleted history rows are not tracked when history sync is off`() = runTest {
        val prefs = SyncPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreference("sync_service", 1, 0),
                    InMemoryPreference("history", false, true),
                ),
            ),
        )

        prefs.rememberDeletedHistory(listOf("1|/m\u001f/c1"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedHistoryKeys.get())
    }

    @Test
    fun `deleted manga and history are not tracked for google drive`() = runTest {
        val prefs = syncPreferences(syncService = 2)

        prefs.rememberDeletedManga(listOf("1|/gone"))
        prefs.rememberDeletedHistory(listOf("1|/m\u001f/c1"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedMangaKeys.get())
        assertEquals(emptySet<String>(), prefs.pendingDeletedHistoryKeys.get())
    }
}
