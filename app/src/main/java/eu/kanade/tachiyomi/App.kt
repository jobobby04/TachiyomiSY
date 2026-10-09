package eu.kanade.tachiyomi

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.crash.CrashActivity
import eu.kanade.tachiyomi.crash.GlobalExceptionHandler
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.ImageDecoder
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import eu.kanade.tachiyomi.data.coil.PagePreviewFetcher
import eu.kanade.tachiyomi.data.coil.PagePreviewKeyer
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.di.InjektKoinBridge
import eu.kanade.tachiyomi.di.importModule
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.notify
import exh.log.CrashlyticsPrinter
import exh.log.EHLogLevel
import exh.log.EnhancedFilePrinter
import exh.log.XLogLogcatLogger
import exh.log.xLogD
import exh.syDebugVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import logcat.LogcatLogger
import mihon.app.di.AppGraph
import mihon.app.di.injekt.MetroInteropModule
import mihon.core.firebase.FirebaseConfig
import mihon.core.metro.GraphProvider
import mihon.core.migration.Migration
import mihon.core.migration.Migrator
import org.conscrypt.Conscrypt
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.WidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Locale
import mihon.icons.materialsymbols.R as MaterialSymbolsR

class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory, GraphProvider<AppGraph> {

    override val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(context = this, isDebugBuild = isDebugBuildType)
    }

    @Inject private lateinit var preferenceStore: PreferenceStore

    @Inject private lateinit var basePreferences: BasePreferences

    @Inject private lateinit var privacyPreferences: PrivacyPreferences

    @Inject private lateinit var networkPreferences: NetworkPreferences

    @Inject private lateinit var uiPreferences: UiPreferences

    @Inject private lateinit var coverCache: CoverCache

    @Inject private lateinit var networkHelper: NetworkHelper

    @Inject private lateinit var sourceManager: SourceManager

    @Inject private lateinit var widgetManager: WidgetManager

    @Inject private lateinit var injektMetroInteropModule: MetroInteropModule

    @Inject private lateinit var migrations: Set<Migration>

    // SY -->
    @Inject private lateinit var syncPreferences: SyncPreferences

    @Inject private lateinit var storageManager: StorageManager
    // SY <--

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()
        try {
            FirebaseConfig.init(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Must run before the graph is built, since injecting dependencies initializes WebView and the
        // suffix can't be set once a provider exists in the process. Secondary processes die otherwise.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }

        graph.inject(this)
        setupInjekt()

        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        setupExhLogging() // EXH logging
        LogcatLogger.install()
        LogcatLogger.loggers += XLogLogcatLogger() // SY Redirect Logcat to XLog

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

        // Show notification to disable Incognito Mode when it's enabled
        basePreferences.incognitoMode.changes()
            .onEach { enabled ->
                if (enabled) {
                    disableIncognitoReceiver.register()
                    notify(
                        Notifications.ID_INCOGNITO_MODE,
                        Notifications.CHANNEL_INCOGNITO_MODE,
                    ) {
                        setContentTitle(stringResource(MR.strings.pref_incognito_mode))
                        setContentText(stringResource(MR.strings.notification_incognito_text))
                        setSmallIcon(MaterialSymbolsR.drawable.rounded_eyeglasses_2)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE).setPackage(BuildConfig.APPLICATION_ID),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                } else {
                    disableIncognitoReceiver.unregister()
                    cancelNotification(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(scope)

        privacyPreferences.analytics
            .changes()
            .onEach(FirebaseConfig::setAnalyticsEnabled)
            .launchIn(scope)

        privacyPreferences.crashlytics
            .changes()
            .onEach(FirebaseConfig::setCrashlyticsEnabled)
            .launchIn(scope)

        setAppCompatDelegateThemeMode(uiPreferences.themeMode.get())

        // Updates widget update
        widgetManager.init(scope)

        /*if (!LogcatLogger.isInstalled && networkPreferences.verboseLogging().get()) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        }*/

        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, Configuration.Builder().build())
        }
        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        if (syncPreferences.isSyncEnabled() && syncTriggerOpt.syncOnAppStart) {
            SyncDataJob.startNow(this@App)
        }

        initializeMigrator()
    }

    private fun setupInjekt() {
        Injekt.importModule(injektMetroInteropModule)
        // SY -->
        InjektKoinBridge.startKoin(this)
        // SY <--
    }

    private fun initializeMigrator() {
        // SY -->
        val preference = preferenceStore.getInt(Preference.appStateKey("eh_last_version_code"), 0)
        // SY <--
        logcat {
            "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE} with ${migrations.size} migration(s)"
        }
        Migrator.initialize(
            old = preference.get(),
            new = BuildConfig.VERSION_CODE,
            migrations = migrations.toList(),
            onMigrationComplete = {
                logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { networkHelper.client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Decoder.Factory
                add(ImageDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy, coverCache, sourceManager))
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy, coverCache, sourceManager))
                // SY -->
                add(PagePreviewFetcher.Factory(callFactoryLazy))
                // SY <--
                // Keyer
                add(MangaCoverKeyer(coverCache))
                add(MangaKeyer())
                // SY -->
                add(PagePreviewKeyer())
                // SY <--
            }

            memoryCache(
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build(),
            )

            crossfade((300 * this@App.animatorDurationScale).toInt())
            allowRgb565(DeviceUtil.isLowRamDevice(this@App))
            if (networkPreferences.verboseLogging.get()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStart(this)
        // SY -->
        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        if (syncPreferences.isSyncEnabled() && syncTriggerOpt.syncOnAppResume) {
            SyncDataJob.startNow(this@App)
        }
        // SY <--
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStopped(this)
    }

    override fun getPackageName(): String {
        try {
            // Override the value passed as X-Requested-With in WebView requests
            val stackTrace = Thread.currentThread().stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                trace.className.lowercase() in setOf("org.chromium.base.buildinfo", "org.chromium.base.apkinfo") &&
                    trace.methodName.lowercase() in setOf("getall", "getpackagename", "<init>")
            }

            if (isChromiumCall) return WebViewUtil.spoofedPackageName(applicationContext)
        } catch (_: Exception) {
        }

        return super.getPackageName()
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    // EXH
    private fun setupExhLogging() {
        EHLogLevel.init(this)

        val logLevel = when {
            EHLogLevel.shouldLog(EHLogLevel.EXTREME) -> LogLevel.ALL
            EHLogLevel.shouldLog(EHLogLevel.EXTRA) || BuildConfig.DEBUG -> LogLevel.DEBUG
            else -> LogLevel.WARN
        }

        val logConfig = LogConfiguration.Builder()
            .logLevel(logLevel)
            .disableStackTrace()
            .disableBorder()
            .build()

        val printers = mutableListOf<Printer>(AndroidPrinter())

        val logFolder = storageManager.getLogsDirectory()

        if (logFolder != null) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

            printers += EnhancedFilePrinter
                .Builder(logFolder) {
                    fileNameGenerator = object : DateFileNameGenerator() {
                        override fun generateFileName(logLevel: Int, timestamp: Long): String {
                            return super.generateFileName(
                                logLevel,
                                timestamp,
                            ) + "-${BuildConfig.BUILD_TYPE}.txt"
                        }
                    }
                    flattener { timeMillis, level, tag, message ->
                        "${dateFormat.format(timeMillis)} ${LogLevel.getShortLevelName(level)}/$tag: $message"
                    }
                    backupStrategy = NeverBackupStrategy()
                }
        }

        // Install Crashlytics in prod
        if (!BuildConfig.DEBUG) {
            printers += CrashlyticsPrinter(LogLevel.ERROR)
        }

        XLog.init(
            logConfig,
            *printers.toTypedArray(),
        )

        xLogD("Application booting...")
        xLogD(
            """
                App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE})
                Preview build: $syDebugVersion
                Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
                Android build ID: ${Build.DISPLAY}
                Device brand: ${Build.BRAND}
                Device manufacturer: ${Build.MANUFACTURER}
                Device name: ${Build.DEVICE}
                Device model: ${Build.MODEL}
                Device product name: ${Build.PRODUCT}
            """.trimIndent(),
        )
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode.set(false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }
}

private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
