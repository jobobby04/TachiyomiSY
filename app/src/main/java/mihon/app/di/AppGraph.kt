package mihon.app.di

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.service.DelayedTrackingUpdateJob
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.library.MetadataUpdateJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.util.ExtensionInstallActivity
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.CrashLogUtil
import exh.eh.EHentaiUpdateWorker
import exh.pref.DelegateSourcePreferences
import exh.source.ExhPreferences
import exh.uconfig.EHConfigurator
import exh.ui.intercept.InterceptActivity
import exh.ui.login.EhLoginActivity
import kotlinx.serialization.json.Json
import mihon.core.metro.IsDebugBuild
import mihon.core.migration.Migration
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.DeleteFavoriteEntries
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack

@DependencyGraph(
    scope = AppScope::class,
    // SY -->
    bindingContainers = [AppBindings::class, SYBindings::class],
    // SY <--
)
interface AppGraph : ViewModelGraph {
    fun inject(app: App)
    fun inject(mainActivity: MainActivity)
    fun inject(readerActivity: ReaderActivity)
    fun inject(webViewActivity: WebViewActivity)
    fun inject(baseOAuthLoginActivity: BaseOAuthLoginActivity)
    fun inject(libraryUpdateJob: LibraryUpdateJob)
    fun inject(metadataUpdateJob: MetadataUpdateJob)
    fun inject(backupRestoreJob: BackupRestoreJob)
    fun inject(backupCreateJob: BackupCreateJob)
    fun inject(delayedTrackingUpdateJob: DelayedTrackingUpdateJob)
    fun inject(downloadJob: DownloadJob)
    fun inject(notificationReceiver: NotificationReceiver)
    fun inject(notificationReceiver: SecureActivityDelegateImpl)
    fun inject(extensionInstallActivity: ExtensionInstallActivity)

    // SY -->
    fun inject(syncDataJob: SyncDataJob)
    fun inject(eHentaiUpdateWorker: EHentaiUpdateWorker)
    fun inject(ehLoginActivity: EhLoginActivity)
    fun inject(interceptActivity: InterceptActivity)
    // SY <--

    val context: Context

    // SY -->
    val migrations: Set<Migration>
    // SY <--

    val viewModelFactory: MetroViewModelFactory

    val basePreferences: BasePreferences
    val uiPreferences: UiPreferences
    val readerPreferences: ReaderPreferences
    val networkPreferences: NetworkPreferences
    val libraryPreferences: LibraryPreferences
    val sourcePreferences: SourcePreferences
    val trackPreferences: TrackPreferences
    val backupPreferences: BackupPreferences
    val storagePreferences: StoragePreferences
    val privacyPreferences: PrivacyPreferences
    val securityPreferences: SecurityPreferences
    val downloadPreferences: DownloadPreferences

    // SY -->
    val syncPreferences: SyncPreferences
    val exhPreferences: ExhPreferences
    val delegateSourcePreferences: DelegateSourcePreferences
    val ehConfigurator: EHConfigurator
    val getAllManga: GetAllManga
    val getChaptersByMangaId: GetChaptersByMangaId
    val pagePreviewCache: PagePreviewCache
    val googleDriveService: GoogleDriveService
    val getFlatMetadataById: GetFlatMetadataById
    val deleteFavoriteEntries: DeleteFavoriteEntries
    val getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata
    // SY <--

    val crashLogUtil: CrashLogUtil

    val downloadManager: DownloadManager

    val updateChecker: AppUpdateChecker

    val trustExtension: TrustExtension

    val sourceManager: SourceManager
    val trackerManager: TrackerManager
    val extensionManager: ExtensionManager
    val chapterCache: ChapterCache
    val downloadCache: DownloadCache

    val json: Json
    val networkHelper: NetworkHelper

    val getFavorites: GetFavorites
    val getCategories: GetCategories
    val resetViewerFlags: ResetViewerFlags
    val resetCategoryFlags: ResetCategoryFlags
    val addTracks: AddTracks
    val insertTrack: InsertTrack
    val getTracks: GetTracks

    val getExtensionStoreCountAsFlow: GetExtensionStoreCountAsFlow

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context, @Provides @IsDebugBuild isDebugBuild: Boolean): AppGraph
    }
}
