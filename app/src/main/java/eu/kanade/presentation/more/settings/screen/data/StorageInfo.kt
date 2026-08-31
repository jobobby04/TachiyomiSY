package eu.kanade.presentation.more.settings.screen.data

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.storage.gdrive.DriveStorageQuota
import eu.kanade.tachiyomi.data.storage.gdrive.GoogleDriveService
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Composable
fun StorageInfo(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val storagePreferences = remember { Injekt.get<StoragePreferences>() }
    val googleDriveService = remember { Injekt.get<GoogleDriveService>() }
    val baseDir by storagePreferences.baseStorageDirectory.collectAsState()
    val token by googleDriveService.preferences.token.collectAsState()

    val isDrive = token.isNotBlank() && baseDir.startsWith(GoogleDriveService.URI_SCHEME)

    if (isDrive) {
        DriveStorageInfo(modifier, googleDriveService)
    } else {
        val storages = remember { DiskUtil.getExternalStorages(context) }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            storages.forEach {
                StorageInfo(it)
            }
        }
    }
}

@Composable
private fun DriveStorageInfo(
    modifier: Modifier = Modifier,
    googleDriveService: GoogleDriveService,
) {
    val context = LocalContext.current
    var quota by remember { mutableStateOf<DriveStorageQuota?>(null) }
    var userEmail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val about = googleDriveService.api.getAbout()
                if (about != null) {
                    quota = about.storageQuota
                    userEmail = about.user?.emailAddress ?: about.user?.displayName
                    if (!userEmail.isNullOrBlank()) {
                        googleDriveService.preferences.accountEmail.set(userEmail!!)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val currentQuota = quota
    val total = currentQuota?.totalBytes ?: 0L
    val used = currentQuota?.usedBytes ?: 0L
    val available = currentQuota?.availableBytes ?: 0L

    val availableText = Formatter.formatFileSize(context, available)
    val totalText = if (total > 0L) Formatter.formatFileSize(context, total) else "15 GB"
    val emailText = userEmail ?: googleDriveService.preferences.accountEmail.get().takeIf { it.isNotBlank() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = emailText?.takeIf { it.isNotBlank() }
                ?.let { stringResource(SYMR.strings.gdrive_storage_label_account, it) }
                ?: stringResource(SYMR.strings.gdrive_storage_label),
            style = MaterialTheme.typography.header,
        )

        LinearProgressIndicator(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .fillMaxWidth()
                .height(12.dp),
            progress = {
                if (total > 0L) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            },
        )

        Text(
            text = if (total > 0L) {
                stringResource(MR.strings.available_disk_space_info, availableText, totalText)
            } else {
                stringResource(SYMR.strings.gdrive_storage_used, Formatter.formatFileSize(context, used))
            },
            modifier = Modifier.secondaryItemAlpha(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StorageInfo(
    file: File,
) {
    val context = LocalContext.current

    val available = remember(file) { DiskUtil.getAvailableStorageSpace(file) }
    val availableText = remember(available) { Formatter.formatFileSize(context, available) }
    val total = remember(file) { DiskUtil.getTotalStorageSpace(file) }
    val totalText = remember(total) { Formatter.formatFileSize(context, total) }

    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = file.absolutePath,
            style = MaterialTheme.typography.header,
        )

        LinearProgressIndicator(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .fillMaxWidth()
                .height(12.dp),
            progress = { (1 - (available / total.toFloat())) },
        )

        Text(
            text = stringResource(MR.strings.available_disk_space_info, availableText, totalText),
            modifier = Modifier.secondaryItemAlpha(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
