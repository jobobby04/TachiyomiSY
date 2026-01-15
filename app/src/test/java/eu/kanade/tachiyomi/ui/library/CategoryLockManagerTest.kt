package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory

class CategoryLockManagerTest {

    private lateinit var securityPreferences: SecurityPreferences

    @BeforeEach
    fun setup() {
        securityPreferences = mockk(relaxed = true)

        // Mock Injekt
        mockkObject(Injekt)
        val module = InjektModule {
            addSingletonFactory { securityPreferences }
        }
        InjektRegistrar.registerInjectModule(module)

        // Reset the manager state
        CategoryLockManager.lockAll()

        // Default timeout: never re-lock during session
        every { securityPreferences.categoryLockTimeout().get() } returns 0
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
        CategoryLockManager.lockAll()
    }

    @Test
    fun `newly created categories are locked by default`() {
        CategoryLockManager.isUnlocked(1L).shouldBeFalse()
        CategoryLockManager.isUnlocked(999L).shouldBeFalse()
    }

    @Test
    fun `unlock marks category as unlocked`() {
        CategoryLockManager.unlock(1L)

        CategoryLockManager.isUnlocked(1L).shouldBeTrue()
    }

    @Test
    fun `lock marks category as locked`() {
        CategoryLockManager.unlock(1L)
        CategoryLockManager.lock(1L)

        CategoryLockManager.isUnlocked(1L).shouldBeFalse()
    }

    @Test
    fun `lockAll locks all categories`() {
        CategoryLockManager.unlock(1L)
        CategoryLockManager.unlock(2L)
        CategoryLockManager.unlock(3L)

        CategoryLockManager.lockAll()

        CategoryLockManager.isUnlocked(1L).shouldBeFalse()
        CategoryLockManager.isUnlocked(2L).shouldBeFalse()
        CategoryLockManager.isUnlocked(3L).shouldBeFalse()
    }

    @Test
    fun `getUnlockedCategories returns all unlocked categories`() {
        CategoryLockManager.unlock(1L)
        CategoryLockManager.unlock(2L)
        CategoryLockManager.unlock(3L)

        val unlocked = CategoryLockManager.getUnlockedCategories()

        unlocked shouldHaveSize 3
        unlocked shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L)
    }

    @Test
    fun `getUnlockedCategories returns empty set when all locked`() {
        CategoryLockManager.lockAll()

        val unlocked = CategoryLockManager.getUnlockedCategories()

        unlocked.shouldBeEmpty()
    }

    @Test
    fun `unlocking same category multiple times is idempotent`() {
        CategoryLockManager.unlock(1L)
        CategoryLockManager.unlock(1L)
        CategoryLockManager.unlock(1L)

        CategoryLockManager.isUnlocked(1L).shouldBeTrue()
        CategoryLockManager.getUnlockedCategories() shouldHaveSize 1
    }

    @Test
    fun `locking already locked category is idempotent`() {
        CategoryLockManager.lock(1L)
        CategoryLockManager.lock(1L)

        CategoryLockManager.isUnlocked(1L).shouldBeFalse()
    }

    @Test
    fun `timeout of -1 always requires PIN`() {
        every { securityPreferences.categoryLockTimeout().get() } returns -1

        CategoryLockManager.unlock(1L)
        CategoryLockManager.unlock(2L)

        // Check if unlocked (triggers timeout check)
        CategoryLockManager.isUnlocked(1L).shouldBeFalse()
        CategoryLockManager.isUnlocked(2L).shouldBeFalse()
    }

    @Test
    fun `timeout of 0 never re-locks during session`() {
        every { securityPreferences.categoryLockTimeout().get() } returns 0

        CategoryLockManager.unlock(1L)

        // Should remain unlocked regardless of time
        CategoryLockManager.isUnlocked(1L).shouldBeTrue()
    }

    @Test
    fun `unlock and lock work independently for different categories`() {
        CategoryLockManager.unlock(1L)
        CategoryLockManager.unlock(2L)
        CategoryLockManager.lock(1L)

        CategoryLockManager.isUnlocked(1L).shouldBeFalse()
        CategoryLockManager.isUnlocked(2L).shouldBeTrue()
    }

    @Test
    fun `getUnlockedCategories excludes locked categories`() {
        CategoryLockManager.unlock(1L)
        CategoryLockManager.unlock(2L)
        CategoryLockManager.unlock(3L)
        CategoryLockManager.lock(2L)

        val unlocked = CategoryLockManager.getUnlockedCategories()

        unlocked shouldHaveSize 2
        unlocked shouldContain 1L
        unlocked shouldContain 3L
        unlocked shouldNotContain 2L
    }

    @Test
    fun `handles large category IDs correctly`() {
        val largeCategoryId = Long.MAX_VALUE

        CategoryLockManager.unlock(largeCategoryId)

        CategoryLockManager.isUnlocked(largeCategoryId).shouldBeTrue()
        CategoryLockManager.getUnlockedCategories() shouldContain largeCategoryId
    }

    @Test
    fun `handles negative category IDs correctly`() {
        val negativeCategoryId = -1L

        CategoryLockManager.unlock(negativeCategoryId)

        CategoryLockManager.isUnlocked(negativeCategoryId).shouldBeTrue()
        CategoryLockManager.getUnlockedCategories() shouldContain negativeCategoryId
    }
}
