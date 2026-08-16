package eu.kanade.domain.source.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
class GetShowLatest(
    private val preferences: UiPreferences,
) {

    fun subscribe(hasSmartSearchConfig: Boolean): Flow<Boolean> {
        return preferences.useNewSourceNavigation.changes()
            .map {
                !hasSmartSearchConfig && !it
            }
    }
}
