package eu.kanade.core.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TachiyomiMessagingService : FirebaseMessagingService() {

    companion object {
        const val KEY_SINGLE_MANGA_UPDATE = "KEY_SINGLE_MANGA_UPDATE"
    }

    private val basePreferences: BasePreferences = Injekt.get()

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        basePreferences.fcmToken().set(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val key = message.data["key"]
        when (key) {
            KEY_SINGLE_MANGA_UPDATE -> handleSingleMangaUpdate(message.data)
            else -> Unit
        }
    }

    private fun handleSingleMangaUpdate(
        data: Map<String, String>
    ) {
        scope.launch {
            val mangaId = data["id"]?.toLong() ?: return@launch
            LibraryUpdateJob.startNow(
                context = this@TachiyomiMessagingService,
                singleMangaUpdateId = mangaId
            )
        }
    }

}
