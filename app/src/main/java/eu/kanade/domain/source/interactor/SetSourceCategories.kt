package eu.kanade.domain.source.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.model.Source

@Inject
class SetSourceCategories(
    private val preferences: SourcePreferences,
) {

    fun await(source: Source, sourceCategories: List<String>) {
        val sourceIdString = source.id.toString()
        preferences.sourcesTabSourcesInCategories.getAndSet { sourcesInCategories ->
            val currentSourceCategories = sourcesInCategories.filterNot {
                it.substringBefore('|') == sourceIdString
            }
            val newSourceCategories = currentSourceCategories + sourceCategories.map {
                "$sourceIdString|$it"
            }
            newSourceCategories.toSet()
        }
    }
}
