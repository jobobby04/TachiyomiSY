package exh.util

import android.net.Uri

/**
 * SY-specific file
 *
 * Uri filter
 */
interface UriFilter {
    fun addToUri(builder: Uri.Builder)
}
