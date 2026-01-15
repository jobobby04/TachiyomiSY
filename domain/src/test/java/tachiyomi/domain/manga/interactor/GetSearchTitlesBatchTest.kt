package tachiyomi.domain.manga.interactor

import exh.metadata.sql.models.SearchTitle
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class GetSearchTitlesBatchTest {

    private lateinit var repository: MangaMetadataRepository
    private lateinit var getSearchTitlesBatch: GetSearchTitlesBatch

    @BeforeEach
    fun setup() {
        repository = mockk()
        getSearchTitlesBatch = GetSearchTitlesBatch(repository)
    }

    @Test
    fun `returns empty map for empty manga list`() = runTest {
        coEvery { repository.getTitlesByIds(emptyList()) } returns emptyMap()

        val result = getSearchTitlesBatch.await(emptyList())

        result.shouldBeEmpty()
        coVerify { repository.getTitlesByIds(emptyList()) }
    }

    @Test
    fun `returns titles for single manga`() = runTest {
        val mangaId = 1L
        val titles = listOf(
            SearchTitle(id = 1, mangaId = mangaId, title = "Original Title", type = 0),
            SearchTitle(id = 2, mangaId = mangaId, title = "Alternative Title", type = 1),
        )
        coEvery { repository.getTitlesByIds(listOf(mangaId)) } returns mapOf(mangaId to titles)

        val result = getSearchTitlesBatch.await(listOf(mangaId))

        result shouldHaveSize 1
        result shouldContainKey mangaId
        result[mangaId] shouldBe titles
    }

    @Test
    fun `returns titles for multiple manga`() = runTest {
        val mangaId1 = 1L
        val mangaId2 = 2L
        val titles1 = listOf(SearchTitle(id = 1, mangaId = mangaId1, title = "Manga One", type = 0))
        val titles2 = listOf(SearchTitle(id = 2, mangaId = mangaId2, title = "Manga Two", type = 0))

        coEvery { repository.getTitlesByIds(listOf(mangaId1, mangaId2)) } returns mapOf(
            mangaId1 to titles1,
            mangaId2 to titles2,
        )

        val result = getSearchTitlesBatch.await(listOf(mangaId1, mangaId2))

        result shouldHaveSize 2
        result shouldContainKey mangaId1
        result shouldContainKey mangaId2
        result[mangaId1] shouldBe titles1
        result[mangaId2] shouldBe titles2
    }

    @Test
    fun `handles manga with no titles`() = runTest {
        val mangaId1 = 1L
        val mangaId2 = 2L
        val titles1 = listOf(SearchTitle(id = 1, mangaId = mangaId1, title = "Manga One", type = 0))

        coEvery { repository.getTitlesByIds(listOf(mangaId1, mangaId2)) } returns mapOf(
            mangaId1 to titles1,
            // mangaId2 has no titles
        )

        val result = getSearchTitlesBatch.await(listOf(mangaId1, mangaId2))

        result shouldHaveSize 1
        result shouldContainKey mangaId1
        result[mangaId1] shouldBe titles1
    }

    @Test
    fun `passes manga IDs directly to repository`() = runTest {
        val mangaIds = listOf(1L, 2L, 3L, 4L, 5L)
        coEvery { repository.getTitlesByIds(mangaIds) } returns emptyMap()

        getSearchTitlesBatch.await(mangaIds)

        coVerify(exactly = 1) { repository.getTitlesByIds(mangaIds) }
    }

    @Test
    fun `handles manga with multiple alternative titles`() = runTest {
        val mangaId = 1L
        val titles = listOf(
            SearchTitle(id = 1, mangaId = mangaId, title = "Primary Title", type = 0),
            SearchTitle(id = 2, mangaId = mangaId, title = "Alt Title 1", type = 1),
            SearchTitle(id = 3, mangaId = mangaId, title = "Alt Title 2", type = 1),
            SearchTitle(id = 4, mangaId = mangaId, title = "Alt Title 3", type = 1),
        )
        coEvery { repository.getTitlesByIds(listOf(mangaId)) } returns mapOf(mangaId to titles)

        val result = getSearchTitlesBatch.await(listOf(mangaId))

        result shouldHaveSize 1
        result[mangaId] shouldBe titles
        result[mangaId]?.size shouldBe 4
    }
}
