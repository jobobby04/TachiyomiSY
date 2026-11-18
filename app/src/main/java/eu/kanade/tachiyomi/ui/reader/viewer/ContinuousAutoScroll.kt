package eu.kanade.tachiyomi.ui.reader.viewer

import android.widget.Toast
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * SY-specific file
 */
class ContinuousAutoScroll internal constructor(
    private val activity: ReaderActivity,
    canActivate: () -> Boolean,
    private val stopScroll: () -> Unit,
    private val smoothScroll: (Duration) -> Unit,
    private val stepScroll: () -> Unit,
    manualScroll: (Int) -> Unit,
) {
    private var autoScrollEnabled = false
    private var autoScrollPaused = false
    private var autoScrollHasRuntimeSpeedOverride = false
    private var autoScrollIntervalMillis = DEFAULT_AUTO_SCROLL_INTERVAL_MILLIS
    private var interruptAutoScrollDelay = false
    private var autoScrollToast: Toast? = null
    private var autoScrollUiBlocked = false
    private var autoScrollGesturesEnabled = true
    private var autoScrollGestureToastsEnabled = true

    internal val gestures = AutoScrollGestures(
        context = activity,
        canActivate = canActivate,
        onPauseToggle = ::toggleAutoScrollPause,
        onActivate = ::startAutoScrollFromGesture,
        onStop = ::stopAutoScroll,
        onSpeedChange = ::adjustAutoScrollSpeed,
        onInteraction = { interruptAutoScrollDelay = true },
        onManualScroll = manualScroll,
        stopScroll = stopScroll,
    )

    fun destroy() {
        stopScroll()
        autoScrollToast?.cancel()
    }

    fun syncAutoScrollSettings(enabled: Boolean, interval: Duration, forceInterval: Boolean = false) {
        val intervalMillis =
            interval.inWholeMilliseconds
                .coerceIn(MIN_AUTO_SCROLL_INTERVAL_MILLIS, MAX_AUTO_SCROLL_INTERVAL_MILLIS)

        if (!enabled) {
            autoScrollEnabled = false
            autoScrollPaused = false
            autoScrollHasRuntimeSpeedOverride = false
            interruptAutoScrollDelay = false
            stopScroll()
            updateAutoScrollGestureMode()
            return
        }

        if (!autoScrollEnabled || forceInterval) {
            autoScrollIntervalMillis = intervalMillis
            autoScrollHasRuntimeSpeedOverride = false
            autoScrollPaused = false
        } else if (!autoScrollHasRuntimeSpeedOverride) {
            autoScrollIntervalMillis = intervalMillis
        }

        autoScrollEnabled = true
        updateAutoScrollGestureMode()
    }

    fun setAutoScrollUiBlocked(blocked: Boolean) {
        autoScrollUiBlocked = blocked
        if (blocked) {
            stopScroll()
            interruptAutoScrollDelay = true
        }
        updateAutoScrollGestureMode()
    }

    fun setAutoScrollGesturesEnabled(enabled: Boolean) {
        autoScrollGesturesEnabled = enabled
        updateAutoScrollGestureMode()
    }

    fun setAutoScrollGestureToastsEnabled(enabled: Boolean) {
        autoScrollGestureToastsEnabled = enabled
        if (!enabled) {
            autoScrollToast?.cancel()
            autoScrollToast = null
        }
    }

    fun performAutoScrollStep(smooth: Boolean) {
        if (
            !autoScrollEnabled ||
            autoScrollPaused ||
            gestures.isInProgress() ||
            autoScrollUiBlocked
        ) {
            return
        }

        if (smooth) {
            smoothScroll(autoScrollIntervalMillis.milliseconds)
        } else {
            stepScroll()
        }
    }

    fun autoScrollDelayMillis(): Long {
        return if (autoScrollPaused || autoScrollUiBlocked || gestures.isInProgress()) {
            AUTO_SCROLL_POLL_DELAY_MILLIS
        } else {
            autoScrollIntervalMillis
        }
    }

    fun consumeAutoScrollDelayInterrupt(): Boolean {
        val shouldInterrupt = interruptAutoScrollDelay
        interruptAutoScrollDelay = false
        return shouldInterrupt
    }

    private fun toggleAutoScrollPause() {
        if (!autoScrollEnabled) return

        autoScrollPaused = !autoScrollPaused
        interruptAutoScrollDelay = true
        stopScroll()

        val action = if (autoScrollPaused) {
            activity.stringResource(MR.strings.action_pause)
        } else {
            activity.stringResource(MR.strings.action_resume)
        }
        showAutoScrollToast("${activity.stringResource(SYMR.strings.eh_autoscroll)}: $action")
    }

    private fun stopAutoScroll() {
        if (!autoScrollEnabled) return

        autoScrollEnabled = false
        autoScrollPaused = false
        autoScrollHasRuntimeSpeedOverride = false
        interruptAutoScrollDelay = true
        updateAutoScrollGestureMode()
        stopScroll()
        activity.viewModel.toggleAutoScroll(false)
        showAutoScrollToast(
            "${activity.stringResource(SYMR.strings.eh_autoscroll)}: " +
                activity.stringResource(SYMR.strings.action_stop),
        )
    }

    private fun adjustAutoScrollSpeed(steps: Int) {
        if (!autoScrollEnabled || steps == 0) return

        stopScroll()
        interruptAutoScrollDelay = true
        autoScrollHasRuntimeSpeedOverride = true

        var newInterval = autoScrollIntervalMillis.toDouble()
        repeat(abs(steps)) {
            newInterval *= if (steps > 0) {
                AUTO_SCROLL_SPEED_UP_FACTOR
            } else {
                AUTO_SCROLL_SPEED_DOWN_FACTOR
            }
        }

        autoScrollIntervalMillis =
            newInterval.roundToLong().coerceIn(
                MIN_AUTO_SCROLL_INTERVAL_MILLIS,
                MAX_AUTO_SCROLL_INTERVAL_MILLIS,
            )

        showAutoScrollToast(
            "${activity.stringResource(SYMR.strings.eh_autoscroll)}: ${formatAutoScrollInterval()}",
        )
    }

    private fun formatAutoScrollInterval(): String {
        val seconds = autoScrollIntervalMillis / 1000f
        val pattern = if (seconds < 10f) "%.2fs" else "%.1fs"
        return String.format(Locale.getDefault(), pattern, seconds)
    }

    private fun showAutoScrollToast(message: String) {
        if (!autoScrollGestureToastsEnabled) return

        autoScrollToast?.cancel()
        autoScrollToast = activity.toast(message)
    }

    private fun startAutoScrollFromGesture() {
        if (autoScrollEnabled || activity.appGraph.readerPreferences.autoscrollInterval.get() <= 0f) return

        activity.viewModel.toggleAutoScroll(true)
        showAutoScrollToast(
            "${activity.stringResource(SYMR.strings.eh_autoscroll)}: " +
                activity.stringResource(MR.strings.action_start),
        )
    }

    private fun updateAutoScrollGestureMode() {
        val gesturesAvailable = autoScrollGesturesEnabled && !autoScrollUiBlocked
        gestures.active = gesturesAvailable && autoScrollEnabled
        gestures.canStart =
            gesturesAvailable &&
            !autoScrollEnabled &&
            activity.appGraph.readerPreferences.autoscrollInterval.get() > 0f
    }
}

private const val AUTO_SCROLL_POLL_DELAY_MILLIS = 100L
private const val DEFAULT_AUTO_SCROLL_INTERVAL_MILLIS = 3_000L
private const val MIN_AUTO_SCROLL_INTERVAL_MILLIS = 250L
private const val MAX_AUTO_SCROLL_INTERVAL_MILLIS = 9_999_000L
private const val AUTO_SCROLL_SPEED_UP_FACTOR = 0.9
private const val AUTO_SCROLL_SPEED_DOWN_FACTOR = 1.1
