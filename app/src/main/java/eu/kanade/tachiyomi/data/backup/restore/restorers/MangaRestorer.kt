package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import exh.EXHMigrations
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.model.RestoredMergeSettings
import tachiyomi.domain.backup.model.RestoredMergedEntry
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Clock

@AssistedInject
class MangaRestorer(
    // SY -->
    @Assisted private val isSync: Boolean,
    // SY <--

    private val restoreRepository: RestoreRepository,
    private val getCategories: GetCategories,
    private val fetchInterval: FetchInterval,
    // SY -->
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    // SY <--
) {
    // SY -->
    @AssistedFactory
    fun interface Factory {
        fun create(isSync: Boolean): MangaRestorer
    }
    // SY <--

    private val timeZone = TimeZone.currentSystemDefault()
    private val now = Clock.System.now().toLocalDateTime(timeZone)
    private val currentFetchWindow = fetchInterval.getWindow(now.date, timeZone)

    /**
     * Restores [backupMangas] all together, so either every one of them is restored or none is.
     */
    suspend fun restore(
        backupMangas: List<BackupManga>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategoriesByName = getCategories.await().associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val entries = backupMangas.map { backupManga ->
            RestoredManga(
                // SY -->
                manga = EXHMigrations.migrateBackupEntry(backupManga.getMangaImpl()),
                // SY <--
                chapters = backupManga.chapters.map { it.toChapterImpl() },
                categoryIds = backupManga.categories.mapNotNull { backupCategoryOrder ->
                    backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                        dbCategoriesByName[backupCategory.name]?.id
                    }
                },
                history = backupManga.history.map {
                    val history = it.getHistoryImpl()
                    RestoredHistory(it.url, history.readAt, history.readDuration)
                },
                tracks = backupManga.tracking.map { it.getTrackImpl() },
                excludedScanlators = backupManga.excludedScanlators,
                // SY -->
                mergedEntries = backupManga.mergedEntries.map { ref ->
                    RestoredMergedEntry(
                        mangaUrl = ref.mangaUrl,
                        mangaSourceId = ref.mangaSourceId,
                        fetchChapterUpdates = ref.fetchChapterUpdates,
                        downloadChapters = ref.downloadChapters,
                        chapterPriority = ref.chapterPriority,
                    )
                },
                mergeSettings = resolveMergeSettings(backupManga),
                flatMetadata = backupManga.flatMetadata?.let {
                    // mangaId is assigned during restore; use 0 as placeholder
                    it.getFlatMetadata(0L)
                },
                // SY <--
            )
        }

        restoreRepository.restoreManga(entries, /* SY --> */ isSync /* SY <-- */) { manga ->
            fetchInterval.withFetchInterval(manga, now, timeZone, currentFetchWindow).apply {
                // A next update still ahead was worked out with the chapters the backup came from
                manga.nextUpdate
                    ?.takeIf { it >= currentFetchWindow.start }
                    ?.let { nextUpdate = it }
            }
        }

        // SY -->
        if (isSync) {
            mangaRepository.resetIsSyncing()
            chapterRepository.resetIsSyncing()
        }
        // SY <--
    }

    // SY -->

    /**
     * The merge-level settings for a restored entry. New backups carry them in [BackupManga.mergeSettings]
     */
    private fun resolveMergeSettings(backupManga: BackupManga): RestoredMergeSettings? {
        val settings = backupManga.mergeSettings
        if (settings != null) {
            return RestoredMergeSettings(
                chapterSortMode = settings.chapterSortMode,
                infoMangaUrl = settings.infoMangaUrl,
                infoMangaSourceId = settings.infoMangaSourceId,
            )
        }
        val refs = backupManga.mergedEntries
        if (refs.isEmpty()) return null
        val sortMode = refs.firstOrNull()?.chapterSortMode ?: 0
        val infoRef = refs.firstOrNull { it.isInfoManga }
        return RestoredMergeSettings(
            chapterSortMode = sortMode,
            infoMangaUrl = infoRef?.mangaUrl.orEmpty(),
            infoMangaSourceId = infoRef?.mangaSourceId ?: 0L,
        )
    }
    // SY <--
}
