package exh.metadata.metadata.base

import com.pushtorefresh.storio.operations.PreparedOperation
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import rx.Completable
import rx.Single
import kotlin.reflect.KClass

@Serializable
data class FlatMetadata(
    val metadata: SearchMetadata,
    val tags: List<SearchTag>,
    val titles: List<SearchTitle>
) {
    inline fun <reified T : RaisedSearchMetadata> raise(): T = raise(T::class)

    @OptIn(InternalSerializationApi::class)
    fun <T : RaisedSearchMetadata> raise(clazz: KClass<T>): T =
        RaisedSearchMetadata.raiseFlattenJson
            .decodeFromString(clazz.serializer(), metadata.extra).apply {
                fillBaseFields(this@FlatMetadata)
            }
}

fun DatabaseHelper.getFlatMetadataForManga(mangaId: Long): PreparedOperation<FlatMetadata?> {
    // We have to use fromCallable because StorIO messes up the thread scheduling if we use their rx functions
    val single = Single.fromCallable {
        val meta = getSearchMetadataForManga(mangaId).executeAsBlocking()
        if (meta != null) {
            val tags = getSearchTagsForManga(mangaId).executeAsBlocking()
            val titles = getSearchTitlesForManga(mangaId).executeAsBlocking()

            FlatMetadata(meta, tags, titles)
        } else null
    }

    return preparedOperationFromSingle(single)
}

private fun <T> preparedOperationFromSingle(single: Single<T>): PreparedOperation<T> {
    return object : PreparedOperation<T> {
        /**
         * Creates [rx.Observable] that emits result of Operation.
         *
         *
         * Observable may be "Hot" or "Cold", please read documentation of the concrete implementation.
         *
         * @return observable result of operation with only one [rx.Observer.onNext] call.
         */
        override fun createObservable() = single.toObservable()

        /**
         * Executes operation synchronously in current thread.
         *
         *
         * Notice: Blocking I/O operation should not be executed on the Main Thread,
         * it can cause ANR (Activity Not Responding dialog), block the UI and drop animations frames.
         * So please, execute blocking I/O operation only from background thread.
         * See [WorkerThread].
         *
         * @return nullable result of operation.
         */
        override fun executeAsBlocking() = single.toBlocking().value()

        /**
         * Creates [rx.Observable] that emits result of Operation.
         *
         *
         * Observable may be "Hot" (usually "Warm") or "Cold", please read documentation of the concrete implementation.
         *
         * @return observable result of operation with only one [rx.Observer.onNext] call.
         */
        override fun asRxObservable() = single.toObservable()

        /**
         * Creates [rx.Single] that emits result of Operation lazily when somebody subscribes to it.
         *
         *
         *
         * @return single result of operation.
         */
        override fun asRxSingle() = single
    }
}

fun DatabaseHelper.insertFlatMetadata(flatMetadata: FlatMetadata): Completable = Completable.fromCallable {
    require(flatMetadata.metadata.mangaId != -1L)

    inTransaction {
        insertSearchMetadata(flatMetadata.metadata).executeAsBlocking()
        setSearchTagsForManga(flatMetadata.metadata.mangaId, flatMetadata.tags)
        setSearchTitlesForManga(flatMetadata.metadata.mangaId, flatMetadata.titles)
    }
}
