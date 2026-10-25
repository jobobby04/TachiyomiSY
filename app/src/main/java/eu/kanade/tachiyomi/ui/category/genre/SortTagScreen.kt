package eu.kanade.tachiyomi.ui.category.genre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * SY-specific file
 */
class SortTagScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<SortTagViewModel>()

        val state by viewModel.state.collectAsState()

        if (state is SortTagScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as SortTagScreenState.Success

        eu.kanade.presentation.category.SortTagScreen(
            state = successState,
            onClickCreate = { viewModel.showDialog(SortTagDialog.Create) },
            onClickDelete = { viewModel.showDialog(SortTagDialog.Delete(it)) },
            onClickMoveUp = viewModel::moveUp,
            onClickMoveDown = viewModel::moveDown,
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            SortTagDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = { viewModel.createTag(it) },
                    categories = successState.tags,
                    title = stringResource(SYMR.strings.add_tag),
                    extraMessage = stringResource(SYMR.strings.action_add_tags_message),
                    alreadyExistsError = SYMR.strings.error_tag_exists,
                )
            }
            is SortTagDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onDelete = { viewModel.delete(dialog.tag) },
                    title = stringResource(SYMR.strings.delete_tag),
                    text = stringResource(SYMR.strings.delete_tag_confirmation, dialog.tag),
                )
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                if (event is SortTagEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
