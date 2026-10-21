package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import dev.zacsweers.metro.Inject
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.webview.SyncAccessLoginWebViewScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

/**
 * Logs in to the access proxy in front of the SyncYomi server and keeps its session cookie for syncing.
 */
class SyncAccessLoginActivity : BaseActivity() {
    private val graph: AppGraph by lazy { metroGraph() }

    @Inject private lateinit var syncPreferences: SyncPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }
        super.onCreate(savedInstanceState)

        if (!WebViewUtil.supportsWebView(this)) {
            toast(MR.strings.information_webview_required, Toast.LENGTH_LONG)
            finish()
            return
        }

        val host = syncPreferences.clientHost.get()
        if (host.toHttpUrlOrNull() == null) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            clearHostCookies(host)
        }

        setComposeContent {
            SyncAccessLoginWebViewScreen(
                url = "$host/api/sync/v2/capabilities",
                onUp = { finish() },
                onDone = {
                    if (!saveSession(host)) toast(SYMR.strings.sync_access_no_session)
                },
                onPageFinished = { url -> onPageFinished(host, url) },
            )
        }
    }

    private fun onPageFinished(host: String, url: String) {
        val syncHost = host.toHttpUrlOrNull()?.host ?: return
        val pageUrl = url.toHttpUrlOrNull() ?: return
        if (!pageUrl.host.equals(syncHost, ignoreCase = true) || pageUrl.encodedPath.startsWith("/cdn-cgi/access")) {
            return
        }
        saveSession(host)
    }

    private fun saveSession(host: String): Boolean {
        val syncHost = host.toHttpUrlOrNull()?.host ?: return false
        val cookie = CookieManager.getInstance().getCookie(host)
        if (cookie.isNullOrBlank()) return false

        syncPreferences.accessCookie.set(cookie)
        syncPreferences.accessCookieHost.set(syncHost)
        syncPreferences.v2ProbedHost.set("")
        setResult(RESULT_OK)
        finish()
        return true
    }

    private fun clearHostCookies(host: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.getCookie(host)
            ?.split(";")
            ?.mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotEmpty) }
            ?.forEach { name -> cookieManager.setCookie(host, "$name=; Max-Age=0") }
        cookieManager.flush()
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    init {
        registerSecureActivity(this)
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, SyncAccessLoginActivity::class.java)
        }
    }
}
