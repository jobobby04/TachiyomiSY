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
}
