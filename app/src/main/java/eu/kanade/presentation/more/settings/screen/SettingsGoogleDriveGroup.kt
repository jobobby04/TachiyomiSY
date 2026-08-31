package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.storage.gdrive.GoogleDriveService
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Lets the user link a Google Drive account and automatically point the storage location at it.
 */
@Composable
internal fun googleDriveGroup(
    storagePreferences: StoragePreferences,
): Preference.PreferenceGroup {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { Injekt.get<GoogleDriveService>() }

    val token by service.preferences.token.collectAsState()
    val email by service.preferences.accountEmail.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }

    val signedIn = token.isNotBlank()

    val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()

    // 1. Primary Sign In / Sign Out button (automatically activates/deactivates cloud storage)
    items.add(
        Preference.PreferenceItem.TextPreference(
            title = stringResource(
                if (signedIn) SYMR.strings.pref_gdrive_sign_out else SYMR.strings.pref_gdrive_sign_in,
            ),
            subtitle = if (signedIn) {
                val emailDisplay = email.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                stringResource(SYMR.strings.gdrive_signed_in_summary, emailDisplay)
            } else {
                stringResource(SYMR.strings.gdrive_sign_in_summary)
            },
            onClick = {
                if (signedIn) {
                    service.signOut()
                    context.toast(SYMR.strings.gdrive_signed_out)
                } else {
                    scope.launch { signIn(context, service) }
                }
            },
        ),
    )

    // 2. Advanced Settings toggle
    items.add(
        Preference.PreferenceItem.TextPreference(
            title = stringResource(
                if (showAdvanced) SYMR.strings.gdrive_advanced_hide else SYMR.strings.gdrive_advanced_show,
            ),
            subtitle = stringResource(SYMR.strings.gdrive_advanced_summary),
            onClick = { showAdvanced = !showAdvanced },
        ),
    )

    if (showAdvanced) {
        items.add(
            Preference.PreferenceItem.EditTextPreference(
                preference = service.preferences.clientId,
                title = stringResource(SYMR.strings.pref_gdrive_client_id),
            ),
        )
        items.add(
            Preference.PreferenceItem.EditTextPreference(
                preference = service.preferences.clientSecret,
                title = stringResource(SYMR.strings.pref_gdrive_client_secret),
            ),
        )
    }

    return Preference.PreferenceGroup(
        title = stringResource(SYMR.strings.pref_category_google_drive),
        preferenceItems = items,
    )
}

/**
 * Hands the user off to the browser, then blocks on the loopback socket until Google
 * redirects back with an authorization code.
 */
private suspend fun signIn(
    context: android.content.Context,
    service: GoogleDriveService,
) {
    if (service.auth.getEffectiveClientId().isBlank()) {
        context.toast(SYMR.strings.gdrive_credentials_missing)
        return
    }
    try {
        val session = withContext(Dispatchers.IO) { service.auth.beginSession() }
        // Hand the wait to the service: opening the browser tears this screen down,
        // and with it any coroutine scoped to the composition.
        service.completeSignIn(session)
        context.openInBrowser(session.authorizationUrl)
        context.toast(SYMR.strings.gdrive_sign_in_started)
    } catch (e: Exception) {
        service.logcat(LogPriority.ERROR, e) { "Google Drive sign-in failed" }
        context.toast(e.message ?: "Google Drive sign-in failed")
    }
}
