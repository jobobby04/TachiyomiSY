package eu.kanade.presentation.browse.components

import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * SY-specific file
 */
@Composable
fun BrowseSourceFloatingActionButton(
    isVisible: Boolean,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmallExtendedFloatingActionButton(
        modifier = modifier,
        text = {
            Text(
                text = if (isVisible) {
                    stringResource(MR.strings.action_filter)
                } else {
                    stringResource(SYMR.strings.saved_searches)
                },
            )
        },
        icon = { Icon(MaterialSymbols.Rounded.FilterList, contentDescription = "") },
        onClick = onFabClick,
    )
}
