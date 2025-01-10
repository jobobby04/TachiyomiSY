package tachiyomi.domain.watcher.repository

import tachiyomi.domain.watcher.model.ExternalWatcherRequest

interface ExternalWatcherRepository {

    suspend fun getExternalWatcher(mangaId: Long, fcmToken: String): Boolean?

    suspend fun addToExternalWatcher(externalWatcherRequest: ExternalWatcherRequest): Boolean

    suspend fun removeFromExternalWatcher(externalWatcherRequest: ExternalWatcherRequest): Boolean

    suspend fun disableExternalWatcher(fcmToken: String): Boolean

    suspend fun enableExternalWatcher(fcmToken: String, interval: Long): Boolean
}
