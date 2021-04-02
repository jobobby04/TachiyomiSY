package exh.tachidesk

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import exh.log.xLogD
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object TachideskBackupHandler {
    fun sendBackup(uniFile: UniFile) {
        GlobalScope.launch(CoroutineExceptionHandler { _, _ -> }) {
            val client = Injekt.get<NetworkHelper>().client
            try {
                client.newCall(
                    POST(
                        Injekt.get<PreferencesHelper>().tachideskUrl()
                            .get() + "/api/v1/backup/legacy/import",
                        body = uniFile.openInputStream().bufferedReader().use { it.readText() }
                            .toRequestBody(jsonMime)
                    )
                ).await().close()
            } catch (e: Exception) {
                xLogD("Error sending backup", e)
            }
        }
    }

    suspend fun getBackup(filePath: String): File? {
        val client = Injekt.get<NetworkHelper>().client
        val response = client.newCall(
            GET(
                Injekt.get<PreferencesHelper>().tachideskUrl()
                    .get() + "/api/v1/backup/legacy/export"
            )
        ).await()

        return response.body?.byteStream()?.buffered()?.use { input ->
            File(filePath).also {
                it.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
