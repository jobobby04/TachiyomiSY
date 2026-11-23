package eu.kanade.domain.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.domain.history.service.HistoryTombstoneDeliverability
import kotlin.time.Clock

class SyncPreferencesTest {

    private fun syncPreferences(
        syncService: Int = 1,
        history: Boolean = true,
        probedHost: String = HOST,
        supportsV2: Boolean = true,
    ) = SyncPreferences(
        InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreference("sync_service", syncService, 0),
                InMemoryPreference("history", history, true),
                InMemoryPreference("sync_client_host", HOST, HOST),
                InMemoryPreference("__APP_STATE_sync_v2_probed_host", probedHost, ""),
                InMemoryPreference("__APP_STATE_sync_server_supports_v2", supportsV2, false),
            ),
        ),
    )

    /** The enqueue gate the removal paths read, through the binding the app installs. */
    private fun tombstones(prefs: SyncPreferences) = SyncHistoryTombstones(SyncYomiHistoryTombstoneDelivery(prefs))

    private companion object {
        const val HOST = "https://sync.example"
    }

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
    fun `a deliverable history removal is stamped with the moment it happened`() = runTest {
        val prefs = syncPreferences(syncService = 1)

        val before = Clock.System.now().toEpochMilliseconds()
        val at = tombstones(prefs).removalTimeMs()
        val after = Clock.System.now().toEpochMilliseconds()

        // epoch milliseconds, the unit the queue column keeps: a whole-second stamp sits three orders of
        // magnitude below this window, and 0 would mean the removal is not queued at all
        assertTrue(at in before..after, "$at is not in $before..$after")
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
    fun `a history removal is not queued when history sync is off`() = runTest {
        val prefs = syncPreferences(history = false)

        // history off is a verdict: no moment means the removal queues nothing
        assertEquals(HistoryTombstoneDeliverability.UNDELIVERABLE, prefs.historyTombstoneDeliverability())
        assertEquals(0L, tombstones(prefs).removalTimeMs())
    }

    @Test
    fun `a history removal is not queued for google drive`() = runTest {
        val prefs = syncPreferences(syncService = 2)

        prefs.rememberDeletedManga(listOf("1|/gone"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedMangaKeys.get())
        // only SyncYomi speaks the v2 history tombstone protocol
        assertEquals(HistoryTombstoneDeliverability.UNDELIVERABLE, prefs.historyTombstoneDeliverability())
        assertEquals(0L, tombstones(prefs).removalTimeMs())
    }

    @Test
    fun `a host the probe has not seen is unknown, not undeliverable`() = runTest {
        // the probe is cached per host: another host's answer says nothing about the one selected now,
        // so this is not a verdict and the queue is neither written nor swept
        val prefs = syncPreferences(probedHost = "")

        assertEquals(HistoryTombstoneDeliverability.UNKNOWN, prefs.historyTombstoneDeliverability())
        assertEquals(0L, tombstones(prefs).removalTimeMs())
    }

    @Test
    fun `a host that answered without v2 is undeliverable`() = runTest {
        val prefs = syncPreferences(supportsV2 = false)

        assertEquals(HistoryTombstoneDeliverability.UNDELIVERABLE, prefs.historyTombstoneDeliverability())
        assertEquals(0L, tombstones(prefs).removalTimeMs())
    }

    @Test
    fun `only a probed v2 host on SyncYomi with history sync on is deliverable`() = runTest {
        assertEquals(
            HistoryTombstoneDeliverability.DELIVERABLE,
            syncPreferences().historyTombstoneDeliverability(),
        )
        assertEquals(
            HistoryTombstoneDeliverability.UNDELIVERABLE,
            syncPreferences(syncService = 0).historyTombstoneDeliverability(),
        )
        // a host swap is UNKNOWN until the new host is probed, even with a v2 verdict cached for the old one
        assertEquals(
            HistoryTombstoneDeliverability.UNKNOWN,
            syncPreferences(probedHost = "$HOST-old").historyTombstoneDeliverability(),
        )
    }
}
