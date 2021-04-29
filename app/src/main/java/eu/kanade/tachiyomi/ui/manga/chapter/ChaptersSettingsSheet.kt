package eu.kanade.tachiyomi.ui.manga.chapter

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.bluelinelabs.conductor.Router
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.manga.MangaPresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import eu.kanade.tachiyomi.widget.sheet.TabbedBottomSheetDialog
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.source.getMainSource
import kotlinx.coroutines.supervisorScope

class ChaptersSettingsSheet(
    private val router: Router,
    private val presenter: MangaPresenter,
    onGroupClickListener: (ExtendedNavigationView.Group) -> Unit
) : TabbedBottomSheetDialog(router.activity!!) {

    val filters: Filter
    private val sort: Sort
    private val display: Display

    init {
        filters = Filter(router.activity!!)
        filters.onGroupClicked = onGroupClickListener

        sort = Sort(router.activity!!)
        sort.onGroupClicked = onGroupClickListener

        display = Display(router.activity!!)
        display.onGroupClicked = onGroupClickListener

        binding.menu.isVisible = true
        binding.menu.setOnClickListener { it.post { showPopupMenu(it) } }
    }

    override fun getTabViews(): List<View> = listOf(
        filters,
        sort,
        display
    )

    override fun getTabTitles(): List<Int> = listOf(
        R.string.action_filter,
        R.string.action_sort,
        R.string.action_display
    )

    private fun showPopupMenu(view: View) {
        view.popupMenu(
            menuRes = R.menu.default_chapter_filter,
            onMenuItemClick = {
                when (itemId) {
                    R.id.set_as_default -> {
                        SetChapterSettingsDialog(presenter.manga).showDialog(router)
                    }
                }
            }
        )
    }

    /**
     * Filters group (unread, downloaded, ...).
     */
    inner class Filter @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        Settings(context, attrs) {

        private val filterGroup = FilterGroup()

        init {
            setGroups(listOf(filterGroup))
        }

        /**
         * Returns true if there's at least one filter from [FilterGroup] active.
         */
        fun hasActiveFilters(): Boolean {
            return filterGroup.items.any { it.state != State.IGNORE.value } || (presenter.meta?.let { it is MangaDexSearchMetadata && it.filteredScanlators != null } ?: false)
        }

        inner class FilterGroup : Group {

            private val downloaded = Item.TriStateGroup(R.string.action_filter_downloaded, this)
            private val unread = Item.TriStateGroup(R.string.action_filter_unread, this)
            private val bookmarked = Item.TriStateGroup(R.string.action_filter_bookmarked, this)

            private val scanlatorFilters = Item.DrawableSelection(0, this, R.string.scanlator, R.drawable.ic_outline_people_alt_24dp)

            override val header = null
            override val items = listOf(downloaded, unread, bookmarked) + if (presenter.source.getMainSource() is MetadataSource<*, *>) listOf(scanlatorFilters) else emptyList()
            override val footer = null

            override fun initModels() {
                if (presenter.forceDownloaded()) {
                    downloaded.state = State.INCLUDE.value
                    downloaded.enabled = false
                } else {
                    downloaded.state = presenter.onlyDownloaded().value
                }
                unread.state = presenter.onlyUnread().value
                bookmarked.state = presenter.onlyBookmarked().value
            }

            override fun onItemClicked(item: Item) {
                if (item is Item.DrawableSelection) {
                    val meta = presenter.meta
                    if (meta == null) {
                        context.toast(R.string.metadata_corrupted)
                        return
                    } else if (presenter.allChapterScanlators.isEmpty()) {
                        context.toast(R.string.no_scanlators)
                        return
                    }
                    val scanlators = presenter.allChapterScanlators.toList()
                    val filteredScanlators = meta.filteredScanlators?.let { MdUtil.getScanlators(it) }
                    val preselected = if (filteredScanlators.isNullOrEmpty()) scanlators.mapIndexed { index, _ -> index }.toIntArray() else filteredScanlators.map { scanlators.indexOf(it) }.toIntArray()

                    MaterialDialog(context)
                        .title(R.string.select_scanlators)
                        .listItemsMultiChoice(items = presenter.allChapterScanlators.toList(), initialSelection = preselected) { _, selections, _ ->
                            launchIO {
                                supervisorScope {
                                    val selected = selections.map { scanlators[it] }.toSet()
                                    presenter.setScanlatorFilter(selected)
                                    withUIContext { onGroupClicked(this@FilterGroup) }
                                }
                            }
                        }
                        .negativeButton(R.string.action_reset) {
                            launchIO {
                                supervisorScope {
                                    presenter.setScanlatorFilter(presenter.allChapterScanlators)
                                    withUIContext { onGroupClicked(this@FilterGroup) }
                                }
                            }
                        }
                        .positiveButton(android.R.string.ok)
                        .show()
                    return
                }
                item as Item.TriStateGroup
                val newState = when (item.state) {
                    State.IGNORE.value -> State.INCLUDE
                    State.INCLUDE.value -> State.EXCLUDE
                    State.EXCLUDE.value -> State.IGNORE
                    else -> throw Exception("Unknown State")
                }
                item.state = newState.value
                when (item) {
                    downloaded -> presenter.setDownloadedFilter(newState)
                    unread -> presenter.setUnreadFilter(newState)
                    bookmarked -> presenter.setBookmarkedFilter(newState)
                }

                initModels()
                item.group.items.forEach { adapter.notifyItemChanged(it) }
            }
        }
    }

    /**
     * Sorting group (alphabetically, by last read, ...) and ascending or descending.
     */
    inner class Sort @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        Settings(context, attrs) {

        init {
            setGroups(listOf(SortGroup()))
        }

        inner class SortGroup : Group {

            private val source = Item.MultiSort(R.string.sort_by_source, this)
            private val chapterNum = Item.MultiSort(R.string.sort_by_number, this)
            private val uploadDate = Item.MultiSort(R.string.sort_by_upload_date, this)

            override val header = null
            override val items = listOf(source, uploadDate, chapterNum)
            override val footer = null

            override fun initModels() {
                val sorting = presenter.manga.sorting
                val order = if (presenter.manga.sortDescending()) {
                    Item.MultiSort.SORT_DESC
                } else {
                    Item.MultiSort.SORT_ASC
                }

                source.state =
                    if (sorting == Manga.SORTING_SOURCE) order else Item.MultiSort.SORT_NONE
                chapterNum.state =
                    if (sorting == Manga.SORTING_NUMBER) order else Item.MultiSort.SORT_NONE
                uploadDate.state =
                    if (sorting == Manga.SORTING_UPLOAD_DATE) order else Item.MultiSort.SORT_NONE
            }

            override fun onItemClicked(item: Item) {
                item as Item.MultiStateGroup
                val prevState = item.state

                item.group.items.forEach {
                    (it as Item.MultiStateGroup).state =
                        Item.MultiSort.SORT_NONE
                }
                item.state = when (prevState) {
                    Item.MultiSort.SORT_NONE -> Item.MultiSort.SORT_ASC
                    Item.MultiSort.SORT_ASC -> Item.MultiSort.SORT_DESC
                    Item.MultiSort.SORT_DESC -> Item.MultiSort.SORT_ASC
                    else -> throw Exception("Unknown state")
                }

                when (item) {
                    source -> presenter.setSorting(Manga.SORTING_SOURCE)
                    chapterNum -> presenter.setSorting(Manga.SORTING_NUMBER)
                    uploadDate -> presenter.setSorting(Manga.SORTING_UPLOAD_DATE)
                    else -> throw Exception("Unknown sorting")
                }

                presenter.reverseSortOrder()

                item.group.items.forEach { adapter.notifyItemChanged(it) }
            }
        }
    }

    /**
     * Display group, to show the library as a list or a grid.
     */
    inner class Display @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        Settings(context, attrs) {

        init {
            setGroups(listOf(DisplayGroup()))
        }

        inner class DisplayGroup : Group {

            private val displayTitle = Item.Radio(R.string.show_title, this)
            private val displayChapterNum = Item.Radio(R.string.show_chapter_number, this)

            override val header = null
            override val items = listOf(displayTitle, displayChapterNum)
            override val footer = null

            override fun initModels() {
                val mode = presenter.manga.displayMode
                displayTitle.checked = mode == Manga.DISPLAY_NAME
                displayChapterNum.checked = mode == Manga.DISPLAY_NUMBER
            }

            override fun onItemClicked(item: Item) {
                item as Item.Radio
                if (item.checked) return

                item.group.items.forEach { (it as Item.Radio).checked = false }
                item.checked = true

                when (item) {
                    displayTitle -> presenter.setDisplayMode(Manga.DISPLAY_NAME)
                    displayChapterNum -> presenter.setDisplayMode(Manga.DISPLAY_NUMBER)
                    else -> throw NotImplementedError("Unknown display mode")
                }

                item.group.items.forEach { adapter.notifyItemChanged(it) }
            }
        }
    }

    open inner class Settings(context: Context, attrs: AttributeSet?) :
        ExtendedNavigationView(context, attrs) {

        lateinit var adapter: Adapter

        /**
         * Click listener to notify the parent fragment when an item from a group is clicked.
         */
        var onGroupClicked: (Group) -> Unit = {}

        fun setGroups(groups: List<Group>) {
            adapter = Adapter(groups.map { it.createItems() }.flatten())
            recycler.adapter = adapter

            groups.forEach { it.initModels() }
            addView(recycler)
        }

        /**
         * Adapter of the recycler view.
         */
        inner class Adapter(items: List<Item>) : ExtendedNavigationView.Adapter(items) {

            override fun onItemClicked(item: Item) {
                if (item is GroupedItem) {
                    item.group.onItemClicked(item)
                    onGroupClicked(item.group)
                }
            }
        }
    }
}
