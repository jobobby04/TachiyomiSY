package eu.kanade.tachiyomi.extension.api

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import exh.source.BlacklistedSources
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@SingleIn(AppScope::class)
class ExtensionApi(
    private val repository: ExtensionStoreRepository,
    private val updateExtensionStores: UpdateExtensionStores,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
    // SY -->
    private val sourcePreferences: SourcePreferences,
    // SY <--
) {

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext { repository.fetchExtensions() }
    }

    suspend fun checkForUpdates(context: Context) {
        updateExtensionStores()

        val extensions = findExtensions()

        // SY -->
        val blacklistEnabled = sourcePreferences.enableSourceBlacklist.get()
        // SY <--

        val installedExtensions = ExtensionLoader.loadExtensions(context)
            .filterIsInstance<LoadResult.Success>()
            .map { it.extension }
            // SY -->
            .filterNot { it.isBlacklisted(blacklistEnabled) }
        // SY <--

        val extensionsWithUpdate = mutableListOf<Extension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            extensionUpdateNotifier.promptUpdates(extensionsWithUpdate.map { it.name })
        }
    }

    // SY -->
    private fun Extension.isBlacklisted(
        blacklistEnabled: Boolean = sourcePreferences.enableSourceBlacklist.get(),
    ): Boolean {
        return pkgName in BlacklistedSources.BLACKLISTED_EXTENSIONS && blacklistEnabled
    }
    // SY <--
}
