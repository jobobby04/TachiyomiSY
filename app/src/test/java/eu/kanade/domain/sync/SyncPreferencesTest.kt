package eu.kanade.domain.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

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
