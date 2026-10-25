package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.manga.model.PagePreview

/**
 * SY-specific file
 */
class PagePreviewKeyer : Keyer<PagePreview> {
    override fun key(data: PagePreview, options: Options): String {
        return data.imageUrl
    }
}
