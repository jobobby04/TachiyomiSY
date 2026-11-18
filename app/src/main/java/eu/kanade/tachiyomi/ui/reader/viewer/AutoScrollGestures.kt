package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * SY-specific file
 */
internal class AutoScrollGestures(
    context: Context,
    private val canActivate: () -> Boolean,
    private val onPauseToggle: () -> Unit,
    private val onActivate: () -> Unit,
    private val onStop: () -> Unit,
    private val onSpeedChange: (Int) -> Unit,
    private val onInteraction: () -> Unit,
    private val onManualScroll: (Int) -> Unit,
    private val stopScroll: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var active = false
        set(value) {
            field = value
            if (!value) resetAutoScrollGestureState()
        }

    var canStart = false
        set(value) {
            field = value
            if (!value) resetAutoScrollActivationGestureState()
        }

    private val tapTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val autoScrollSpeedStep = context.resources.displayMetrics.density * 24f
    private val autoScrollStopThreshold = touchSlop * 4

    private var autoScrollPointerActive = false
    private var autoScrollStartX = 0f
    private var autoScrollStartY = 0f
    private var autoScrollLastY = 0f
    private var autoScrollSpeedAccumulator = 0f
    private var autoScrollTwoFingerLastY = 0f
    private var autoScrollTwoFingerRemainder = 0f
    private var autoScrollDownTime = 0L
    private var autoScrollMoved = false
    private var autoScrollStopTriggered = false
    private var autoScrollMode = AutoScrollGestureMode.NONE
    private var autoScrollActivationTracking = false
    private var autoScrollActivationStartX = 0f
    private var autoScrollActivationStartY = 0f
    private var autoScrollActivationMultiTouch = false

    fun isInProgress(): Boolean {
        return active && autoScrollPointerActive
    }

    private fun resetAutoScrollGestureState() {
        autoScrollPointerActive = false
        autoScrollSpeedAccumulator = 0f
        autoScrollTwoFingerRemainder = 0f
        autoScrollMoved = false
        autoScrollStopTriggered = false
        autoScrollMode = AutoScrollGestureMode.NONE
    }

    private fun resetAutoScrollActivationGestureState() {
        autoScrollActivationTracking = false
        autoScrollActivationMultiTouch = false
    }

    fun trackAutoScrollActivationTouchEvent(ev: MotionEvent) {
        if (!canStart || active) return

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetAutoScrollActivationGestureState()
                autoScrollActivationTracking = true
                autoScrollActivationStartX = ev.x
                autoScrollActivationStartY = ev.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                autoScrollActivationMultiTouch = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) {
                    autoScrollActivationMultiTouch = true
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - autoScrollActivationStartX
                val dy = ev.y - autoScrollActivationStartY
                val shouldActivate =
                    autoScrollActivationTracking &&
                        !autoScrollActivationMultiTouch &&
                        canActivate() &&
                        abs(dx) >= autoScrollStopThreshold &&
                        abs(dx) > abs(dy)

                resetAutoScrollActivationGestureState()
                if (shouldActivate) {
                    onActivate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                resetAutoScrollActivationGestureState()
            }
        }
    }

    fun handleAutoScrollTouchEvent(ev: MotionEvent): Boolean {
        if (!active) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetAutoScrollGestureState()
                autoScrollPointerActive = true
                autoScrollStartX = ev.x
                autoScrollStartY = ev.y
                autoScrollLastY = ev.y
                autoScrollDownTime = ev.eventTime
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                autoScrollPointerActive = true
                autoScrollMode = AutoScrollGestureMode.MOVE
                autoScrollMoved = true
                autoScrollTwoFingerLastY = averageY(ev)
                autoScrollTwoFingerRemainder = 0f
                stopScroll()
                onInteraction()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount >= 2 || autoScrollMode == AutoScrollGestureMode.MOVE) {
                    val currentY = averageY(ev)
                    val deltaY = currentY - autoScrollTwoFingerLastY
                    autoScrollTwoFingerLastY = currentY

                    autoScrollMode = AutoScrollGestureMode.MOVE
                    autoScrollMoved = true
                    autoScrollTwoFingerRemainder += -deltaY

                    val scrollDelta = autoScrollTwoFingerRemainder.toInt()
                    if (scrollDelta != 0) {
                        autoScrollTwoFingerRemainder -= scrollDelta
                        stopScroll()
                        onManualScroll(scrollDelta)
                    }
                    onInteraction()
                    return true
                }

                val dx = ev.x - autoScrollStartX
                val dy = ev.y - autoScrollStartY
                if (autoScrollMode == AutoScrollGestureMode.NONE) {
                    if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                        autoScrollMode = AutoScrollGestureMode.SPEED
                        autoScrollMoved = true
                        autoScrollLastY = ev.y
                        autoScrollSpeedAccumulator = 0f
                        stopScroll()
                        onInteraction()
                        return true
                    }
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        autoScrollMode = AutoScrollGestureMode.STOP
                        autoScrollMoved = true
                        stopScroll()
                        onInteraction()
                    }
                }

                when (autoScrollMode) {
                    AutoScrollGestureMode.SPEED -> {
                        autoScrollSpeedAccumulator += autoScrollLastY - ev.y
                        val steps = (autoScrollSpeedAccumulator / autoScrollSpeedStep).toInt()
                        if (steps != 0) {
                            autoScrollSpeedAccumulator -= steps * autoScrollSpeedStep
                            onSpeedChange(steps)
                            onInteraction()
                        }
                        autoScrollLastY = ev.y
                    }
                    AutoScrollGestureMode.STOP -> {
                        if (!autoScrollStopTriggered && abs(dx) >= autoScrollStopThreshold) {
                            autoScrollStopTriggered = true
                            onStop()
                            onInteraction()
                        }
                    }
                    else -> Unit
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (autoScrollMode == AutoScrollGestureMode.MOVE && ev.pointerCount - 1 < 2) {
                    autoScrollMode = AutoScrollGestureMode.NONE
                    autoScrollTwoFingerRemainder = 0f
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val shouldTogglePause =
                    !autoScrollMoved &&
                        !autoScrollStopTriggered &&
                        autoScrollMode != AutoScrollGestureMode.MOVE &&
                        ev.eventTime - autoScrollDownTime <= tapTimeout
                resetAutoScrollGestureState()
                if (shouldTogglePause) {
                    onPauseToggle()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetAutoScrollGestureState()
                return true
            }
        }

        return true
    }

    private fun averageY(event: MotionEvent): Float {
        var total = 0f
        for (index in 0 until event.pointerCount) {
            total += event.getY(index)
        }
        return total / event.pointerCount
    }
}

private enum class AutoScrollGestureMode {
    NONE,
    SPEED,
    MOVE,
    STOP,
}
