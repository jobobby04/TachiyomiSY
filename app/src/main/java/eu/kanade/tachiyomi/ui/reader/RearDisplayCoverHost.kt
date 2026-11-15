package eu.kanade.tachiyomi.ui.reader

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga

/**
 * SY-specific file
 *
 * Shows the manga cover on the rear (outer) display of a foldable while the reader is open on the
 * inner display, using the WindowManager dual-screen presentation API.
 */
@OptIn(ExperimentalWindowApi::class)
class RearDisplayCoverHost(
    private val activity: ComponentActivity,
) : WindowAreaPresentationSessionCallback {

    private val controller by lazy { WindowAreaController.getOrCreate() }
    private val executor by lazy { ContextCompat.getMainExecutor(activity) }

    private var rearArea: WindowAreaInfo? = null
    private var session: WindowAreaSessionPresenter? = null
    private var coverView: ImageView? = null
    private var manga: Manga? = null
    private var cover: Drawable? = null
    private var loadJob: Job? = null
    private var configCallbacks: ComponentCallbacks? = null

    /** Whether the cover should be shown whenever the rear display becomes available. */
    private var wanted = false

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun start(showByDefault: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        wanted = showByDefault
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    controller.windowAreaInfos.collect { infos ->
                        rearArea = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                        val status = presentStatus()
                        _isAvailable.value = status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE ||
                            status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE
                        if (wanted && session == null && status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) {
                            present()
                        }
                    }
                } finally {
                    // Activity went to the background; give the rear display back to the system
                    closeSession()
                }
            }
        }
    }

    fun setManga(manga: Manga) {
        if (this.manga == manga) return
        this.manga = manga
        cover = null
        loadCover()
    }

    /**
     * Decodes the cover up front, downsampled to roughly the rear panel's size. Setting it on the view
     * only once it's ready avoids the rear window drawing mid-transition and getting stuck with a stale
     * layout (very large covers took long enough to load for that to happen).
     */
    private fun loadCover(onLoaded: (() -> Unit)? = null): Job? {
        val manga = manga ?: return null
        loadJob?.cancel()
        return activity.lifecycleScope.launch {
            val request = ImageRequest.Builder(activity)
                .data(manga)
                .size(REAR_DISPLAY_MAX_SIZE, REAR_DISPLAY_MAX_SIZE)
                .scale(Scale.FIT)
                .precision(Precision.INEXACT)
                .build()
            cover = activity.imageLoader.execute(request).image?.asDrawable(activity.resources)
            coverView?.setCover(cover)
            onLoaded?.invoke()
        }.also { loadJob = it }
    }

    /** @return false if the rear display can't be used right now */
    fun toggle(): Boolean {
        if (session != null) {
            wanted = false
            closeSession()
            return true
        }
        if (presentStatus() != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) return false
        wanted = true
        present()
        return true
    }

    private fun ImageView.setCover(drawable: Drawable?) {
        setImageDrawable(drawable)
        requestLayout()
        invalidate()
    }

    private fun presentStatus(): WindowAreaCapability.Status? {
        return rearArea
            ?.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
            ?.status
    }

    private fun present() {
        if (cover == null && manga != null) {
            if (loadJob?.isActive != true) loadCover { if (wanted && session == null) present() }
            return
        }
        val area = rearArea ?: return
        try {
            controller.presentContentOnWindowArea(area.token, activity, executor, this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to present on rear display" }
        }
    }

    private fun closeSession() {
        unregisterConfigCallbacks()
        session?.close()
        session = null
        coverView = null
        _isActive.value = false
    }

    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
        this.session = session
        val context = session.context
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            keepScreenOn = true
            setImageDrawable(cover)
        }
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(image, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        session.setContentView(root)
        coverView = image
        _isActive.value = true

        // The rear display may rotate while the session comes up; re-layout so the cover follows it
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                root.requestLayout()
                image.setCover(cover)
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {}
        }
        context.registerComponentCallbacks(callbacks)
        configCallbacks = callbacks
        root.post { image.setCover(cover) }
    }

    private fun unregisterConfigCallbacks() {
        val callbacks = configCallbacks ?: return
        session?.context?.unregisterComponentCallbacks(callbacks)
        configCallbacks = null
    }

    override fun onSessionEnded(t: Throwable?) {
        if (t != null) logcat(LogPriority.ERROR, t) { "Rear display session ended with error" }
        unregisterConfigCallbacks()
        session = null
        coverView = null
        _isActive.value = false
    }

    override fun onContainerVisibilityChanged(isVisible: Boolean) {}

    companion object {
        // Longest edge of the Pixel Fold outer panels is ~2.4k; anything beyond that is wasted memory
        private const val REAR_DISPLAY_MAX_SIZE = 2560
    }
}
