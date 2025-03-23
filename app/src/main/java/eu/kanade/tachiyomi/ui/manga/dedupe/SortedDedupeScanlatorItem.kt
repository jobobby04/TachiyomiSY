package eu.kanade.tachiyomi.ui.manga.dedupe

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

class SortedDedupeScanlatorItem(val scanlator: String) : AbstractFlexibleItem<SortedDedupeScanlatorHolder>() {
    override fun getLayoutRes() = R.layout.sorted_scanlator_item

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): SortedDedupeScanlatorHolder {
        return SortedDedupeScanlatorHolder(view, adapter as SortedDedupeScanlatorAdapter)
    }

    /**
     * Binds the given view holder with this item.
     *
     * @param adapter The adapter of this item.
     * @param holder The holder to bind.
     * @param position The position of this item in the adapter.
     * @param payloads List of partial changes.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SortedDedupeScanlatorHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(scanlator)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SortedDedupeScanlatorItem) {
            return scanlator == other.scanlator
        }
        return false
    }

    override fun hashCode(): Int {
        return scanlator.hashCode()
    }
}
