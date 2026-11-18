package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import ca.mpreg.webgpuviewer.ImageViewContinuous
import ca.mpreg.webgpuviewer.viewer.ImagePage
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ContinuousAutoScroll
import eu.kanade.tachiyomi.ui.reader.viewer.ContinuousAutoScrollViewer
import kotlinx.coroutines.launch
import kotlin.math.max

class WebGpuViewerContinuous(activity: ReaderActivity, val useGap: Boolean = false) :
    WebGpuViewer(activity, isReversed = false, isVertical = true, pager = ImageViewContinuous(activity))
    /* SY --> */,
    ContinuousAutoScrollViewer /* SY <-- */ {

    override val isContinuous: Boolean get() = true

    // How many pages the viewport shows depends on the zoom, and a page on screen has to be
    // decoded rather than merely reserved - so the window follows what the last frame reached.
    override val preloadAhead get() = max(3, state.pagesBelow)
    override val preloadBehind get() = max(2, state.pagesAbove)

    override val cacheSize get() = 2 + 2 * max(4, state.pagesAbove + state.pagesBelow)

    private val state get() = (pager as ImageViewContinuous).state

    // SY -->
    private var autoScrollAnimator: ValueAnimator? = null

    override val autoScroll = ContinuousAutoScroll(
        activity = activity,
        canActivate = { state.scale == 1f },
        stopScroll = { autoScrollAnimator?.cancel() },
        smoothScroll = { animateAutoScroll(state.height.toFloat(), it.inWholeMilliseconds) },
        stepScroll = { animateAutoScroll(state.height * 0.75f, 200L) },
        manualScroll = {
            state.isPanning = true
            scrollByPixels(it.toFloat())
        },
    )

    private val touchContainer = object : FrameLayout(activity) {
        private var trackingAutoScroll = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            val gestures = autoScroll.gestures
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                trackingAutoScroll = gestures.active
            }
            if (trackingAutoScroll) {
                gestures.handleAutoScrollTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    trackingAutoScroll = false
                    state.isPanning = false
                    state.invalidate()
                }
                return true
            }

            gestures.trackAutoScrollActivationTouchEvent(event)
            return super.dispatchTouchEvent(event)
        }
    }.apply { addView(pager, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }

    override fun getView() = touchContainer

    private fun scrollByPixels(delta: Float) {
        state.scrollBy(delta / state.scale)
        state.invalidate()
    }

    private fun animateAutoScroll(distance: Float, durationMillis: Long) {
        autoScrollAnimator?.cancel()
        var applied = 0f
        autoScrollAnimator = ValueAnimator.ofFloat(0f, distance).apply {
            duration = durationMillis
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val position = animation.animatedValue as Float
                scrollByPixels(position - applied)
                applied = position
            }
            doOnEnd {
                autoScrollAnimator = null
                state.isPanning = false
                state.invalidate()
            }
        }
        // Report viewport progress while generating high quality tiles during slow autoscroll.
        state.isPanning = true
        autoScrollAnimator?.start()
    }
    // SY <--

    init {
        state.backgroundColor = readerBackgroundColor()

        state.onViewport = { readThrough ->
            val pageChanged = readThrough !== lastReadThrough
            if (pageChanged) {
                lastReadThrough = readThrough
                readThrough?.let { select(it) }
            }

            val page0 = state.getPage(0)
            val edge = (page0 is TransitionPage && page0.prevChapter == null) ||
                (readThrough is TransitionPage && readThrough.nextChapter == null)

            val first = wasAtEdge == null
            val edgeChanged = edge != wasAtEdge
            wasAtEdge = edge
            if (edge) {
                if (edgeChanged) scope.launch { activity.showMenu() }
            } else if (!first && (edgeChanged || pageChanged) && !activity.isScrollingThroughPages) {
                scope.launch { activity.hideMenu() }
            }
        }
    }

    /** Null until the first frame, so opening mid-document neither shows nor hides. */
    @Volatile
    private var wasAtEdge: Boolean? = null

    @Volatile
    private var lastReadThrough: ImagePage? = null

    /**
     * Mark [imagePage] read - its bottom has cleared the viewport, which is later than
     * onPageChange's "top arrived" and the reason this mode selects from here: scrolling up to
     * reveal the page above would otherwise walk progress back past pages still on screen.
     */
    private fun select(imagePage: ImagePage) {
        val page = readerPageFor(imagePage) ?: return
        scope.launch { activity.onPageSelected(page) }
    }

    private fun readerPageFor(imagePage: ImagePage): ReaderPage? {
        // Off the page itself: a transition page is never currentPage (the scroll walk stops
        // before it), so it is the first thing a preload evicts.
        if (imagePage is TransitionPage) return imagePage.prevChapter?.pages?.lastOrNull()

        // Only an image page needs the lookup - nothing on it names its ReaderPage. Evicted
        // means scrolled well clear of, so there is no progress left to report.
        return (viewerPageFor(imagePage) as? ViewerReaderPage)?.page
    }

    override fun destroy() {
        // SY -->
        autoScroll.destroy()
        // SY <--
        state.onViewport = null
        lastReadThrough = null
        wasAtEdge = null
        super.destroy()
    }

    private fun scrollByHalfPage(direction: Int) {
        state.animateScroll(direction * state.height / 2f)
    }

    override fun moveRight() = scrollByHalfPage(1)

    override fun moveLeft() = scrollByHalfPage(-1)

    override fun moveToPage(page: ReaderPage) {
        super.moveToPage(page)
        wasAtEdge = null
        lastReadThrough = null
        // Also for a jump to the page already showing, which turns nothing to slide in.
        state.resetScroll()
    }

    override fun animateTurn(direction: Int, fromSpread: ImagePage) {
        state.animateSlideIn(direction)
    }
}
