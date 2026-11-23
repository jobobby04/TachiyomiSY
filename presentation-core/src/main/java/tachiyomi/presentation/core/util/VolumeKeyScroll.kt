package tachiyomi.presentation.core.util

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Lets the volume keys scroll the lists that are currently on screen, one screenful at a time.
 *
 * Lists register themselves with [volumeKeyScrollable] while they are composed. The activity forwards the
 * volume key presses to [scroll], which picks the registered list showing the most on screen.
 *
 * Everything here runs on the main thread, like composition and key dispatching.
 */
object VolumeKeyScroll {

    interface Target {
        /** How much of the list is currently visible in the window, in square pixels. 0 if it isn't visible. */
        fun visibleArea(): Float

        /** Scrolls the list by a screenful. Returns false if the list could not move in that direction. */
        fun scroll(forward: Boolean): Boolean
    }

    private val targets = mutableListOf<Target>()

    fun register(target: Target) {
        targets += target
    }

    fun unregister(target: Target) {
        targets -= target
    }

    /**
     * Scrolls the list showing the most on screen. Lists registered later win ties, as they are the ones
     * composed on top. Returns true if a list scrolled, so the key press doesn't also change the volume.
     */
    fun scroll(forward: Boolean): Boolean {
        var best: Target? = null
        var bestArea = 0f
        for (target in targets) {
            val area = target.visibleArea()
            if (area > 0f && area >= bestArea) {
                best = target
                bestArea = area
            }
        }
        return best?.scroll(forward) ?: false
    }
}

private class ScrollTarget(
    private val state: ScrollableState,
    private val pageSize: () -> Int,
) : VolumeKeyScroll.Target {

    var bounds: Rect = Rect.Zero

    override fun visibleArea(): Float {
        return bounds.width.coerceAtLeast(0f) * bounds.height.coerceAtLeast(0f)
    }

    override fun scroll(forward: Boolean): Boolean {
        if (if (forward) !state.canScrollForward else !state.canScrollBackward) return false
        val page = pageSize().toFloat()
        if (page <= 0f) return false
        // Not animated on purpose, animations are unreadable on e-ink screens
        return state.dispatchRawDelta(if (forward) page else -page) != 0f
    }
}

@Composable
private fun Modifier.volumeKeyScrollable(
    state: ScrollableState,
    pageSize: () -> Int,
): Modifier {
    val target = remember(state) { ScrollTarget(state, pageSize) }
    DisposableEffect(target) {
        VolumeKeyScroll.register(target)
        onDispose { VolumeKeyScroll.unregister(target) }
    }
    return onGloballyPositioned { target.bounds = it.boundsInWindow() }
}

/**
 * Makes the list scrollable with the volume keys, see [VolumeKeyScroll].
 */
@Composable
fun Modifier.volumeKeyScrollable(state: LazyListState): Modifier {
    return volumeKeyScrollable(state) {
        val info = state.layoutInfo
        info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
    }
}

/**
 * Makes the grid scrollable with the volume keys, see [VolumeKeyScroll].
 */
@Composable
fun Modifier.volumeKeyScrollable(state: LazyGridState): Modifier {
    return volumeKeyScrollable(state) {
        val info = state.layoutInfo
        info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
    }
}
