package tachiyomi.domain.manga.repository

import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga

interface MangaMetadataRepository {
    suspend fun getMetadataById(id: Long): SearchMetadata?

    fun subscribeMetadataById(id: Long): Flow<SearchMetadata?>

    suspend fun getTagsById(id: Long): List<SearchTag>

    fun subscribeTagsById(id: Long): Flow<List<SearchTag>>

    suspend fun getTitlesById(id: Long): List<SearchTitle>

    /**
     * Emits the current list of titles for the given metadata id and subsequent updates.
     *
     * @param id The metadata id whose titles will be observed.
     * @return The list of titles associated with the metadata id, or an empty list if none.
     */
    fun subscribeTitlesById(id: Long): Flow<List<SearchTitle>>

    /**
     * Fetches the tags associated with the given metadata IDs.
     *
     * @param ids List of metadata IDs to fetch tags for.
     * @return A map from metadata ID to its list of `SearchTag`. Only IDs that have tag entries are included in the map.
     */
    suspend fun getTagsByIds(ids: List<Long>): Map<Long, List<SearchTag>>

    /**
     * Retrieves the titles for multiple metadata entries by their IDs.
     *
     * @param ids The metadata IDs to fetch titles for.
     * @return A map from each metadata ID to its list of `SearchTitle`.
     */
    suspend fun getTitlesByIds(ids: List<Long>): Map<Long, List<SearchTitle>>

    /**
     * Inserts a flattened metadata entry into the repository.
     *
     * @param flatMetadata The flattened metadata to persist.
     */
    suspend fun insertFlatMetadata(flatMetadata: FlatMetadata)

    /**
     * Inserts the provided RaisedSearchMetadata into persistent storage.
     *
     * @param metadata The metadata object to store.
     */
    suspend fun insertMetadata(metadata: RaisedSearchMetadata) = insertFlatMetadata(metadata.flatten())

    suspend fun getExhFavoriteMangaWithMetadata(): List<Manga>

    suspend fun getIdsOfFavoriteMangaWithMetadata(): List<Long>

    suspend fun getSearchMetadata(): List<SearchMetadata>
}
