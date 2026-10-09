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
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Clock

@AssistedInject
class MangaRestorer(
    @Assisted private val isSync: Boolean,

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

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = restoreRepository.getMangaUrlsBySourceId()

        return backupMangas
            .sortedWith(
                compareBy<BackupManga> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

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
                mergedMangaReferences = backupManga.mergedMangaReferences.map { it.getMergedMangaReference() },
                flatMetadata = backupManga.flatMetadata?.let {
                    // mangaId is assigned during restore; use 0 as placeholder
                    it.getFlatMetadata(0L)
                },
                customMangaInfo = getCustomMangaInfo(backupManga),
                // SY <--
            )
        }

        restoreRepository.restoreManga(entries, /* SY --> */ isSync /* SY <-- */) { manga ->
            fetchInterval.withFetchInterval(manga, now, timeZone, currentFetchWindow).apply {
                // A next update still ahead was worked out with the chapters the backup came from
                manga.nextUpdate
                    .takeIf { it >= currentFetchWindow.first }
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
    private fun getCustomMangaInfo(backupManga: BackupManga): CustomMangaInfo? {
        if (backupManga.customTitle != null ||
            backupManga.customArtist != null ||
            backupManga.customAuthor != null ||
            backupManga.customThumbnailUrl != null ||
            backupManga.customDescription != null ||
            backupManga.customGenre != null ||
            backupManga.customStatus != 0
        ) {
            return CustomMangaInfo(
                id = 0L,
                title = backupManga.customTitle,
                author = backupManga.customAuthor,
                artist = backupManga.customArtist,
                thumbnailUrl = backupManga.customThumbnailUrl,
                description = backupManga.customDescription,
                genre = backupManga.customGenre,
                status = backupManga.customStatus.takeUnless { it == 0 }?.toLong(),
            )
        }
        return null
    }
    // SY <--
}
