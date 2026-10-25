package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.StubSource

interface SourceManager {

    val sources: Flow<List<Source>>

    suspend fun get(sourceKey: Long): Source?

    suspend fun getOrStub(sourceKey: Long): Source

    suspend fun getAll(): List<Source>

    suspend fun getOnlineSources(): List<HttpSource>

    // SY -->

    suspend fun getVisibleOnlineSources(): List<HttpSource>

    suspend fun getVisibleSources(): List<Source>
    // SY <--

    suspend fun getStubSources(): List<StubSource>
}
