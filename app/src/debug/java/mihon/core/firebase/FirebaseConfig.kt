package mihon.core.firebase

import android.content.Context

/**
 * SY-specific file
 */
object FirebaseConfig {
    fun init(context: Context) = Unit

    fun setAnalyticsEnabled(enabled: Boolean) = Unit

    fun setCrashlyticsEnabled(enabled: Boolean) = Unit

    fun recordException(throwable: Throwable) = Unit
}
