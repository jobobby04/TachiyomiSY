package eu.kanade.tachiyomi.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only hook so automated tests can trigger a sync without UI:
 * `adb shell am broadcast -a <appId>.TRIGGER_SYNC -p <appId>`
 */
class SyncTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncDataJob.startNow(context, manual = true)
    }
}
