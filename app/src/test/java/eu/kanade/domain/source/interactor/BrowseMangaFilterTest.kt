package eu.kanade.domain.source.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BrowseMangaFilterTest {

    @Test
    fun `visible when filters are disabled`() {
        val filter = BrowseMangaFilter(
            hideInLibraryItems = false,
            readMangaIds = emptySet(),
        )

        filter.isVisible(mangaId = 1, isFavorite = true) shouldBe true
    }

    @Test
    fun `hidden when already in library`() {
        val filter = BrowseMangaFilter(
            hideInLibraryItems = true,
            readMangaIds = emptySet(),
        )

        filter.isVisible(mangaId = 1, isFavorite = true) shouldBe false
        filter.isVisible(mangaId = 2, isFavorite = false) shouldBe true
    }

    @Test
    fun `hidden when reading history exists`() {
        val filter = BrowseMangaFilter(
            hideInLibraryItems = false,
            readMangaIds = setOf(2),
        )

        filter.isVisible(mangaId = 1, isFavorite = false) shouldBe true
        filter.isVisible(mangaId = 2, isFavorite = false) shouldBe false
    }
}
