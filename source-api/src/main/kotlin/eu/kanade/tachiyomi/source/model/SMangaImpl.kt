@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mihon.core.common.extensions.EMPTY
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class SMangaImpl : SManga {

    override lateinit var url: String

    // SY -->
    override var title: String = ""
    // SY <--

    override var thumbnail_url: String? = null

    override var artist: String? = null

    override var author: String? = null

    override var status: Int = 0

    override var description: String? = null

    override var genre: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    // SY -->
    // JsonObject is not java.io.Serializable; writeObject/readObject below carry it as a string
    // so screens holding SManga in their saved state can be restored
    @Transient
    // SY <--
    override var memo: JsonObject = JsonObject.EMPTY

    // SY -->
    private fun writeObject(out: ObjectOutputStream) {
        out.defaultWriteObject()
        out.writeObject(memo.toString())
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        memo = Json.parseToJsonElement(input.readObject() as String).jsonObject
    }

    override val originalTitle: String
        get() = title
    override val originalAuthor: String?
        get() = author
    override val originalArtist: String?
        get() = artist
    override val originalThumbnailUrl: String?
        get() = thumbnail_url
    override val originalDescription: String?
        get() = description
    override val originalGenre: String?
        get() = genre
    override val originalStatus: Int
        get() = status
    // SY <--
}
