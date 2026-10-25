package exh.favorites

import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * SY-specific file
 */
class FavoritesSyncStatusTest {

    private val json = Json

    private fun roundTrip(status: FavoritesSyncStatus): FavoritesSyncStatus =
        json.decodeFromString(json.encodeToString(status))

    /**
     * Every leaf of the [FavoritesSyncStatus] hierarchy, so the list doubles as a
     * compile-time exhaustiveness check: adding a new status without covering it here
     * (and in the UI) is a visible gap.
     */
    private val allStatuses: List<FavoritesSyncStatus> = listOf(
        FavoritesSyncStatus.Idle,
        FavoritesSyncStatus.Initializing,
        FavoritesSyncStatus.SyncError.NotLoggedInSyncError,
        FavoritesSyncStatus.SyncError.FailedToFetchFavorites,
        FavoritesSyncStatus.SyncError.UnknownSyncError("boom"),
        FavoritesSyncStatus.SyncError.GallerySyncError.UnableToAddGalleryToRemote("title", "123"),
        FavoritesSyncStatus.SyncError.GallerySyncError.UnableToDeleteFromRemote,
        FavoritesSyncStatus.SyncError.GallerySyncError.GalleryAddFail("title", "reason"),
        FavoritesSyncStatus.SyncError.GallerySyncError.InvalidGalleryFail("title", "url"),
        FavoritesSyncStatus.BadLibraryState.MangaInMultipleCategories(1, "title", listOf("a", "b")),
        FavoritesSyncStatus.Processing.VerifyingLibrary,
        FavoritesSyncStatus.Processing.DownloadingFavorites,
        FavoritesSyncStatus.Processing.CalculatingRemoteChanges,
        FavoritesSyncStatus.Processing.CalculatingLocalChanges,
        FavoritesSyncStatus.Processing.SyncingCategoryNames,
        FavoritesSyncStatus.Processing.RemovingRemoteGalleries(5),
        FavoritesSyncStatus.Processing.AddingGalleryToRemote(1, 10, true, "title"),
        FavoritesSyncStatus.Processing.RemovingGalleryFromLocal(2, 8),
        FavoritesSyncStatus.Processing.AddingGalleryToLocal(3, 9, false, "title"),
        FavoritesSyncStatus.Processing.CleaningUp,
        FavoritesSyncStatus.CompleteWithErrors(
            listOf(
                FavoritesSyncStatus.SyncError.GallerySyncError.GalleryAddFail("t1", "r1"),
                FavoritesSyncStatus.SyncError.GallerySyncError.UnableToDeleteFromRemote,
            ),
        ),
    )

    @Test
    fun `every status survives a serialization round trip`() {
        allStatuses.shouldForAll { original ->
            roundTrip(original) shouldBe original
        }
    }

    @Test
    fun `round trip preserves the concrete subtype`() {
        val original: FavoritesSyncStatus =
            FavoritesSyncStatus.Processing.AddingGalleryToRemote(4, 7, true, "title")

        val decoded = roundTrip(original)

        decoded shouldBe original
        decoded is FavoritesSyncStatus.Processing.AddingGalleryToRemote
    }

    @Test
    fun `complete with errors keeps its message list intact`() {
        val messages = listOf(
            FavoritesSyncStatus.SyncError.GallerySyncError.UnableToAddGalleryToRemote("a", "1"),
            FavoritesSyncStatus.SyncError.GallerySyncError.GalleryAddFail("b", "reason"),
        )
        val original = FavoritesSyncStatus.CompleteWithErrors(messages)

        val decoded = roundTrip(original) as FavoritesSyncStatus.CompleteWithErrors

        decoded.messages shouldBe messages
    }
}
