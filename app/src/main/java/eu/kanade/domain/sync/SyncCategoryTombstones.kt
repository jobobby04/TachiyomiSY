package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import tachiyomi.domain.category.service.CategoryTombstones

@Inject
@ContributesBinding(AppScope::class)
class SyncCategoryTombstones(
    private val syncPreferences: SyncPreferences,
) : CategoryTombstones {
    override suspend fun remember(uid: Long) {
        syncPreferences.rememberDeletedCategory(uid)
    }
}
