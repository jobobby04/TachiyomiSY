package eu.kanade.tachiyomi.ui.category.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.SourceCategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class SourceCategoryScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<SourceCategoryViewModel>()

        val state by viewModel.state.collectAsState()

        if (state is SourceCategoryScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as SourceCategoryScreenState.Success

        SourceCategoryScreen(
            state = successState,
            onClickCreate = { viewModel.showDialog(SourceCategoryDialog.Create) },
            onClickRename = { viewModel.showDialog(SourceCategoryDialog.Rename(it)) },
            onClickDelete = { viewModel.showDialog(SourceCategoryDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            SourceCategoryDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = { viewModel.createCategory(it) },
                    // SY -->
                    categories = successState.categories,
                    title = stringResource(MR.strings.action_add_category),
                    // SY <--
                )
            }
            is SourceCategoryDialog.Rename -> {
                CategoryRenameDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onRename = { viewModel.renameCategory(dialog.category, it) },
                    // SY -->
                    categories = successState.categories,
                    category = dialog.category,
                    // SY <--
                )
            }
            is SourceCategoryDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onDelete = { viewModel.deleteCategory(dialog.category) },
                    // SY -->
                    title = stringResource(MR.strings.delete_category),
                    text = stringResource(MR.strings.delete_category_confirmation, dialog.category),
                    // SY <--
                )
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                if (event is SourceCategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
