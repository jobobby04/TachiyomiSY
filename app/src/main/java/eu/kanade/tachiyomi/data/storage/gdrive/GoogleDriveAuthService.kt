package eu.kanade.tachiyomi.data.storage.gdrive

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.sy.SYMR

/**
 * Keeps the process alive while the user authenticates with Google in the browser.
 *
 * Without it the backgrounded app can be frozen before the redirect comes back,
 * which drops the loopback listener waiting for the authorization code.
 */
class GoogleDriveAuthService : Service() {

    override fun onCreate() {
        super.onCreate()
        try {
            val notification = notificationBuilder(Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_tachi)
                setAutoCancel(true)
                setOngoing(true)
                setShowWhen(false)
                setContentTitle(stringResource(SYMR.strings.pref_gdrive_sign_in))
                setContentText(stringResource(SYMR.strings.gdrive_sign_in_started))
            }.build()
            startForeground(Notifications.ID_GDRIVE_AUTH, notification)
        } catch (e: Exception) {
            // Never leave the service running without a foreground notification:
            // the system kills the process for it.
            logcat(LogPriority.WARN, e) { "Could not start Drive auth notification" }
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GoogleDriveAuthService::class.java),
                )
            }.onFailure {
                context.logcat(LogPriority.WARN, it) { "Could not start Drive auth service" }
            }
        }

        /**
         * Uses stopService rather than a stop-action Intent: starting a service from
         * the background is blocked on Android 8+, which would strand the notification.
         */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, GoogleDriveAuthService::class.java))
            }.onFailure {
                context.logcat(LogPriority.WARN, it) { "Could not stop Drive auth service" }
            }
        }
    }
}
