package exh.pref

import dev.zacsweers.metro.Inject
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
class DelegateSourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    val delegateSources: Preference<Boolean> = preferenceStore.getBoolean("eh_delegate_sources", true)

    val useJapaneseTitle: Preference<Boolean> = preferenceStore.getBoolean("use_jp_title", false)
}
