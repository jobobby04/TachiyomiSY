package mihon.domain.library.model.search

class LibrarySearchParser(private val tokens: List<LibrarySearchLexer.Token>) {
    private var index = 0

    private fun peek(): LibrarySearchLexer.Token? = tokens.getOrNull(index)
    private fun advance(): LibrarySearchLexer.Token? = tokens.getOrNull(index++)

    fun parse(): QueryNode {
        if (tokens.isEmpty()) return AndNode(emptyList())
        return parseOr()
    }

    private fun parseOr(): QueryNode {
        val nodes = mutableListOf<QueryNode>()
        nodes.add(parseAnd())
        while (peek() is LibrarySearchLexer.Token.Or) {
            advance()
            nodes.add(parseAnd())
        }
        return if (nodes.size == 1) nodes.first() else OrNode(nodes)
    }

    private fun parseAnd(): QueryNode {
        val nodes = mutableListOf<QueryNode>()

        while (index < tokens.size && peek() !is LibrarySearchLexer.Token.Or &&
            peek() !is LibrarySearchLexer.Token.RParen
        ) {
            if (peek() is LibrarySearchLexer.Token.And) {
                advance()
            }
            nodes.add(parseTerm())
        }

        val filteredNodes = nodes.filter { it !is EmptyQueryNode }
        if (filteredNodes.isEmpty()) return EmptyQueryNode
        return if (filteredNodes.size == 1) filteredNodes.first() else AndNode(filteredNodes)
    }

    private fun parseTerm(): QueryNode {
        var negated = false

        while (peek() is LibrarySearchLexer.Token.Not) {
            advance()
            negated = !negated
        }

        if (peek() is LibrarySearchLexer.Token.LParen) {
            advance()

            val subTree = parseOr()

            if (peek() is LibrarySearchLexer.Token.RParen) {
                advance()
            }

            if (negated) {
                return NotNode(subTree)
            }
            return subTree
        }

        return when (val nextToken = advance()) {
            is LibrarySearchLexer.Token.General -> /* SY --> */ toSyQueryNode(nextToken, negated) ?: /* SY <-- */
                GeneralQueryNode(nextToken.value, negated)
            is LibrarySearchLexer.Token.Field -> {
                // SY -->
                toExactNamespaceQueryNode(nextToken, negated)?.let { return it }
                // SY <--
                MangaField.fromString(nextToken.field)?.let {
                    FieldQueryNode(it, nextToken.value, negated)
                } /* SY --> */ ?: toSyQueryNode(nextToken, negated) /* SY <-- */
            }
            is LibrarySearchLexer.Token.CompField -> {
                ComparisonField.fromString(nextToken.field)?.let {
                    ComparisonQueryNode(it, nextToken.value, Comparator.fromString(nextToken.comparator)!!, negated)
                } ?: GeneralQueryNode("${nextToken.field}${nextToken.comparator}${nextToken.value}", negated)
            }
            else -> EmptyQueryNode
        }
    }

    // SY -->

    /**
     * Keeps the `id:` prefix working and treats any other unknown key as a metadata tag namespace.
     */
    private fun toSyQueryNode(token: LibrarySearchLexer.Token.Field, negated: Boolean): QueryNode {
        return if (token.field.equals("id", ignoreCase = true)) {
            ComparisonQueryNode(ComparisonField.ID, token.value, Comparator.EQ, negated)
        } else {
            NamespaceQueryNode(token.field, token.value, negated)
        }
    }

    /**
     * `female:` matches any tag in the namespace, and a trailing `$` was SY's exact tag marker, which only ever
     * marked tags, so on text it's dropped.
     */
    private fun toSyQueryNode(token: LibrarySearchLexer.Token.General, negated: Boolean): QueryNode? {
        emptyNamespaceRegex.matchEntire(token.value)?.let {
            return NamespaceQueryNode(it.groupValues[1], "", negated)
        }
        if (token.value.length > 1 && token.value.endsWith('$')) {
            return GeneralQueryNode(token.value.dropLast(1), negated)
        }
        return null
    }

    /**
     * `female:glasses$` only matches tags named exactly "glasses", including on keys that are also fields.
     */
    private fun toExactNamespaceQueryNode(token: LibrarySearchLexer.Token.Field, negated: Boolean): QueryNode? {
        if (token.value.length <= 1 || !token.value.endsWith('$')) return null
        return NamespaceQueryNode(token.field, token.value.dropLast(1), negated, exact = true)
    }
    // SY <--
}

// SY -->
private val emptyNamespaceRegex = Regex("([a-zA-Z_][a-zA-Z0-9_]*):")
// SY <--
