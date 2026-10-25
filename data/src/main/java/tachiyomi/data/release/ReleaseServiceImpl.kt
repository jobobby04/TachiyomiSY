package tachiyomi.data.release

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    // SY -->
    override suspend fun latest(repository: String): Release {
        return withContext(Dispatchers.IO) {
            with(json) {
                networkService.client
                    .newCall(GET("https://api.github.com/repos/$repository/releases/latest"))
                    .awaitSuccess()
                    .parseAs<GithubRelease>()
                    .let(releaseMapper)
            }
        }
    }
    // SY <--
}
