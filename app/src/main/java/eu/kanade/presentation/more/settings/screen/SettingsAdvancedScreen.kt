package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.network.interceptor.FlareSolverrInterceptor
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.isDevFlavor
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import exh.debug.SettingsDebugScreen
import exh.log.EHLogLevel
import exh.pref.DelegateSourcePreferences
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.ExhPreferences
import exh.util.toAnnotatedString
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import tachiyomi.core.common.preference.Preference as BasePreference

object SettingsAdvancedScreen : SearchableSettings {

    private val networkHelper: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_dump_crash_logs),
                subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        CrashLogUtil(context).dumpLogs()
                    }
                },
            ),
            /* SY --> Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.verboseLogging(),
                title = stringResource(MR.strings.pref_verbose_logging),
                subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(MR.strings.requires_app_restart)
                    true
                },
            ), SY <-- */
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_debug_info),
                onClick = { navigator.push(DebugInfoScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_onboarding_guide),
                onClick = { navigator.push(OnboardingScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_manage_notifications),
                onClick = {
                    // SY -->
                    val intent = Intent().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            setAction("android.settings.APP_NOTIFICATION_SETTINGS")
                            putExtra("app_package", context.packageName)
                            putExtra("app_uid", context.applicationInfo.uid)
                        }
                    }
                    // SY <--
                    context.startActivity(intent)
                },
            ),
            getBackgroundActivityGroup(),
            getDataGroup(),
            getNetworkGroup(networkPreferences = networkPreferences),
            getLibraryGroup(libraryPreferences = libraryPreferences),
            getDownloadsGroup(downloadPreferences = downloadPreferences),
            getReaderGroup(basePreferences = basePreferences),
            getExtensionsGroup(basePreferences = basePreferences),
            // SY -->
            // getDownloaderGroup(),
            getDataSaverGroup(),
            getDeveloperToolsGroup(),
            // SY <--
        )
    }

    /**
     * Creates the Background Activity preference group used on the Advanced settings screen.
     *
     * The group includes a preference to request ignoring battery optimizations for the app
     * (opens the system settings when available) and a link to the "Don't kill my app!" guide.
     *
     * @return A `PreferenceGroup` containing the battery optimization request and the external guide link.
     */
    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_disable_battery_optimization),
                    subtitle = stringResource(MR.strings.pref_disable_battery_optimization_summary),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = "package:$packageName".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                logcat(LogPriority.ERROR, e)
                                context.toast(MR.strings.battery_optimization_setting_activity_not_found)
                            }
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(MR.strings.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_data),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_invalidate_download_cache),
                    subtitle = stringResource(MR.strings.pref_invalidate_download_cache_summary),
                    onClick = {
                        Injekt.get<DownloadCache>().invalidateCache()
                        context.toast(MR.strings.download_cache_invalidated)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_database),
                    subtitle = stringResource(MR.strings.pref_clear_database_summary),
                    onClick = { navigator.push(ClearDatabaseScreen()) },
                ),
            ),
        )
    }

    /**
     * Builds the network preference group for the advanced settings screen.
     *
     * Contains actions and preferences for cookie and WebView cleanup, DNS-over-HTTPS selection,
     * user-agent editing and reset, and FlareSolverr configuration (enable toggle, URL, notifications,
     * and a test action that can update the user-agent).
     *
     * @param networkPreferences Preferences provider used to read and persist network-related settings
     *        (DoH provider, user agent, FlareSolverr settings, and related flags).
     * @return A PreferenceGroup with network-related preference items.
     */
    @Composable
    private fun getNetworkGroup(
        networkPreferences: NetworkPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()

        val context = LocalContext.current
        val networkHelper = remember { Injekt.get<NetworkHelper>() }

        val userAgentPref = networkPreferences.defaultUserAgent()
        val userAgent by userAgentPref.collectAsState()

        val flareSolverrUrlPref = networkPreferences.flareSolverrUrl()
        val enableFlareSolverrPref = networkPreferences.enableFlareSolverr()
        val enableFlareSolverr by enableFlareSolverrPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_webview_data),
                    onClick = {
                        try {
                            WebView(context).run {
                                setDefaultSettings()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                            }
                            WebStorage.getInstance().deleteAllData()
                            context.applicationInfo?.dataDir?.let {
                                File("$it/app_webview/").deleteRecursively()
                            }
                            context.toast(MR.strings.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                    ),
                    title = stringResource(MR.strings.pref_dns_over_https),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = userAgentPref,
                    title = stringResource(MR.strings.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                            context.toast(MR.strings.requires_app_restart)
                        } catch (_: IllegalArgumentException) {
                            context.toast(MR.strings.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableFlareSolverrPref,
                    title = stringResource(MR.strings.pref_enable_flare_solverr),
                    subtitle = stringResource(MR.strings.pref_enable_flare_solverr_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = flareSolverrUrlPref,
                    title = stringResource(MR.strings.pref_flare_solverr_url),
                    enabled = enableFlareSolverr,
                    subtitle = stringResource(MR.strings.pref_flare_solverr_url_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = networkPreferences.showFlareSolverrNotifications(),
                    title = stringResource(MR.strings.pref_show_flare_solverr_notifications),
                    subtitle = stringResource(MR.strings.pref_show_flare_solverr_notifications_summary),
                    enabled = enableFlareSolverr,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_test_flare_solverr_and_update_user_agent),
                    enabled = enableFlareSolverr,
                    subtitle = stringResource(MR.strings.pref_test_flare_solverr_and_update_user_agent_summary_full),
                    onClick = {
                        scope.launch {
                            testFlareSolverrAndUpdateUserAgent(flareSolverrUrlPref, userAgentPref, context)
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_library),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = { LibraryUpdateJob.startNow(context, target = LibraryUpdateJob.Target.COVERS) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_viewer_flags),
                    subtitle = stringResource(MR.strings.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = Injekt.get<ResetViewerFlags>().await()
                            withUIContext {
                                val message = if (success) {
                                    MR.strings.pref_reset_viewer_flags_success
                                } else {
                                    MR.strings.pref_reset_viewer_flags_error
                                }
                                context.toast(message)
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateMangaTitles(),
                    title = stringResource(MR.strings.pref_update_library_manga_titles),
                    subtitle = stringResource(MR.strings.pref_update_library_manga_titles_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.disallowNonAsciiFilenames(),
                    title = stringResource(MR.strings.pref_disallow_non_ascii_filenames),
                    subtitle = stringResource(MR.strings.pref_disallow_non_ascii_filenames_details),
                ),
            ),
        )
    }

    // SY ->
    @Composable
    private fun getDownloadsGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_downloads),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.includeChapterUrlHash(),
                    title = stringResource(SYMR.strings.pref_include_chapter_url_hash),
                    subtitle = stringResource(SYMR.strings.pref_include_chapter_url_hash_desc),
                ),
            ),
        )
    }
    // <- SY

    @Composable
    private fun getReaderGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val chooseColorProfile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                basePreferences.displayProfile().set(uri.toString())
            }
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = basePreferences.hardwareBitmapThreshold(),
                    entries = GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS
                        .mapIndexed { index, option ->
                            val display = if (index == 0) {
                                stringResource(MR.strings.pref_hardware_bitmap_threshold_default, option)
                            } else {
                                option.toString()
                            }
                            option to display
                        }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_hardware_bitmap_threshold),
                    subtitleProvider = { value, options ->
                        stringResource(MR.strings.pref_hardware_bitmap_threshold_summary, options[value].orEmpty())
                    },
                    enabled = !ImageUtil.HARDWARE_BITMAP_UNSUPPORTED &&
                        GLUtil.DEVICE_TEXTURE_LIMIT > GLUtil.SAFE_TEXTURE_LIMIT,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePreferences.alwaysDecodeLongStripWithSSIV(),
                    title = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_2),
                    subtitle = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_display_profile),
                    subtitle = basePreferences.displayProfile().get(),
                    onClick = {
                        chooseColorProfile.launch(arrayOf("*/*"))
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller()
        var shizukuMissing by rememberSaveable { mutableStateOf(false) }
        val trustExtension = remember { Injekt.get<TrustExtension>() }

        if (shizukuMissing) {
            val dismiss = { shizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(MR.strings.ext_installer_shizuku)) },
                text = { Text(text = stringResource(MR.strings.ext_installer_shizuku_unavailable_dialog)) },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://shizuku.rikka.app/download")
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_extensions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = extensionInstallerPref,
                    entries = extensionInstallerPref.entries
                        .filter {
                            // TODO: allow private option in stable versions once URL handling is more fleshed out
                            if (isPreviewBuildType || isDevFlavor) {
                                true
                            } else {
                                it != BasePreferences.ExtensionInstaller.PRIVATE
                            }
                        }
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.ext_installer_pref),
                    onValueChanged = {
                        if (it == BasePreferences.ExtensionInstaller.SHIZUKU &&
                            !context.isShizukuInstalled
                        ) {
                            shizukuMissing = true
                            false
                        } else {
                            true
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.ext_revoke_trust),
                    onClick = {
                        trustExtension.revokeAll()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    /**
     * Shows a modal dialog that lets the user pick cleanup options for downloaded chapters and confirms the selection.
     *
     * The dialog lists options from R.array.clean_up_downloads and invokes [onCleanupDownloads] when the user confirms.
     *
     * @param onDismissRequest Called when the dialog should be dismissed without performing cleanup.
     * @param onCleanupDownloads Called when the user confirms cleanup. The first boolean (`removeRead`) is true if the "remove read" option (the second entry in the resource array) was selected. The second boolean (`removeNonFavorite`) is true if the "remove non-favorite" option (the third entry in the resource array) was selected.
     */
    @Composable
    fun CleanupDownloadsDialog(
        onDismissRequest: () -> Unit,
        onCleanupDownloads: (removeRead: Boolean, removeNonFavorite: Boolean) -> Unit,
    ) {
        val context = LocalContext.current
        val options = remember { context.resources.getStringArray(R.array.clean_up_downloads).toList() }
        val selection = remember {
            // Option 0 is always required and shown as checked via the UI logic.
            // Expected array structure: [0] = mandatory option, [1] = remove read, [2] = remove non-favorite
            mutableStateListOf<String>().apply {
                options.getOrNull(1)?.let { add(it) }
                options.getOrNull(2)?.let { add(it) }
            }
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(SYMR.strings.clean_up_downloaded_chapters)) },
            text = {
                LazyColumn {
                    options.forEachIndexed { index, option ->
                        item {
                            LabeledCheckbox(
                                label = option,
                                checked = index == 0 || selection.contains(option),
                                onCheckedChange = {
                                    when (it) {
                                        true -> selection.add(option)
                                        false -> selection.remove(option)
                                    }
                                },
                            )
                        }
                    }
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        val removeRead = options.getOrNull(1)?.let { it in selection } ?: false
                        val removeNonFavorite = options.getOrNull(2)?.let { it in selection } ?: false
                        onCleanupDownloads(removeRead, removeNonFavorite)
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

// TODO: Re-enable getDownloaderGroup() for download cleanup feature.

    /**
     * Creates the "Data Saver" preference group that exposes settings for source data-saver behavior.
     *
     * The group contains preferences for selecting the data-saver mode, configuring the Bandwidth Hero
     * server, toggles for using the data-saver downloader and ignoring JPEG/GIF, image quality options,
     * an image-format toggle with a dynamic subtitle, and a black-and-white color toggle. Preference
     * enabled/disabled states reflect the current data-saver mode (for example, server and color-BW are
     * only enabled for `Bandwidth Hero`, and most controls are disabled when the mode is `None`).
     *
     * @return A Preference.PreferenceGroup containing the Data Saver preferences and their configured states.
     */

    @Composable
    private fun getDataSaverGroup(): Preference.PreferenceGroup {
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val dataSaver by sourcePreferences.dataSaver().collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.data_saver),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = sourcePreferences.dataSaver(),
                    title = stringResource(SYMR.strings.data_saver),
                    subtitle = stringResource(SYMR.strings.data_saver_summary),
                    entries = persistentMapOf(
                        DataSaver.NONE to stringResource(MR.strings.disabled),
                        DataSaver.BANDWIDTH_HERO to stringResource(SYMR.strings.bandwidth_hero),
                        DataSaver.WSRV_NL to stringResource(SYMR.strings.wsrv),
                    ),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = sourcePreferences.dataSaverServer(),
                    title = stringResource(SYMR.strings.bandwidth_data_saver_server),
                    subtitle = stringResource(SYMR.strings.data_saver_server_summary),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverDownloader(),
                    title = stringResource(SYMR.strings.data_saver_downloader),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverIgnoreJpeg(),
                    title = stringResource(SYMR.strings.data_saver_ignore_jpeg),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverIgnoreGif(),
                    title = stringResource(SYMR.strings.data_saver_ignore_gif),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = sourcePreferences.dataSaverImageQuality(),
                    title = stringResource(SYMR.strings.data_saver_image_quality),
                    subtitle = stringResource(SYMR.strings.data_saver_image_quality_summary),
                    entries = listOf(
                        "10%",
                        "20%",
                        "40%",
                        "50%",
                        "70%",
                        "80%",
                        "90%",
                        "95%",
                    ).associateBy { it.trimEnd('%').toInt() }.toImmutableMap(),
                    enabled = dataSaver != DataSaver.NONE,
                ),
                kotlin.run {
                    val dataSaverImageFormatJpeg by sourcePreferences.dataSaverImageFormatJpeg()
                        .collectAsState()
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.dataSaverImageFormatJpeg(),
                        title = stringResource(SYMR.strings.data_saver_image_format),
                        subtitle = if (dataSaverImageFormatJpeg) {
                            stringResource(SYMR.strings.data_saver_image_format_summary_on)
                        } else {
                            stringResource(SYMR.strings.data_saver_image_format_summary_off)
                        },
                        enabled = dataSaver != DataSaver.NONE,
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.dataSaverColorBW(),
                    title = stringResource(SYMR.strings.data_saver_color_bw),
                    enabled = dataSaver == DataSaver.BANDWIDTH_HERO,
                ),
            ),
        )
    }

    /**
     * Builds the Developer Tools preference group for the advanced settings screen.
     *
     * The group contains developer-focused preferences including toggles for hentai features
     * and delegated sources, log level selection, source blacklist toggle, database
     * encryption toggle (shows a confirmation dialog when enabling), and a shortcut to the
     * debug menu.
     *
     * @return A Preference.PreferenceGroup containing the developer tools preference items.
     */
    @Composable
    private fun getDeveloperToolsGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val exhPreferences = remember { Injekt.get<ExhPreferences>() }
        val delegateSourcePreferences = remember { Injekt.get<DelegateSourcePreferences>() }
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.developer_tools),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = exhPreferences.isHentaiEnabled(),
                    title = stringResource(SYMR.strings.toggle_hentai_features),
                    subtitle = stringResource(SYMR.strings.toggle_hentai_features_summary),
                    onValueChanged = {
                        if (it) {
                            BlacklistedSources.HIDDEN_SOURCES += EH_SOURCE_ID
                            BlacklistedSources.HIDDEN_SOURCES += EXH_SOURCE_ID
                        } else {
                            BlacklistedSources.HIDDEN_SOURCES -= EH_SOURCE_ID
                            BlacklistedSources.HIDDEN_SOURCES -= EXH_SOURCE_ID
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = delegateSourcePreferences.delegateSources(),
                    title = stringResource(SYMR.strings.toggle_delegated_sources),
                    subtitle = stringResource(
                        SYMR.strings.toggle_delegated_sources_summary,
                        stringResource(MR.strings.app_name),
                        AndroidSourceManager.DELEGATED_SOURCES.values.map { it.sourceName }.distinct()
                            .joinToString(),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = exhPreferences.logLevel(),
                    title = stringResource(SYMR.strings.log_level),
                    subtitle = stringResource(SYMR.strings.log_level_summary),
                    entries = EHLogLevel.entries.mapIndexed { index, ehLogLevel ->
                        index to "${context.stringResource(ehLogLevel.nameRes)} (${
                            context.stringResource(ehLogLevel.description)
                        })"
                    }.toMap().toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.enableSourceBlacklist(),
                    title = stringResource(SYMR.strings.enable_source_blacklist),
                    subtitle = stringResource(
                        SYMR.strings.enable_source_blacklist_summary,
                        stringResource(MR.strings.app_name),
                    ),
                ),
                kotlin.run {
                    var enableEncryptDatabase by rememberSaveable { mutableStateOf(false) }

                    if (enableEncryptDatabase) {
                        val dismiss = { enableEncryptDatabase = false }
                        AlertDialog(
                            onDismissRequest = dismiss,
                            title = { Text(text = stringResource(SYMR.strings.encrypt_database)) },
                            text = {
                                Text(
                                    text = remember {
                                        HtmlCompat.fromHtml(
                                            context.stringResource(SYMR.strings.encrypt_database_message),
                                            HtmlCompat.FROM_HTML_MODE_COMPACT,
                                        ).toAnnotatedString()
                                    },
                                )
                            },
                            dismissButton = {
                                TextButton(onClick = dismiss) {
                                    Text(text = stringResource(MR.strings.action_cancel))
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        dismiss()
                                        securityPreferences.encryptDatabase().set(true)
                                    },
                                ) {
                                    Text(text = stringResource(MR.strings.action_ok))
                                }
                            },
                        )
                    }
                    Preference.PreferenceItem.SwitchPreference(
                        title = stringResource(SYMR.strings.encrypt_database),
                        preference = securityPreferences.encryptDatabase(),
                        subtitle = stringResource(SYMR.strings.encrypt_database_subtitle),
                        onValueChanged = {
                            if (it) {
                                enableEncryptDatabase = true
                                false
                            } else {
                                true
                            }
                        },
                    )
                },
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.open_debug_menu),
                    subtitle = remember {
                        HtmlCompat.fromHtml(
                            context.stringResource(SYMR.strings.open_debug_menu_summary),
                            HtmlCompat.FROM_HTML_MODE_COMPACT,
                        ).toAnnotatedString()
                    },
                    onClick = { navigator.push(SettingsDebugScreen()) },
                ),
            ),
        )
    }

    /**
     * Tests the configured FlareSolverr endpoint and updates the stored user agent when a valid solution is returned.
     *
     * Attempts a request against the FlareSolverr API at the URL in [flareSolverrUrlPref]; if the service returns a successful
     * challenge solution, stores the returned user agent into [userAgentPref]. Shows user-facing toasts for success, validation
     * failures, HTTP errors, and network exceptions.
     *
     * @param flareSolverrUrlPref Preference that contains the FlareSolverr API base URL (expected to end with `/v1`).
     * @param userAgentPref Preference to be updated with the user agent returned by FlareSolverr on success.
     * @param context Android context used for displaying toasts.
     */
    private suspend fun testFlareSolverrAndUpdateUserAgent(
        flareSolverrUrlPref: BasePreference<String>,
        userAgentPref: BasePreference<String>,
        context: android.content.Context,
    ) {
        val jsonMediaType = "application/json".toMediaType()

        try {
            withContext(Dispatchers.IO) {
                var flareSolverUrl = flareSolverrUrlPref.get().trim()

                if (flareSolverUrl.isBlank()) {
                    withContext(Dispatchers.Main) {
                        context.toast(SYMR.strings.flare_solver_url_not_configured)
                    }
                    return@withContext
                }

                // Ensure URL ends with /v1 (FlareSolverr API endpoint)
                if (!flareSolverUrl.endsWith("/v1")) {
                    flareSolverUrl = flareSolverUrl.trimEnd('/') + "/v1"
                    logcat(LogPriority.DEBUG, tag = "FlareSolverr") {
                        "Appended /v1 to URL: $flareSolverUrl"
                    }
                }

                val requestBody = Json.encodeToString(
                    FlareSolverrInterceptor.CFClearance.FlareSolverRequest(
                        cmd = "request.get",
                        url = "http://www.google.com/",
                        maxTimeout = 60000,
                    ),
                ).toRequestBody(jsonMediaType)

                logcat(LogPriority.DEBUG, tag = "FlareSolverr") {
                    "Testing FlareSolverr at: $flareSolverUrl"
                }

                val request = POST(
                    url = flareSolverUrl,
                    headers = Headers.Builder()
                        .add("Content-Type", "application/json")
                        .build(),
                    body = requestBody,
                )

                // Create a custom client with extended timeouts for FlareSolverr
                // FlareSolverr can take up to 60 seconds to solve challenges
                val customClient = networkHelper.client.newBuilder()
                    .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                customClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error details"
                        logcat(LogPriority.ERROR, tag = "FlareSolverr") {
                            "HTTP ${response.code}: $errorBody"
                        }
                        withContext(Dispatchers.Main) {
                            val errorMsg = when (response.code) {
                                405 -> context.stringResource(SYMR.strings.flare_solver_http_405)
                                else -> context.stringResource(SYMR.strings.flare_solver_http_error, response.code)
                            }
                            context.toast(errorMsg)
                        }
                        return@withContext
                    }

                    val flareSolverResponse = with(json) {
                        response.parseAs<FlareSolverrInterceptor.CFClearance.FlareSolverResponse>()
                    }

                    logcat(LogPriority.DEBUG, tag = "FlareSolverr") {
                        "FlareSolverr response: status=${flareSolverResponse.status}, solution.status=${flareSolverResponse.solution.status}"
                    }

                    if (flareSolverResponse.solution.status in 200..299) {
                        // Set the user agent to the one provided by FlareSolverr
                        userAgentPref.set(flareSolverResponse.solution.userAgent)

                        withContext(Dispatchers.Main) {
                            context.toast(SYMR.strings.flare_solver_user_agent_update_success)
                        }
                    } else {
                        logcat(LogPriority.ERROR, tag = "FlareSolverr") {
                            "Solution failed with status: ${flareSolverResponse.solution.status}, message: ${flareSolverResponse.message}"
                        }
                        withContext(Dispatchers.Main) {
                            context.toast(context.stringResource(SYMR.strings.flare_solver_failed, flareSolverResponse.message))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, tag = "FlareSolverr", throwable = e) {
                "Failed to resolve with FlareSolverr: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                val errorMsg = when {
                    e is java.net.UnknownHostException -> {
                        context.stringResource(SYMR.strings.flare_solver_dns_error)
                    }

                    e.message?.contains("timeout", ignoreCase = true) == true -> {
                        context.stringResource(SYMR.strings.flare_solver_timeout_error)
                    }

                    else -> context.stringResource(SYMR.strings.flare_solver_unknown_error, e.message ?: "Unknown error")
                }
                context.toast(errorMsg)
            }
        }
    }

    // SY <--
}
