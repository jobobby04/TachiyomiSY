package eu.kanade.tachiyomi.ui.manga.dedupe

import android.view.View
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.SortedScanlatorItemBinding

class SortedDedupeScanlatorHolder(view: View, val adapter: SortedDedupeScanlatorAdapter) : FlexibleViewHolder(view, adapter) {
    val binding = SortedScanlatorItemBinding.bind(view)

    init {
        setDragHandleView(binding.reorder)

        binding.remove.setOnClickListener {
            adapter.sortedDedupeScanlatorsItemListener.onRemoveClicked(absoluteAdapterPosition)
        }
    }

    fun bind(scanlator: String) {
        binding.title.text = scanlator
    }
}
