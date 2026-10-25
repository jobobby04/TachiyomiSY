package exh.favorites

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.EXH_SOURCE_ID
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.DeleteFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.InsertFavoriteEntries
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Instant

/**
 * SY-specific file
 */
class LocalFavoritesStorageTest {

    // region fixtures

    private fun manga(
        id: Long,
        url: String,
        favorite: Boolean = true,
        source: Long = EXH_SOURCE_ID,
    ) = Manga.create().copy(
        id = id,
        url = url,
        ogTitle = "t",
        source = source,
        favoriteAt = if (favorite) Instant.fromEpochMilliseconds(1) else null,
    )

    private fun category(id: Long, name: String, order: Long) =
        Category(id = id, name = name, order = order, flags = 0)

    private fun entry(
        gid: String,
        token: String,
        category: Int,
        otherGid: String? = null,
        otherToken: String? = null,
    ) = FavoriteEntry(
        title = "t",
        gid = gid,
        token = token,
        otherGid = otherGid,
        otherToken = otherToken,
        category = category,
    )

    private fun parsedManga(fav: Int, url: String) =
        EHentai.ParsedManga(fav, SManga(url, "t"), EHentaiSearchMetadata())

    private fun buildStorage(
        favorites: List<Manga>,
        allCategories: List<Category>,
        mangaToCategories: Map<Long, List<Category>>,
        snapshot: List<FavoriteEntry>,
        deleteFavoriteEntries: DeleteFavoriteEntries = mockk(),
        insertFavoriteEntries: InsertFavoriteEntries = mockk(),
    ): LocalFavoritesStorage {
        val getFavorites = mockk<GetFavorites>()
        coEvery { getFavorites.await() } returns favorites

        val getCategories = mockk<GetCategories>()
        coEvery { getCategories.await() } returns allCategories
        coEvery { getCategories.await(any()) } answers {
            mangaToCategories.getOrDefault(arg(0), emptyList())
        }

        val getFavoriteEntries = mockk<GetFavoriteEntries>()
        coEvery { getFavoriteEntries.await() } returns snapshot

        return LocalFavoritesStorage(
            getFavorites = getFavorites,
            getCategories = getCategories,
            deleteFavoriteEntries = deleteFavoriteEntries,
            getFavoriteEntries = getFavoriteEntries,
            insertFavoriteEntries = insertFavoriteEntries,
        )
    }

    // endregion

    // region getChangedDbEntries

    @Test
    fun `db favorite missing from snapshot is added`() = runTest {
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1")),
            allCategories = listOf(category(1, "a", 0)),
            mangaToCategories = mapOf(1L to listOf(category(1, "a", 0))),
            snapshot = emptyList(),
        )

        val changeSet = storage.getChangedDbEntries()

        changeSet.added shouldBe listOf(entry("g1", "t1", 0))
        changeSet.removed.shouldBeEmpty()
    }

    @Test
    fun `snapshot entry missing from db is removed`() = runTest {
        val storage = buildStorage(
            favorites = emptyList(),
            allCategories = emptyList(),
            mangaToCategories = emptyMap(),
            snapshot = listOf(entry("g1", "t1", 0)),
        )

        val changeSet = storage.getChangedDbEntries()

        changeSet.added.shouldBeEmpty()
        changeSet.removed shouldBe listOf(entry("g1", "t1", 0))
    }

    @Test
    fun `no changes when db and snapshot match`() = runTest {
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1")),
            allCategories = listOf(category(1, "a", 0)),
            mangaToCategories = mapOf(1L to listOf(category(1, "a", 0))),
            snapshot = listOf(entry("g1", "t1", 0)),
        )

        val changeSet = storage.getChangedDbEntries()

        changeSet.added.shouldBeEmpty()
        changeSet.removed.shouldBeEmpty()
    }

    @Test
    fun `alias in snapshot prevents a false removal`() = runTest {
        // The snapshot entry's alias (A/a1) points at the db entry's primary id.
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/A/a1")),
            allCategories = listOf(category(1, "a", 0)),
            mangaToCategories = mapOf(1L to listOf(category(1, "a", 0))),
            snapshot = listOf(entry("B", "b1", 0, otherGid = "A", otherToken = "a1")),
        )

        val changeSet = storage.getChangedDbEntries()

        changeSet.added.shouldBeEmpty()
        changeSet.removed.shouldBeEmpty()
    }

    @Test
    fun `category change counts as both an addition and a removal`() = runTest {
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1")),
            allCategories = listOf(category(1, "a", 0), category(2, "b", 1)),
            mangaToCategories = mapOf(1L to listOf(category(2, "b", 1))),
            snapshot = listOf(entry("g1", "t1", 0)),
        )

        val changeSet = storage.getChangedDbEntries()

        changeSet.added shouldBe listOf(entry("g1", "t1", 1))
        changeSet.removed shouldBe listOf(entry("g1", "t1", 0))
    }

    @Test
    fun `non-favorite mangas are excluded`() = runTest {
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1", favorite = false)),
            allCategories = listOf(category(1, "a", 0)),
            mangaToCategories = mapOf(1L to listOf(category(1, "a", 0))),
            snapshot = emptyList(),
        )

        storage.getChangedDbEntries().added.shouldBeEmpty()
    }

    @Test
    fun `non-eh mangas are excluded`() = runTest {
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1", source = 999)),
            allCategories = listOf(category(1, "a", 0)),
            mangaToCategories = mapOf(1L to listOf(category(1, "a", 0))),
            snapshot = emptyList(),
        )

        storage.getChangedDbEntries().added.shouldBeEmpty()
    }

    @Test
    fun `manga with no category is excluded`() = runTest {
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1")),
            allCategories = listOf(category(1, "a", 0)),
            mangaToCategories = mapOf(1L to emptyList()),
            snapshot = emptyList(),
        )

        storage.getChangedDbEntries().added.shouldBeEmpty()
    }

    @Test
    fun `manga in the last allowed category is kept`() = runTest {
        val categories = (0..9).map { category((it + 1).toLong(), "c$it", it.toLong()) }
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1")),
            allCategories = categories,
            mangaToCategories = mapOf(1L to listOf(category(10, "c9", 9))),
            snapshot = emptyList(),
        )

        storage.getChangedDbEntries().added shouldBe listOf(entry("g1", "t1", 9))
    }

    @Test
    fun `manga beyond max categories is dropped`() = runTest {
        val categories = (0..10).map { category((it + 1).toLong(), "c$it", it.toLong()) }
        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1")),
            allCategories = categories,
            mangaToCategories = mapOf(1L to listOf(category(11, "c10", 10))),
            snapshot = emptyList(),
        )

        storage.getChangedDbEntries().added.shouldBeEmpty()
    }

    // endregion

    // region getChangedRemoteEntries

    @Test
    fun `remote addition is detected`() = runTest {
        val storage = buildStorage(
            favorites = emptyList(),
            allCategories = emptyList(),
            mangaToCategories = emptyMap(),
            snapshot = emptyList(),
        )

        val changeSet = storage.getChangedRemoteEntries(listOf(parsedManga(0, "/g/g5/t5")))

        changeSet.added shouldBe listOf(entry("g5", "t5", 0))
        changeSet.removed.shouldBeEmpty()
    }

    @Test
    fun `remote removal is detected`() = runTest {
        val storage = buildStorage(
            favorites = emptyList(),
            allCategories = emptyList(),
            mangaToCategories = emptyMap(),
            snapshot = listOf(entry("g6", "t6", 0)),
        )

        val changeSet = storage.getChangedRemoteEntries(emptyList())

        changeSet.added.shouldBeEmpty()
        changeSet.removed shouldBe listOf(entry("g6", "t6", 0))
    }

    @Test
    fun `remote category comes from the fav field`() = runTest {
        val storage = buildStorage(
            favorites = emptyList(),
            allCategories = emptyList(),
            mangaToCategories = emptyMap(),
            snapshot = emptyList(),
        )

        val changeSet = storage.getChangedRemoteEntries(listOf(parsedManga(3, "/g/g7/t7")))

        changeSet.added shouldBe listOf(entry("g7", "t7", 3))
    }

    // endregion

    // region snapshotEntries

    @Test
    fun `snapshot deletes then inserts the parsed db entries`() = runTest {
        val deleteFavoriteEntries = mockk<DeleteFavoriteEntries>(relaxed = true)
        val inserted = slot<List<FavoriteEntry>>()
        val insertFavoriteEntries = mockk<InsertFavoriteEntries>()
        coEvery { insertFavoriteEntries.await(capture(inserted)) } returns Unit

        val storage = buildStorage(
            favorites = listOf(manga(1, "/g/g1/t1")),
            allCategories = listOf(category(1, "a", 0)),
            mangaToCategories = mapOf(1L to listOf(category(1, "a", 0))),
            snapshot = emptyList(),
            deleteFavoriteEntries = deleteFavoriteEntries,
            insertFavoriteEntries = insertFavoriteEntries,
        )

        storage.snapshotEntries()

        coVerifyOrder {
            deleteFavoriteEntries.await()
            insertFavoriteEntries.await(any())
        }
        inserted.captured shouldBe listOf(entry("g1", "t1", 0))
    }

    // endregion
}
