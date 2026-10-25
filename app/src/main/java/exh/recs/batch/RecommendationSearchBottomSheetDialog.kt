package exh.recs.batch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * SY-specific file
 */
@Composable
fun RecommendationSearchBottomSheetDialog(
    selectedCount: Int,
    onDismissRequest: () -> Unit,
    onSearchRequest: () -> Unit,
) {
    val context = LocalContext.current
    val flagsPreference = remember { context.appGraph.sourcePreferences.recommendationSearchFlags }
    val flags by flagsPreference.collectAsState()

    fun toggle(flag: Int) = flagsPreference.set(flags xor flag)

    val includeSources = SearchFlags.hasIncludeSources(flags)
    val includeTrackers = SearchFlags.hasIncludeTrackers(flags)

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column {
            Text(
                text = stringResource(SYMR.strings.rec_search_short),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
            )
            Text(
                text = pluralStringResource(SYMR.plurals.rec_selected_entries, selectedCount, selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(SYMR.strings.rec_search_in),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            SwitchPreferenceWidget(
                title = stringResource(MR.strings.label_sources),
                subtitle = stringResource(SYMR.strings.rec_sources_summary),
                checked = includeSources,
                onCheckedChanged = { toggle(SearchFlags.INCLUDE_SOURCES) },
            )
            SwitchPreferenceWidget(
                title = stringResource(MR.strings.label_tracker_section),
                subtitle = stringResource(SYMR.strings.rec_trackers_summary),
                checked = includeTrackers,
                onCheckedChanged = { toggle(SearchFlags.INCLUDE_TRACKERS) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SwitchPreferenceWidget(
                title = stringResource(SYMR.strings.rec_hide_library_entries),
                checked = SearchFlags.hasHideLibraryResults(flags),
                onCheckedChanged = { toggle(SearchFlags.HIDE_LIBRARY_RESULTS) },
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = onSearchRequest,
                // Only allow searching when at least one service group is selected
                enabled = includeSources || includeTrackers,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            ) {
                Text(stringResource(MR.strings.action_search))
            }
        }
    }
}
