package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.sync.mangaTombstoneKey
import tachiyomi.domain.manga.model.MangaTombstoneKey
import tachiyomi.domain.manga.service.MangaTombstones

@Inject
@ContributesBinding(AppScope::class)
class SyncMangaTombstones(
    private val syncPreferences: SyncPreferences,
) : MangaTombstones {
    override suspend fun remember(mangas: Collection<MangaTombstoneKey>) {
        syncPreferences.rememberDeletedManga(mangas.map { mangaTombstoneKey(it.sourceId, it.url) })
    }
}
