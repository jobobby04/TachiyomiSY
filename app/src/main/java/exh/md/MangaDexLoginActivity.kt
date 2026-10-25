package exh.md

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import exh.md.utils.MdUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.domain.source.service.SourceManager

/**
 * SY-specific file
 */
class MangaDexLoginActivity : BaseOAuthLoginActivity() {

    @Inject private lateinit var sourceManager: SourceManager

    @Inject private lateinit var sourcePreferences: SourcePreferences

    override fun handleResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                MdUtil.getEnabledMangaDex(sourcePreferences, sourceManager)?.login(code)
                returnToSettings()
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                MdUtil.getEnabledMangaDex(sourcePreferences, sourceManager)?.logout()
                returnToSettings()
            }
        }
    }
}
