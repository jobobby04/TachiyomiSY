package eu.kanade.tachiyomi.ui.manga.dedupe

import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.databinding.DedupeScanlatorsSettingsDialogBinding
import tachiyomi.domain.manga.model.SortedScanlator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs

@Stable
class DedupeScanlatorsSettingsState(
    private val onDismissRequest: () -> Unit,
    private val onPositiveClick: (Set<SortedScanlator>) -> Unit,
    private val onEnabledToggled: (Boolean) -> Unit,
) : SortedDedupeScanlatorAdapter.SortedDedupeScanlatorsItemListener, DedupeScanlatorAdapter.DedupeScanlatorsItemListener {
    private var localScanlatorsAdapter: DedupeScanlatorAdapter? by mutableStateOf(null)
    private var localSortedScanlatorsAdapter: SortedDedupeScanlatorAdapter? by mutableStateOf(null)


    fun onViewCreated(
        context: Context,
        binding: DedupeScanlatorsSettingsDialogBinding,
        scanlators: List<String>,
        sortedScanlators: Set<SortedScanlator>,
        dedupeEnabled: Boolean,
    ) {
        localScanlatorsAdapter = DedupeScanlatorAdapter(this)
        localSortedScanlatorsAdapter = SortedDedupeScanlatorAdapter(this)

        binding.dedupeSwitch.isChecked = dedupeEnabled
        binding.dedupeSwitch.setOnCheckedChangeListener { _, isChecked ->
            onEnabledToggled(isChecked)
        }

        binding.scanlators.adapter = localScanlatorsAdapter
        binding.scanlators.layoutManager = LinearLayoutManager(context)

        binding.sortedScanlators.adapter = localSortedScanlatorsAdapter
        binding.sortedScanlators.layoutManager = LinearLayoutManager(context)

        val filteredScanlators = scanlators.subtract(sortedScanlators.map { it.scanlator }.toSet())

        localScanlatorsAdapter?.updateDataSet(
            filteredScanlators.sortedBy { it.lowercase() }.map {
                DedupeScanlatorItem(it)
            }
        )

        localSortedScanlatorsAdapter?.isHandleDragEnabled = true

        localSortedScanlatorsAdapter?.updateDataSet(
            sortedScanlators.sortedBy { it.rank }.map {
                SortedDedupeScanlatorItem(it.scanlator)
            },
        )
    }

    override fun onRemoveClicked(position: Int) {
        val sortedScanlator = localSortedScanlatorsAdapter?.getItem(position) ?: return
        var insertionIndex = localScanlatorsAdapter?.currentItems?.binarySearchBy(sortedScanlator.scanlator.lowercase()) {it.scanlator.lowercase()} ?: 0
        insertionIndex = if (insertionIndex >= 0) insertionIndex else abs(insertionIndex+1)
        localScanlatorsAdapter?.addItem(insertionIndex, DedupeScanlatorItem(sortedScanlator.scanlator))
        localSortedScanlatorsAdapter?.removeItem(position)
    }

    override fun onAddClicked(position: Int) {
        val scanlator = localScanlatorsAdapter?.getItem(position) ?: return
        localSortedScanlatorsAdapter?.addItem(SortedDedupeScanlatorItem(scanlator.scanlator))
        localScanlatorsAdapter?.removeItem(position)
    }

    fun onPositiveButtonClick() {
        val sortedItems = localSortedScanlatorsAdapter?.currentItems ?: listOfNotNull()
        val sortedScanlators = sortedItems.mapIndexed { index, sortedDedupeScanlatorItem -> SortedScanlator(sortedDedupeScanlatorItem.scanlator, index.toLong()) }
        onPositiveClick(sortedScanlators.toSet())
        onDismissRequest()
    }
}

@Composable
fun DedupeScanlatorsSettingsDialog(
    onDismissRequest: () -> Unit,
    sortedScanlators: Set<SortedScanlator>,
    scanlators: Set<String>,
    dedupeEnabled: Boolean,
    onPositiveClick: (Set<SortedScanlator>) -> Unit,
    onEnabledToggled: (Boolean) -> Unit,
) {
    val state = remember {
        DedupeScanlatorsSettingsState(onDismissRequest, onPositiveClick, onEnabledToggled)
    }
    val rememberedScanlators by remember { mutableStateOf(scanlators.toList()) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = state::onPositiveButtonClick) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        text = {
            Column (
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                AndroidView(
                    factory = {
                        factoryContext ->
                        val binding = DedupeScanlatorsSettingsDialogBinding.inflate(LayoutInflater.from(factoryContext))
                        state.onViewCreated(factoryContext, binding, rememberedScanlators, sortedScanlators, dedupeEnabled)
                        binding.root
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true
        )
    )
}
