package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * SY-specific file
 */
@Inject
class SetMangaUserInfo(
    private val mangaRepository: MangaRepository,
) {

    suspend fun set(
        mangaId: Long,
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        genre: List<String>?,
        status: Long?,
    ) = mangaRepository.updateUserInfo(
        mangaId = mangaId,
        title = title,
        author = author,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        description = description,
        genre = genre,
        status = status,
    )

    suspend fun setAll(updates: List<MangaUserInfoUpdate>) = mangaRepository.updateUserInfoAll(updates)
}
