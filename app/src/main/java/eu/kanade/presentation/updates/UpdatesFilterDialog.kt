package eu.kanade.presentation.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsScreenModel
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.updates.service.UpdatesPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun UpdatesFilterDialog(
    onDismissRequest: () -> Unit,
    screenModel: UpdatesSettingsScreenModel,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(MR.strings.action_filter),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            FilterSheet(screenModel = screenModel)
        }
    }
}

@Composable
private fun ColumnScope.FilterSheet(
    screenModel: UpdatesSettingsScreenModel,
) {
    val filterDownloaded by screenModel.updatesPreferences.filterDownloaded.collectAsState()
    TriStateItem(
        label = stringResource(SYMR.strings.gdrive_filter_downloaded_local),
        state = filterDownloaded,
        onClick = { screenModel.toggleFilter(UpdatesPreferences::filterDownloaded) },
    )

    val filterCloud by screenModel.updatesPreferences.filterCloud.collectAsState()
    TriStateItem(
        label = stringResource(SYMR.strings.gdrive_filter_stored_in_cloud),
        state = filterCloud,
        onClick = { screenModel.toggleFilter(UpdatesPreferences::filterCloud) },
    )

    val filterUnread by screenModel.updatesPreferences.filterUnread.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filterUnread,
        onClick = { screenModel.toggleFilter(UpdatesPreferences::filterUnread) },
    )

    val filterStarted by screenModel.updatesPreferences.filterStarted.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filterStarted,
        onClick = { screenModel.toggleFilter(UpdatesPreferences::filterStarted) },
    )

    val filterBookmarked by screenModel.updatesPreferences.filterBookmarked.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { screenModel.toggleFilter(UpdatesPreferences::filterBookmarked) },
    )

    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.small))

    val filterExcludedScanlators by screenModel.updatesPreferences.filterExcludedScanlators.collectAsState()

    fun toggleScanlatorFilter() = screenModel.updatesPreferences.filterExcludedScanlators.getAndSet { !it }

    Row(
        modifier = Modifier
            .clickable { toggleScanlatorFilter() }
            .fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(MR.strings.action_filter_excluded_scanlators),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )

        Switch(
            checked = filterExcludedScanlators,
            onCheckedChange = { toggleScanlatorFilter() },
        )
    }
}
