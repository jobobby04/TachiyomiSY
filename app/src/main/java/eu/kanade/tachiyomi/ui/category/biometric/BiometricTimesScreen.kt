package eu.kanade.tachiyomi.ui.category.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.timepicker.MaterialTimePicker
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.BiometricTimesScreen
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * SY-specific file
 */
class BiometricTimesScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<BiometricTimesViewModel>()

        val state by viewModel.state.collectAsState()

        if (state is BiometricTimesScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as BiometricTimesScreenState.Success

        BiometricTimesScreen(
            state = successState,
            onClickCreate = { viewModel.showDialog(BiometricTimesDialog.Create) },
            onClickDelete = { viewModel.showDialog(BiometricTimesDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        fun showTimePicker(startTime: Duration? = null) {
            val activity = context as? MainActivity ?: return
            val picker = MaterialTimePicker.Builder()
                .setTitleText(
                    if (startTime ==
                        null
                    ) {
                        SYMR.strings.biometric_lock_start_time.getString(context)
                    } else {
                        SYMR.strings.biometric_lock_end_time.getString(context)
                    },
                )
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .build()
            picker.addOnPositiveButtonClickListener {
                val timeRange = picker.hour.hours + picker.minute.minutes
                if (startTime != null) {
                    viewModel.dismissDialog()
                    viewModel.createTimeRange(TimeRange(startTime, timeRange))
                } else {
                    showTimePicker(timeRange)
                }
            }
            picker.addOnDismissListener {
                viewModel.dismissDialog()
            }
            picker.show(activity.supportFragmentManager, null)
        }

        when (val dialog = successState.dialog) {
            null -> {}
            BiometricTimesDialog.Create -> {
                LaunchedEffect(Unit) {
                    showTimePicker()
                }
            }
            is BiometricTimesDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onDelete = { viewModel.deleteTimeRanges(dialog.timeRange) },
                    title = stringResource(SYMR.strings.delete_time_range),
                    text = stringResource(SYMR.strings.delete_time_range_confirmation, dialog.timeRange.formattedString),
                )
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                if (event is BiometricTimesEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
