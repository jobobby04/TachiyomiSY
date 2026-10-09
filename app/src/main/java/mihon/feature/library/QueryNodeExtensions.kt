package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.LibraryItem
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.domain.library.model.search.AndNode
import mihon.domain.library.model.search.ComparisonField
import mihon.domain.library.model.search.ComparisonQueryNode
import mihon.domain.library.model.search.EmptyQueryNode
import mihon.domain.library.model.search.FieldQueryNode
import mihon.domain.library.model.search.GeneralQueryNode
import mihon.domain.library.model.search.MangaField
import mihon.domain.library.model.search.NamespaceQueryNode
import mihon.domain.library.model.search.NotNode
import mihon.domain.library.model.search.OrNode
import mihon.domain.library.model.search.QueryNode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource
import kotlin.math.abs
import kotlin.time.Instant

fun QueryNode.matches(item: LibraryItem, /* SY --> */ extras: LibrarySearchExtras /* SY <-- */): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item, /* SY --> */ extras /* SY <-- */) }
        is OrNode -> children.any { it.matches(item, /* SY --> */ extras /* SY <-- */) }
        is NotNode -> !child.matches(item, /* SY --> */ extras /* SY <-- */)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item, /* SY --> */ extras /* SY <-- */)
        is FieldQueryNode -> matches(item, /* SY --> */ extras /* SY <-- */)
        is ComparisonQueryNode -> matches(item)
        // SY -->
        is NamespaceQueryNode -> matches(item, extras)
        // SY <--
    }
}

private fun GeneralQueryNode.matches(item: LibraryItem, /* SY --> */ extras: LibrarySearchExtras /* SY <-- */): Boolean {
    val manga = item.libraryManga.manga

    // Use when so each added field has to be handled explicitly
    val match = MangaField.entries.any { field ->
        if (field.fieldOnly) return@any false

        when (field) {
            MangaField.TITLE -> manga.title.contains(value, ignoreCase = true)
            MangaField.AUTHOR -> manga.author?.contains(value, ignoreCase = true) ?: false
            MangaField.ARTIST -> manga.artist?.contains(value, ignoreCase = true) ?: false
            MangaField.DESCRIPTION -> manga.description?.contains(value, ignoreCase = true) ?: false
            MangaField.GENRE -> manga.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            MangaField.SOURCE -> {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }
            MangaField.NOTES -> manga.notes.contains(value, ignoreCase = true)

            // field-only queries; unreachable; added here to make `when` exhaustive
            MangaField.LANGUAGE, MangaField.SOURCE_ID -> error("How did we get here?")
        }
    } /* SY --> */ || extras.matchesGeneral(item, value) /* SY <-- */
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: LibraryItem, /* SY --> */ extras: LibrarySearchExtras /* SY <-- */): Boolean {
    val manga = item.libraryManga.manga

    val match = when (field) {
        MangaField.GENRE -> {
            if (value.isEmpty()) {
                manga.genre.isNullOrEmpty()
            } else {
                manga.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            }
        }

        MangaField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }
        }

        MangaField.SOURCE_ID -> {
            value.toLongOrNull()?.let { it == manga.source } ?: false
        }

        else -> {
            val text = when (field) {
                MangaField.TITLE -> manga.title
                MangaField.AUTHOR -> manga.author
                MangaField.ARTIST -> manga.artist
                MangaField.DESCRIPTION -> manga.description
                MangaField.NOTES -> manga.notes
                MangaField.LANGUAGE -> item.sourceLanguage

                // unreachable; added here to make `when` exhaustive
                MangaField.GENRE, MangaField.SOURCE, MangaField.SOURCE_ID -> error("How did we get here?")
            }

            if (value.isEmpty()) {
                text.isNullOrEmpty()
            } else {
                text?.contains(value, ignoreCase = true) ?: false
            }
        }
    } /* SY --> */ || extras.matchesField(field, value) /* SY <-- */

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.libraryManga.manga

    fun compareDates(date: Instant, value: String): Boolean? {
        val inputDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val mangaDate = date.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return queryComparator.apply(mangaDate, inputDate)
    }

    val match = when (field) {
        ComparisonField.ID -> value.toLongOrNull()?.let { queryComparator.apply(manga.id, it) }

        // SY -->
        ComparisonField.DATE_ADDED -> manga.favoriteAt?.takeIf { it != Manga.UNKNOWN_FAVORITE_AT }
            ?.let { compareDates(it, value) }
        // SY <--

        ComparisonField.FETCH_INTERVAL -> value.toIntOrNull()
            ?.let { queryComparator.apply(abs(manga.fetchInterval), it) }

        ComparisonField.NEXT_UPDATE -> manga.nextUpdate?.let { compareDates(it, value) }

        ComparisonField.UNREAD -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.unreadCount, it)
            }
        }

        ComparisonField.READ -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.readCount, it)
            }
        }

        ComparisonField.TOTAL -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.totalChapters, it)
            }
        }
    } ?: false

    return if (negated) !match else match
}

// SY -->

/**
 * Data outside of [LibraryItem] that SY searches through: metadata tags and titles, and tracker names and statuses.
 */
class LibrarySearchExtras(
    val searchTags: List<SearchTag> = emptyList(),
    val searchTitles: List<SearchTitle> = emptyList(),
    val trackMatches: (String) -> Boolean = { false },
) {
    fun matchesGeneral(item: LibraryItem, value: String): Boolean {
        val source = item.libraryManga.manga.source
        return (source != LocalSource.ID && source.toString() == value) ||
            searchTags.any { it.name.contains(value, ignoreCase = true) } ||
            searchTitles.any { it.title.contains(value, ignoreCase = true) } ||
            trackMatches(value)
    }

    /**
     * Metadata tags use the same names for some namespaces, e.g. `artist:` and `language:`.
     */
    fun matchesField(field: MangaField, value: String): Boolean {
        if (value.isEmpty()) return false
        return searchTags.any { tag ->
            field.aliases.any { tag.namespace.equals(it, ignoreCase = true) } &&
                tag.name.contains(value, ignoreCase = true)
        } ||
            (field == MangaField.TITLE && searchTitles.any { it.title.contains(value, ignoreCase = true) })
    }
}

private val namespaceAliases = mapOf(
    "a" to "artist",
    "c" to "character",
    "char" to "character",
    "f" to "female",
    "g" to "group",
    "creator" to "group",
    "circle" to "group",
    "l" to "language",
    "lang" to "language",
    "m" to "male",
    "p" to "parody",
    "series" to "parody",
    "r" to "reclass",
)

private fun NamespaceQueryNode.matches(item: LibraryItem, extras: LibrarySearchExtras): Boolean {
    val resolvedNamespace = namespaceAliases[namespace.lowercase()] ?: namespace
    val match = extras.searchTags.any { tag ->
        tag.namespace.equals(resolvedNamespace, ignoreCase = true) &&
            when {
                value.isEmpty() -> true
                exact -> tag.name.equals(value, ignoreCase = true)
                else -> tag.name.contains(value, ignoreCase = true)
            }
    } ||
        // An exact tag on a key that's also a field, e.g. `artist:name$`, still searches that field
        (
            exact &&
                MangaField.fromString(namespace)
                    ?.let { FieldQueryNode(it, value, negated = false).matches(item, extras) } == true
            ) ||
        GeneralQueryNode("$namespace:$value", negated = false).matches(item, extras)
    return if (negated) !match else match
}
// SY <--
