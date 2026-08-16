package eu.kanade.tachiyomi.ui.category.biometric

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class BiometricTimesViewModel(
    private val preferences: SecurityPreferences,
    private val application: Application,
) : ViewModel() {

    val state: StateFlow<BiometricTimesScreenState>
        field = MutableStateFlow<BiometricTimesScreenState>(BiometricTimesScreenState.Loading)

    private val _events: Channel<BiometricTimesEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            // todo usecase
            preferences.authenticatorTimeRanges.changes()
                .collectLatest { times ->
                    val context = application
                    state.update {
                        BiometricTimesScreenState.Success(
                            timeRanges = times.toList()
                                .mapNotNull(TimeRange::fromPreferenceString)
                                .map { TimeRangeItem(it, it.getFormattedString(context)) },
                        )
                    }
                }
        }
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createTimeRange(timeRange: TimeRange) {
        // todo usecase
        viewModelScope.launchIO {
            // Do not allow duplicate categories.
            if (timeRangeConflicts(timeRange)) {
                _events.send(BiometricTimesEvent.TimeConflicts)
                return@launchIO
            }

            preferences.authenticatorTimeRanges += timeRange.toPreferenceString()
        }
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param timeRanges The list of categories to delete.
     */
    fun deleteTimeRanges(timeRange: TimeRangeItem) {
        // todo usecase
        viewModelScope.launchIO {
            val state = state.value as? BiometricTimesScreenState.Success ?: return@launchIO
            preferences.authenticatorTimeRanges.set(
                state.timeRanges.filterNot { it == timeRange }.map { it.timeRange.toPreferenceString() }.toSet(),
            )
        }
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun timeRangeConflicts(timeRange: TimeRange): Boolean {
        val state = state.value as? BiometricTimesScreenState.Success ?: return false
        return state.timeRanges.any { timeRange.conflictsWith(it.timeRange) }
    }

    fun showDialog(dialog: BiometricTimesDialog) {
        state.update {
            when (it) {
                BiometricTimesScreenState.Loading -> it
                is BiometricTimesScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        state.update {
            when (it) {
                BiometricTimesScreenState.Loading -> it
                is BiometricTimesScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class BiometricTimesEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : BiometricTimesEvent()
    data object TimeConflicts : LocalizedMessage(SYMR.strings.biometric_lock_time_conflicts)
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed class BiometricTimesDialog {
    data object Create : BiometricTimesDialog()
    data class Delete(val timeRange: TimeRangeItem) : BiometricTimesDialog()
}

sealed class BiometricTimesScreenState {

    @Immutable
    data object Loading : BiometricTimesScreenState()

    @Immutable
    data class Success(
        val timeRanges: List<TimeRangeItem>,
        val dialog: BiometricTimesDialog? = null,
    ) : BiometricTimesScreenState() {

        val isEmpty: Boolean
            get() = timeRanges.isEmpty()
    }
}
