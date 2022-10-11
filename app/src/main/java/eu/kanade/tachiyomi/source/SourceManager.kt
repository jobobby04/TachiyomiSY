package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.source.model.SourceData
import eu.kanade.domain.source.repository.SourceDataRepository
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HBrowse
import eu.kanade.tachiyomi.source.online.english.Pururin
import eu.kanade.tachiyomi.source.online.english.Tsumino
import exh.log.xLogD
import exh.source.BlacklistedSources
import exh.source.DelegatedHttpSource
import exh.source.EH_SOURCE_ID
import exh.source.EIGHTMUSES_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.EnhancedHttpSource
import exh.source.HBROWSE_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import exh.source.PURURIN_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.source.handleSourceLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.reflect.KClass

class SourceManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val sourceRepository: SourceDataRepository,
) {
    private val downloadManager: DownloadManager by injectLazy()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private var sourcesMap = emptyMap<Long, Source>()
        set(value) {
            field = value
            sourcesMapFlow.value = field
        }

    private val sourcesMapFlow = MutableStateFlow(sourcesMap)

    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map { it.values.filterIsInstance<CatalogueSource>() }
    val onlineSources: Flow<List<HttpSource>> = catalogueSources.map { sources -> sources.filterIsInstance<HttpSource>() }

    // SY -->
    private val preferences: UnsortedPreferences by injectLazy()
    // SY <--

    init {
        scope.launch {
            extensionManager.getInstalledExtensionsFlow()
                // SY -->
                .combine(preferences.enableExhentai().changes()) { extensions, enableExhentai ->
                    extensions to enableExhentai
                }
                // SY <--
                .collectLatest { (extensions, enableExhentai) ->
                    val mutableMap = mutableMapOf<Long, Source>(LocalSource.ID to LocalSource(context)).apply {
                        // SY -->
                        put(EH_SOURCE_ID, EHentai(EH_SOURCE_ID, false, context))
                        if (enableExhentai) {
                            put(EXH_SOURCE_ID, EHentai(EXH_SOURCE_ID, true, context))
                        }
                        put(MERGED_SOURCE_ID, MergedSource())
                        // SY <--
                    }
                    extensions.forEach { extension ->
                        extension.sources.mapNotNull { it.toInternalSource() }.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(it.toSourceData())
                        }
                    }
                    sourcesMap = mutableMap
                }
        }

        scope.launch {
            sourceRepository.subscribeAll()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = StubSource(it)
                    }
                }
        }
    }

    private fun Source.toInternalSource(): Source? {
        // EXH -->
        val sourceQName = this::class.qualifiedName
        val factories = DELEGATED_SOURCES.entries.filter { it.value.factory }.map { it.value.originalSourceQualifiedClassName }
        val delegate = if (sourceQName != null) {
            val matched = factories.find { sourceQName.startsWith(it) }
            if (matched != null) {
                DELEGATED_SOURCES[matched]
            } else {
                DELEGATED_SOURCES[sourceQName]
            }
        } else {
            null
        }
        val newSource = if (this is HttpSource && delegate != null) {
            xLogD("Delegating source: %s -> %s!", sourceQName, delegate.newSourceClass.qualifiedName)
            val enhancedSource = EnhancedHttpSource(
                this,
                delegate.newSourceClass.constructors.find { it.parameters.size == 2 }!!.call(this, context),
            )

            currentDelegatedSources[enhancedSource.originalSource.id] = DelegatedSource(
                enhancedSource.originalSource.name,
                enhancedSource.originalSource.id,
                enhancedSource.originalSource::class.qualifiedName ?: delegate.originalSourceQualifiedClassName,
                (enhancedSource.enhancedSource as DelegatedHttpSource)::class,
                delegate.factory,
            )
            enhancedSource
        } else {
            this
        }

        return if (id in BlacklistedSources.BLACKLISTED_EXT_SOURCES) {
            xLogD("Removing blacklisted source: (id: %s, name: %s, lang: %s)!", id, name, (this as? CatalogueSource)?.lang)
            null
        } else {
            newSource
        }
        // EXH <--
    }

    fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    // SY -->
    fun getVisibleOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>().filter {
        it.id !in BlacklistedSources.HIDDEN_SOURCES
    }

    fun getVisibleCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>().filter {
        it.id !in BlacklistedSources.HIDDEN_SOURCES
    }

    fun getDelegatedCatalogueSources() = sourcesMap.values.filterIsInstance<EnhancedHttpSource>().mapNotNull { enhancedHttpSource ->
        enhancedHttpSource.enhancedSource as? DelegatedHttpSource
    }
    // SY <--

    private fun registerStubSource(sourceData: SourceData) {
        scope.launch {
            val (id, lang, name) = sourceData
            val dbSourceData = sourceRepository.getSourceData(id)
            if (dbSourceData == sourceData) return@launch
            sourceRepository.upsertSourceData(id, lang, name)
            if (dbSourceData != null) {
                downloadManager.renameSource(StubSource(dbSourceData), StubSource(sourceData))
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubSource {
        sourceRepository.getSourceData(id)?.let {
            return StubSource(it)
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return StubSource(it)
        }
        return StubSource(SourceData(id, "", ""))
    }

    @Suppress("OverridingDeprecatedMember")
    open inner class StubSource(val sourceData: SourceData) : Source {

        override val id: Long = sourceData.id

        override val name: String = sourceData.name.ifBlank { id.toString() }

        override val lang: String = sourceData.lang

        override suspend fun getMangaDetails(manga: SManga): SManga {
            throw getSourceNotInstalledException()
        }

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            throw getSourceNotInstalledException()
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> {
            throw getSourceNotInstalledException()
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            return if (sourceData.isMissingInfo.not()) "$name (${lang.uppercase()})" else id.toString()
        }

        fun getSourceNotInstalledException(): SourceNotInstalledException {
            return SourceNotInstalledException(toString())
        }
    }

    inner class SourceNotInstalledException(val sourceString: String) :
        Exception(context.getString(R.string.source_not_installed, sourceString))

    // SY -->
    companion object {
        private const val fillInSourceId = Long.MAX_VALUE
        val DELEGATED_SOURCES = listOf(
            DelegatedSource(
                "Pururin",
                PURURIN_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.pururin.Pururin",
                Pururin::class,
            ),
            DelegatedSource(
                "Tsumino",
                TSUMINO_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.tsumino.Tsumino",
                Tsumino::class,
            ),
            DelegatedSource(
                "MangaDex",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.mangadex",
                MangaDex::class,
                true,
            ),
            DelegatedSource(
                "HBrowse",
                HBROWSE_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.hbrowse.HBrowse",
                HBrowse::class,
            ),
            DelegatedSource(
                "8Muses",
                EIGHTMUSES_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.eightmuses.EightMuses",
                EightMuses::class,
            ),
            DelegatedSource(
                "NHentai",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.nhentai.NHentai",
                NHentai::class,
                true,
            ),
        ).associateBy { it.originalSourceQualifiedClassName }

        val currentDelegatedSources: MutableMap<Long, DelegatedSource> = ListenMutableMap(mutableMapOf(), ::handleSourceLibrary)

        data class DelegatedSource(
            val sourceName: String,
            val sourceId: Long,
            val originalSourceQualifiedClassName: String,
            val newSourceClass: KClass<out DelegatedHttpSource>,
            val factory: Boolean = false,
        )
    }

    private class ListenMutableMap<K, V>(
        private val internalMap: MutableMap<K, V>,
        private val listener: () -> Unit,
    ) : MutableMap<K, V> by internalMap {
        override fun clear() {
            val clearResult = internalMap.clear()
            listener()
            return clearResult
        }

        override fun put(key: K, value: V): V? {
            val putResult = internalMap.put(key, value)
            if (putResult == null) {
                listener()
            }
            return putResult
        }

        override fun putAll(from: Map<out K, V>) {
            internalMap.putAll(from)
            listener()
        }

        override fun remove(key: K): V? {
            val removeResult = internalMap.remove(key)
            if (removeResult != null) {
                listener()
            }
            return removeResult
        }
    }

    // SY <--
}
