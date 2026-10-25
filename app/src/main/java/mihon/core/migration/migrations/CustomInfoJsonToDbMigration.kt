package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.io.File

/**
 * SY-specific file
 *
 * Moves the user-edited manga details out of edits.json and into the manga row itself. Runs once:
 * every entry is written to the database and the file is deleted, so later starts are a no-op.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class CustomInfoJsonToDbMigration(
    private val context: Context,
    private val mangaRepository: MangaRepository,
) : Migration {
    override val version: Float = 84f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val editJson = File(context.getExternalFilesDir(null), "edits.json")
        if (editJson.exists() && editJson.isFile) {
            val json = try {
                Json.decodeFromString<MangaList>(
                    editJson.bufferedReader().use { it.readText() },
                )
            } catch (e: Exception) {
                null
            }
            val updates = json?.mangas?.mapNotNull { mangaJson ->
                mangaJson.id?.let { id ->
                    MangaUserInfoUpdate(
                        id = id,
                        title = mangaJson.title?.takeUnless { it.isBlank() },
                        author = mangaJson.author,
                        artist = mangaJson.artist,
                        thumbnailUrl = mangaJson.thumbnailUrl,
                        description = mangaJson.description,
                        genre = mangaJson.genre,
                        status = mangaJson.status?.takeUnless { it == 0L },
                    )
                }
            }
            if (!updates.isNullOrEmpty()) {
                mangaRepository.updateUserInfoAll(updates)
            }
            editJson.delete()
        }
        true
    }

    @Serializable
    private data class MangaList(
        val mangas: List<MangaJson>? = null,
    )

    @Serializable
    private data class MangaJson(
        val id: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    )
}
