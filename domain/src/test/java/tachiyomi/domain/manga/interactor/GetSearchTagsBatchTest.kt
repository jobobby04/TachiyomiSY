package tachiyomi.domain.manga.interactor

import exh.metadata.sql.models.SearchTag
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

class GetSearchTagsBatchTest {

    private lateinit var repository: MangaMetadataRepository
    private lateinit var getSearchTagsBatch: GetSearchTagsBatch

    @BeforeEach
    fun setup() {
        repository = mockk()
        getSearchTagsBatch = GetSearchTagsBatch(repository)
    }

    @Test
    fun `returns empty map for empty manga list`() = runTest {
        coEvery { repository.getTagsByIds(emptyList()) } returns emptyMap()

        val result = getSearchTagsBatch.await(emptyList())

        result.shouldBeEmpty()
        coVerify { repository.getTagsByIds(emptyList()) }
    }

    @Test
    fun `returns tags for single manga`() = runTest {
        val mangaId = 1L
        val tags = listOf(
            SearchTag(id = 1, mangaId = mangaId, namespace = "genre", name = "Action", type = 0),
            SearchTag(id = 2, mangaId = mangaId, namespace = "genre", name = "Adventure", type = 0),
        )
        coEvery { repository.getTagsByIds(listOf(mangaId)) } returns mapOf(mangaId to tags)

        val result = getSearchTagsBatch.await(listOf(mangaId))

        result shouldHaveSize 1
        result shouldContainKey mangaId
        result[mangaId] shouldBe tags
    }

    @Test
    fun `returns tags for multiple manga`() = runTest {
        val mangaId1 = 1L
        val mangaId2 = 2L
        val tags1 = listOf(SearchTag(id = 1, mangaId = mangaId1, namespace = "genre", name = "Action", type = 0))
        val tags2 = listOf(SearchTag(id = 2, mangaId = mangaId2, namespace = "genre", name = "Romance", type = 0))

        coEvery { repository.getTagsByIds(listOf(mangaId1, mangaId2)) } returns mapOf(
            mangaId1 to tags1,
            mangaId2 to tags2,
        )

        val result = getSearchTagsBatch.await(listOf(mangaId1, mangaId2))

        result shouldHaveSize 2
        result shouldContainKey mangaId1
        result shouldContainKey mangaId2
        result[mangaId1] shouldBe tags1
        result[mangaId2] shouldBe tags2
    }

    @Test
    fun `handles manga with no tags`() = runTest {
        val mangaId1 = 1L
        val mangaId2 = 2L
        val tags1 = listOf(SearchTag(id = 1, mangaId = mangaId1, namespace = "genre", name = "Action", type = 0))

        coEvery { repository.getTagsByIds(listOf(mangaId1, mangaId2)) } returns mapOf(
            mangaId1 to tags1,
            // mangaId2 has no tags
        )

        val result = getSearchTagsBatch.await(listOf(mangaId1, mangaId2))

        result shouldHaveSize 1
        result shouldContainKey mangaId1
        result[mangaId1] shouldBe tags1
    }

    @Test
    fun `passes manga IDs directly to repository`() = runTest {
        val mangaIds = listOf(1L, 2L, 3L, 4L, 5L)
        coEvery { repository.getTagsByIds(mangaIds) } returns emptyMap()

        getSearchTagsBatch.await(mangaIds)

        coVerify(exactly = 1) { repository.getTagsByIds(mangaIds) }
    }
}
