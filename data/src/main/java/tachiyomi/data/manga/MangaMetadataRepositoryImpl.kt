package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class MangaMetadataRepositoryImpl(
    private val database: Database,
) : MangaMetadataRepository {

    override suspend fun getMetadataById(id: Long): SearchMetadata? {
        return database.search_metadataQueries
            .selectByMangaId(id, ::searchMetadataMapper)
            .awaitAsOneOrNull()
    }

    override fun subscribeMetadataById(id: Long): Flow<SearchMetadata?> {
        return database.search_metadataQueries
            .selectByMangaId(id, ::searchMetadataMapper)
            .subscribeToOneOrNull()
    }

    override suspend fun getTagsById(id: Long): List<SearchTag> {
        return database.search_tagsQueries
            .selectByMangaId(id, ::searchTagMapper)
            .awaitAsList()
    }

    override fun subscribeTagsById(id: Long): Flow<List<SearchTag>> {
        return database.search_tagsQueries
            .selectByMangaId(id, ::searchTagMapper)
            .subscribeToList()
    }

    override suspend fun getTitlesById(id: Long): List<SearchTitle> {
        return database.search_titlesQueries
            .selectByMangaId(id, ::searchTitleMapper)
            .awaitAsList()
    }

    override fun subscribeTitlesById(id: Long): Flow<List<SearchTitle>> {
        return database.search_titlesQueries
            .selectByMangaId(id, ::searchTitleMapper)
            .subscribeToList()
    }

    override suspend fun insertFlatMetadata(flatMetadata: FlatMetadata) {
        require(flatMetadata.metadata.mangaId != -1L)

        database.transaction {
            flatMetadata.metadata.run {
                database.search_metadataQueries.upsert(mangaId, uploader, extra, indexedExtra, extraVersion.toLong())
            }
            database.search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.tags.forEach {
                database.search_tagsQueries.insert(it.mangaId, it.namespace, it.name, it.type.toLong())
            }
            database.search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.titles.forEach {
                database.search_titlesQueries.insert(it.mangaId, it.title, it.type.toLong())
            }
        }
    }

    override suspend fun getExhFavoriteMangaWithMetadata(): List<Manga> {
        return database.mangasQueries
            .getEhMangaWithMetadata(EH_SOURCE_ID, EXH_SOURCE_ID, MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getIdsOfFavoriteMangaWithMetadata(): List<Long> {
        return database.mangasQueries
            .getIdsOfFavoriteMangaWithMetadata()
            .awaitAsList()
    }

    override suspend fun getSearchMetadata(): List<SearchMetadata> {
        return database.search_metadataQueries
            .selectAll(::searchMetadataMapper)
            .awaitAsList()
    }

    private fun searchMetadataMapper(
        mangaId: Long,
        uploader: String?,
        extra: String,
        indexedExtra: String?,
        extraVersion: Long,
    ): SearchMetadata {
        return SearchMetadata(
            mangaId = mangaId,
            uploader = uploader,
            extra = extra,
            indexedExtra = indexedExtra,
            extraVersion = extraVersion.toInt(),
        )
    }

    private fun searchTitleMapper(
        mangaId: Long,
        id: Long?,
        title: String,
        type: Long,
    ): SearchTitle {
        return SearchTitle(
            mangaId = mangaId,
            id = id,
            title = title,
            type = type.toInt(),
        )
    }

    private fun searchTagMapper(
        mangaId: Long,
        id: Long?,
        namespace: String?,
        name: String,
        type: Long,
    ): SearchTag {
        return SearchTag(
            mangaId = mangaId,
            id = id,
            namespace = namespace,
            name = name,
            type = type.toInt(),
        )
    }
}
