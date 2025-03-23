package eu.kanade.tachiyomi.ui.manga.dedupe

import android.view.View
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.ScanlatorItemBinding

class DedupeScanlatorHolder(view: View, val adapter: DedupeScanlatorAdapter) : FlexibleViewHolder(view, adapter) {
    val binding = ScanlatorItemBinding.bind(view)

    init {
        binding.add.setOnClickListener {
            adapter.dedupeScanlatorsItemListener.onAddClicked(layoutPosition)
        }
    }

    fun bind(scanlator: String) {
        binding.title.text = scanlator
    }
}
