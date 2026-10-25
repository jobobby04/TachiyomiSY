package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

/**
 * SY-specific file
 */
@Serializable
class RankedSearchMetadata : RaisedSearchMetadata() {
    var rank: Int? = null

    override fun createMangaInfo(manga: SManga) = manga
    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> = emptyList()
}
