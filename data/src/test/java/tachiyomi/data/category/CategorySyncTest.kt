package tachiyomi.data.category

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.InMemoryDatabase
import tachiyomi.domain.category.model.SyncedCategory

class CategorySyncTest {

    private val db = InMemoryDatabase()
    private val repository = CategoryRepositoryImpl(db.database)

    @BeforeEach
    fun create() = runTest {
        db.create()
    }

    @AfterEach
    fun close() {
        db.close()
    }

    @Test
    fun `synced categories can swap positions`() = runTest {
        repository.applySynced(listOf(synced("A", order = 0, uid = 1), synced("B", order = 1, uid = 2)))

        repository.applySynced(listOf(synced("B", order = 0, uid = 2), synced("A", order = 1, uid = 1)))

        positions() shouldContainExactly listOf("B" to 0L, "A" to 1L)
    }

    @Test
    fun `a new synced category can take the position of an existing one`() = runTest {
        repository.applySynced(listOf(synced("A", order = 0, uid = 1)))

        repository.applySynced(listOf(synced("C", order = 0, uid = 3), synced("A", order = 1, uid = 1)))

        positions() shouldContainExactly listOf("C" to 0L, "A" to 1L)
    }

    @Test
    fun `a local category the server does not know moves after the synced ones`() = runTest {
        repository.applySynced(listOf(synced("Local", order = 0, uid = 9)))

        repository.applySynced(listOf(synced("A", order = 0, uid = 1)))

        positions() shouldContainExactly listOf("A" to 0L, "Local" to 1L)
    }

    @Test
    fun `synced categories keep the server versions`() = runTest {
        repository.applySynced(listOf(synced("A", order = 0, uid = 1, version = 3), synced("B", order = 1, uid = 2, version = 5)))

        repository.applySynced(listOf(synced("B", order = 0, uid = 2, version = 6), synced("A", order = 1, uid = 1, version = 3)))
        repository.resetIsSyncing()

        repository.getAll().filterNot { it.isSystemCategory }.map { it.name to it.version } shouldContainExactly
            listOf("B" to 6L, "A" to 3L)
    }

    private suspend fun positions() = repository.getAll().filterNot { it.isSystemCategory }.map { it.name to it.order }

    private fun synced(name: String, order: Long, uid: Long, version: Long = 1) = SyncedCategory(
        name = name,
        order = order,
        flags = 0,
        version = version,
        uid = uid,
        lastModifiedAt = 0,
    )
}
