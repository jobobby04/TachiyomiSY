package eu.kanade.tachiyomi.ui.manga.dedupe

import eu.davidea.flexibleadapter.FlexibleAdapter

class SortedDedupeScanlatorAdapter(listener: DedupeScanlatorsSettingsState) :
    FlexibleAdapter<SortedDedupeScanlatorItem>(null, listener, true) {

    val sortedDedupeScanlatorsItemListener: SortedDedupeScanlatorsItemListener = listener

    interface SortedDedupeScanlatorsItemListener {
        fun onRemoveClicked(position: Int)
    }
}
