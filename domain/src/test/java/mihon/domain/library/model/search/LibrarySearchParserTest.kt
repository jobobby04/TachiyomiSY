package mihon.domain.library.model.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * SY-specific file
 */
// SY -->
@Execution(ExecutionMode.CONCURRENT)
class LibrarySearchParserTest {

    @Test
    fun `id prefix is an id comparison`() {
        QueryNode.from("id:12") shouldBe ComparisonQueryNode(ComparisonField.ID, "12", Comparator.EQ, false)
    }

    @Test
    fun `unknown keys are namespaces`() {
        QueryNode.from("-female:\"big breasts\"") shouldBe NamespaceQueryNode("female", "big breasts", true)
    }

    @Test
    fun `known keys stay fields`() {
        QueryNode.from("artist:foo") shouldBe FieldQueryNode(MangaField.ARTIST, "foo", false)
    }

    @Test
    fun `a key without a value is a namespace`() {
        QueryNode.from("female: -artist:") shouldBe AndNode(
            listOf(
                NamespaceQueryNode("female", "", false),
                NamespaceQueryNode("artist", "", true),
            ),
        )
    }

    @Test
    fun `a trailing dollar is an exact tag`() {
        QueryNode.from("female:glasses$") shouldBe NamespaceQueryNode("female", "glasses", false, exact = true)
        QueryNode.from("artist:\"some name$\"") shouldBe NamespaceQueryNode("artist", "some name", false, exact = true)
        QueryNode.from("glasses$") shouldBe GeneralQueryNode("glasses", false)
    }

    @Test
    fun `quoted empty fields stay fields`() {
        QueryNode.from("artist:\"\"") shouldBe FieldQueryNode(MangaField.ARTIST, "", false)
    }

    @Test
    fun `namespaces combine with other terms`() {
        QueryNode.from("parody:touhou || title") shouldBe OrNode(
            listOf(
                NamespaceQueryNode("parody", "touhou", false),
                GeneralQueryNode("title", false),
            ),
        )
    }
}
// SY <--
