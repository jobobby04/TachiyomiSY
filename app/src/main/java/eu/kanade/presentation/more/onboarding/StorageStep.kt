package eu.kanade.presentation.more.onboarding

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.tachiyomi.data.storage.gdrive.GoogleDriveService
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class StorageStep : OnboardingStep {

    private val storagePref = Injekt.get<StoragePreferences>().baseStorageDirectory
    private val gdriveService = Injekt.get<GoogleDriveService>()

    private var _isComplete by mutableStateOf(false)

    override val isComplete: Boolean
        get() = _isComplete

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val handler = LocalUriHandler.current
        val scope = rememberCoroutineScope()

        val pickStorageLocation = SettingsDataScreen.storageLocationPicker(storagePref)
        val token by gdriveService.preferences.token.collectAsState()
        val email by gdriveService.preferences.accountEmail.collectAsState()
        val currentDir by storagePref.collectAsState()
        val isDrive = token.isNotBlank() && currentDir.startsWith(GoogleDriveService.URI_SCHEME)

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                stringResource(
                    MR.strings.onboarding_storage_info,
                    stringResource(MR.strings.app_name),
                    if (isDrive) {
                        email.takeIf { it.isNotBlank() }
                            ?.let { stringResource(SYMR.strings.gdrive_storage_label_account, it) }
                            ?: stringResource(SYMR.strings.gdrive_storage_label)
                    } else {
                        SettingsDataScreen.storageLocationText(storagePref)
                    },
                ),
            )

            // 1. Google Drive Cloud Storage (Recommended & Zero local footprint)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        try {
                            val session = withContext(Dispatchers.IO) { gdriveService.auth.beginSession() }
                            gdriveService.completeSignIn(session)
                            context.openInBrowser(session.authorizationUrl)
                            context.toast(SYMR.strings.gdrive_sign_in_started)
                        } catch (e: Exception) {
                            gdriveService.logcat(LogPriority.ERROR, e) { "Google Drive sign-in failed" }
                            context.toast(e.message ?: "Google Drive sign-in failed")
                        }
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (isDrive) {
                            SYMR.strings.gdrive_onboarding_linked
                        } else {
                            SYMR.strings.gdrive_onboarding_use
                        },
                    ),
                )
            }

            // 2. Local folder picker
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        pickStorageLocation.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        context.toast(MR.strings.file_picker_error)
                    }
                },
            ) {
                Text(stringResource(MR.strings.onboarding_storage_action_select))
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(stringResource(MR.strings.onboarding_storage_help_info, stringResource(MR.strings.app_name)))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { handler.openUri(SettingsDataScreen.HELP_URL) },
            ) {
                Text(stringResource(MR.strings.onboarding_storage_help_action))
            }
        }

        LaunchedEffect(Unit) {
            storagePref.changes()
                .collectLatest { _isComplete = storagePref.isSet() }
        }
    }
}
