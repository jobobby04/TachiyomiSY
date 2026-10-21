package exh.search

import exh.util.plusAssign

class Text : QueryComponent() {
    val components = mutableListOf<TextComponent>()

    private var query: String? = null
    private var rawText: String? = null

    fun asQuery(): String {
        if (query == null) {
            query = rBaseBuilder().toString()
        }
        return query!!
    }

    fun rBaseBuilder(): StringBuilder {
        val builder = StringBuilder()
        for (component in components) {
            when (component) {
                is StringTextComponent -> builder += SearchEngine.escapeLike(component.value)
                is SingleWildcard -> builder += "_"
                is MultiWildcard -> builder += "%"
            }
        }
        return builder
    }

    fun rawTextOnly() = if (rawText != null) {
        rawText!!
    } else {
        rawText = components
            .joinToString(separator = "", transform = { it.rawText })
        rawText!!
    }
}
