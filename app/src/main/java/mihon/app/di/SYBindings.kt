package mihon.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import tachiyomi.data.Database
import tachiyomi.data.manga.FavoritesEntryRepositoryImpl
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.data.manga.MangaMetadataRepositoryImpl
import tachiyomi.data.source.FeedSavedSearchRepositoryImpl
import tachiyomi.data.source.SavedSearchRepositoryImpl
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.repository.FavoritesEntryRepository
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaMetadataRepository
import tachiyomi.domain.source.repository.FeedSavedSearchRepository
import tachiyomi.domain.source.repository.SavedSearchRepository
import tachiyomi.source.local.LocalSourceAllowHiddenFiles
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/**
 * SY-specific file
 * Metro bindings for SY specific repositories and singletons.
 */
@BindingContainer
object SYBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun providesSharedPreferences(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @SingleIn(AppScope::class)
    fun providesLocalSourceAllowHiddenFiles(sourcePreferences: SourcePreferences): LocalSourceAllowHiddenFiles =
        LocalSourceAllowHiddenFiles { sourcePreferences.allowLocalSourceHiddenFolders.get() }

    @Provides
    @SingleIn(AppScope::class)
    fun providesFilterSerializer(): FilterSerializer = FilterSerializer()

    @Provides
    @SingleIn(AppScope::class)
    fun providesMangaMetadataRepository(database: Database): MangaMetadataRepository =
        MangaMetadataRepositoryImpl(database)

    @Provides
    @SingleIn(AppScope::class)
    fun providesMangaMergeRepository(database: Database): MangaMergeRepository =
        MangaMergeRepositoryImpl(database)

    @Provides
    @SingleIn(AppScope::class)
    fun providesFavoritesEntryRepository(database: Database): FavoritesEntryRepository =
        FavoritesEntryRepositoryImpl(database)

    @Provides
    @SingleIn(AppScope::class)
    fun providesSavedSearchRepository(database: Database): SavedSearchRepository =
        SavedSearchRepositoryImpl(database)

    @Provides
    @SingleIn(AppScope::class)
    fun providesFeedSavedSearchRepository(database: Database): FeedSavedSearchRepository =
        FeedSavedSearchRepositoryImpl(database)

    @Provides
    @SingleIn(AppScope::class)
    fun providesCbzCrypto(securityPreferences: SecurityPreferences): CbzCrypto =
        CbzCrypto(securityPreferences)

    @Provides
    fun providesGetMangaId(getManga: GetManga): MetadataSource.GetMangaId =
        getManga

    @Provides
    fun providesInsertFlatMetadata(insertFlatMetadata: InsertFlatMetadata): MetadataSource.InsertFlatMetadata =
        insertFlatMetadata

    @Provides
    fun providesGetFlatMetadataById(getFlatMetadataById: GetFlatMetadataById): MetadataSource.GetFlatMetadataById =
        getFlatMetadataById
}
