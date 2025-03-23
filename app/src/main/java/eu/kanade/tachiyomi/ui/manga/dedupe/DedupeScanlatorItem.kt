package eu.kanade.tachiyomi.ui.manga.dedupe

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

class DedupeScanlatorItem(val scanlator: String) : AbstractFlexibleItem<DedupeScanlatorHolder>() {
    override fun getLayoutRes() = R.layout.scanlator_item

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): DedupeScanlatorHolder {
        return DedupeScanlatorHolder(view, adapter as DedupeScanlatorAdapter)
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
        holder: DedupeScanlatorHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(scanlator)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is DedupeScanlatorItem) {
            return scanlator == other.scanlator
        }
        return false
    }

    override fun hashCode(): Int {
        return scanlator.hashCode()
    }
}
