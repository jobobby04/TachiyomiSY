package tachiyomi.domain.release.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    private val lastChecked: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_app_check"), 0)
    }

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()

        // Limit checks to once every 3 days at most
        val nextCheckTime = Instant.ofEpochMilli(lastChecked.get()).plus(3, ChronoUnit.DAYS)
        if (!arguments.forceCheck && now.isBefore(nextCheckTime)) {
            return Result.NoNewUpdate
        }

        val release = service.latest(arguments.repository)

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        // SY -->
        val isNewVersion =
            isNewVersion(arguments.versionName, release.version)
        // SY <--
        return when {
            isNewVersion && arguments.isThirdParty -> Result.ThirdPartyInstallation
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    // Shin -->
    private fun isNewVersion(
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")

        // Release builds: based on releases in "achmadss/Shinyomi" repo
        // tagged as something like "0.1.2"
        val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")

        val newSemVer = newVersion.split(".").map { it.toInt() }
        val oldSemVer = oldVersion.split(".").map { it.toInt() }

        oldSemVer.mapIndexed { index, i ->
            if (newSemVer[index] > i) {
                return true
            }
        }

        return false
    }
    // Shin <--

    data class Arguments(
        val isThirdParty: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
        data object ThirdPartyInstallation : Result
    }
}
