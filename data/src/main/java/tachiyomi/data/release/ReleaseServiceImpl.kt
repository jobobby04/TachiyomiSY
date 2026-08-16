package tachiyomi.data.release

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(repository: String): Release {
        return with(json) {
            networkService.client
                .newCall(GET("https://api.github.com/repos/$repository/releases/latest"))
                .awaitSuccess()
                .parseAs<GithubRelease>()
                .let(releaseMapper)
        }
    }
}
