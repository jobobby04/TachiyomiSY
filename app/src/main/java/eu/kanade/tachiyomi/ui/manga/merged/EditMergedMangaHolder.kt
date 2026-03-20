package eu.kanade.tachiyomi.ui.manga.merged

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EditMergedSettingsItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import exh.ui.metadata.adapters.MetadataUIUtil.getResourceColor
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMergedMangaHolder(view: View, val adapter: EditMergedMangaAdapter) : FlexibleViewHolder(view, adapter) {

    lateinit var reference: MergedMangaReference
    var binding = EditMergedSettingsItemBinding.bind(view)

    init {
        binding.remove.setOnClickListener {
            adapter.editMergedMangaItemListener.onDeleteClick(bindingAdapterPosition)
        }
        binding.getChapterUpdates.setOnClickListener {
            adapter.editMergedMangaItemListener.onToggleChapterUpdatesClicked(bindingAdapterPosition)
        }
        binding.download.setOnClickListener {
            adapter.editMergedMangaItemListener.onToggleChapterDownloadsClicked(bindingAdapterPosition)
        }
        binding.moveUp.setOnClickListener {
            adapter.editMergedMangaItemListener.onMoveUpClick(bindingAdapterPosition)
        }
        binding.moveDown.setOnClickListener {
            adapter.editMergedMangaItemListener.onMoveDownClick(bindingAdapterPosition)
        }
    }

    fun bind(item: EditMergedMangaItem) {
        reference = item.mergedMangaReference
        item.mergedManga?.let {
            binding.cover.load(it) {
                transformations(RoundedCornersTransformation(4.dpToPx.toFloat()))
            }
        }

        binding.title.text = Injekt.get<SourceManager>().getOrStub(item.mergedMangaReference.mangaSourceId).toString()
        binding.subtitle.text = item.mergedManga?.title
        updateDownloadChaptersIcon(item.mergedMangaReference.downloadChapters)
        updateChapterUpdatesIcon(item.mergedMangaReference.getChapterUpdates)
        updateMoveButtons()
    }

    fun updateMoveButtons() {
        val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return
        val lastIndex = adapter.currentItems.lastIndex
        binding.moveUp.isEnabled = position > 0
        binding.moveDown.isEnabled = position < lastIndex
        binding.moveUp.alpha = if (binding.moveUp.isEnabled) 1F else 0.5F
        binding.moveDown.alpha = if (binding.moveDown.isEnabled) 1F else 0.5F
    }

    fun updateDownloadChaptersIcon(setTint: Boolean) {
        val color = if (setTint) {
            itemView.context.getResourceColor(R.attr.colorAccent)
        } else {
            itemView.context.getResourceColor(R.attr.colorOnSurface)
        }

        binding.download.drawable.setTint(color)
    }

    fun updateChapterUpdatesIcon(setTint: Boolean) {
        val color = if (setTint) {
            itemView.context.getResourceColor(R.attr.colorAccent)
        } else {
            itemView.context.getResourceColor(R.attr.colorOnSurface)
        }

        binding.getChapterUpdates.drawable.setTint(color)
    }
}
