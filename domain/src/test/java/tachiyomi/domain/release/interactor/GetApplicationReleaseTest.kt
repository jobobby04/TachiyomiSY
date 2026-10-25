package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationReleaseTest {

    private lateinit var getApplicationRelease: GetApplicationRelease
    private lateinit var releaseService: ReleaseService

    @BeforeEach
    fun beforeEach() {
        releaseService = mockk()

        getApplicationRelease = GetApplicationRelease(releaseService)
    }

    // SY -->
    @Test
    fun `When has update but is preview expect new update`() = runTest {
        val release = Release(
            "200",
            "info",
            "http://example.com/release_link",
            listOf("http://example.com/assets"),
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = true,
                commitCount = 1000,
                versionName = "",
                repository = "test",
                syDebugVersion = "100",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has update expect new update`() = runTest {
        val release = Release(
            "v2.0.0",
            "info",
            "http://example.com/release_link",
            listOf("http://example.com/assets"),
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                syDebugVersion = "0",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has no update expect no new update`() = runTest {
        val release = Release(
            "v1.0.0",
            "info",
            "http://example.com/release_link",
            listOf("http://example.com/assets"),
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v2.0.0",
                syDebugVersion = "0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When now is before three days expect no new update`() = runTest {
        val release = Release(
            "v1.0.0",
            "info",
            "http://example.com/release_link",
            listOf("http://example.com/assets"),
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v2.0.0",
                syDebugVersion = "0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }
    // SY <--
}
