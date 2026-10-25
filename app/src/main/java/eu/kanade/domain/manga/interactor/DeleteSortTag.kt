package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * SY-specific file
 */
@Inject
class DeleteSortTag(
    private val preferences: LibraryPreferences,
    private val getSortTag: GetSortTag,
) {

    fun await(tag: String) {
        preferences.sortTagsForLibrary.set(
            (getSortTag.await() - tag).mapIndexed { index, s ->
                CreateSortTag.encodeTag(index, s)
            }.toSet(),
        )
    }
}
