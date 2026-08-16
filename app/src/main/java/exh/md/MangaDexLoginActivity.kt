package exh.md

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import exh.md.utils.MdUtil
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.service.SourceManager

class MangaDexLoginActivity : BaseOAuthLoginActivity() {

    @Inject private lateinit var sourceManager: SourceManager

    override fun handleResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                sourceManager.isInitialized.first { it }
                MdUtil.getEnabledMangaDex(sourceManager = sourceManager)?.login(code)
                returnToSettings()
            }
        } else {
            lifecycleScope.launchIO {
                sourceManager.isInitialized.first { it }
                MdUtil.getEnabledMangaDex(sourceManager = sourceManager)?.logout()
                returnToSettings()
            }
        }
    }
}
