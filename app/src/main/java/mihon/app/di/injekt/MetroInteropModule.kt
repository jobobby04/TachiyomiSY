package mihon.app.di.injekt

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.di.InjektModule
import eu.kanade.tachiyomi.di.addSingleton
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import exh.GalleryAdder
import exh.eh.EHentaiUpdateHelper
import exh.pref.DelegateSourcePreferences
import exh.source.ExhPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.source.interactor.UpdateMangaFromRemote
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.Database
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.GetSearchMetadata
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.api.InjektRegistrar

@Inject
class MetroInteropModule(
    private val json: Json,
    private val protoBuf: ProtoBuf,
    private val xml: XML,

    private val networkHelper: NetworkHelper,
    private val javaScriptEngine: JavaScriptEngine,

    private val preferenceStore: PreferenceStore,
    private val trackPreferences: TrackPreferences,

    private val extensionManager: ExtensionManager,

    private val coverCache: CoverCache,

    // SY -->
    // Bridge the fork's dependencies into Injekt so the existing Injekt.get<T>()
    // call sites keep working now that the graph is Metro-backed.
    private val application: Application,
    private val context: Context,

    private val sourcePreferences: SourcePreferences,

    private val sourceManager: SourceManager,
    private val trackerManager: TrackerManager,

    private val getFavorites: GetFavorites,
    private val networkToLocalManga: NetworkToLocalManga,

    private val database: Database,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val insertFlatMetadata: InsertFlatMetadata,
    private val getManga: GetManga,
    private val getMergedReferencesById: GetMergedReferencesById,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val updateManga: UpdateManga,
    private val getCategories: GetCategories,
    private val downloadManager: DownloadManager,
    private val downloadPreferences: DownloadPreferences,
    private val filterChaptersForDownload: FilterChaptersForDownload,

    private val exhPreferences: ExhPreferences,
    private val syncPreferences: SyncPreferences,
    private val pagePreviewCache: PagePreviewCache,
    private val getAllManga: GetAllManga,
    private val getFlatMetadataById: GetFlatMetadataById,
    private val galleryAdder: GalleryAdder,
    private val getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata,
    private val getSearchMetadata: GetSearchMetadata,
    private val ehentaiUpdateHelper: EHentaiUpdateHelper,
    private val customMangaInfo: GetCustomMangaInfo,
    private val networkPreferences: NetworkPreferences,
    private val delegateSourcePreferences: DelegateSourcePreferences,
    // SY <--
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(json)
        addSingleton(protoBuf)
        addSingleton(xml)

        addSingleton(networkHelper)
        addSingleton(javaScriptEngine)

        addSingleton(preferenceStore)
        addSingleton(trackPreferences)

        addSingleton(extensionManager)

        addSingleton(coverCache)

        // SY -->
        addSingleton(application)
        addSingleton(context)

        addSingleton(sourcePreferences)

        addSingleton(sourceManager)
        addSingleton(trackerManager)

        addSingleton(getFavorites)
        addSingleton(networkToLocalManga)

        addSingleton(database)
        addSingleton(updateMangaFromRemote)
        addSingleton(insertFlatMetadata)
        addSingleton(getManga)
        addSingleton(getMergedReferencesById)
        addSingleton(syncChaptersWithSource)
        addSingleton(updateManga)
        addSingleton(getCategories)
        addSingleton(downloadManager)
        addSingleton(downloadPreferences)
        addSingleton(filterChaptersForDownload)

        addSingleton(exhPreferences)
        addSingleton(syncPreferences)
        addSingleton(pagePreviewCache)
        addSingleton(getAllManga)
        addSingleton(getFlatMetadataById)
        addSingleton(galleryAdder)
        addSingleton(getExhFavoriteMangaWithMetadata)
        addSingleton(getSearchMetadata)
        addSingleton(ehentaiUpdateHelper)
        addSingleton(customMangaInfo)
        addSingleton(networkPreferences)
        addSingleton(delegateSourcePreferences)
        // SY <--
    }
}
