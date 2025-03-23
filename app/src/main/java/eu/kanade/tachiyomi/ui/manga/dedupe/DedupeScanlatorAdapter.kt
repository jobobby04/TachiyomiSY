package eu.kanade.tachiyomi.ui.manga.dedupe

import eu.davidea.flexibleadapter.FlexibleAdapter

class DedupeScanlatorAdapter(listener: DedupeScanlatorsSettingsState) :
    FlexibleAdapter<DedupeScanlatorItem>(null, listener, true) {

    val dedupeScanlatorsItemListener: DedupeScanlatorsItemListener = listener

    interface DedupeScanlatorsItemListener {
        fun onAddClicked(position: Int)
    }
}
