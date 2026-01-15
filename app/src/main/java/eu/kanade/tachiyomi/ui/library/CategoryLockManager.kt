package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.core.security.SecurityPreferences
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

// SY -->
/**
 * Manages the session-based unlock state for locked categories.
 * Categories remain unlocked until the app process terminates or timeout occurs.
 */
object CategoryLockManager {
    private val securityPreferences: SecurityPreferences by injectLazy()

    // Thread-safe set of category IDs that are currently unlocked in this session
    private val unlockedCategories = ConcurrentHashMap.newKeySet<Long>()

    // Thread-safe map of category ID to unlock timestamp (in milliseconds)
    private val unlockTimestamps = ConcurrentHashMap<Long, Long>()

    /**
     * Determines whether the given category is unlocked for the current session, after enforcing lock timeouts.
     *
     * @return `true` if the category is unlocked in the current session, `false` otherwise.
     */
    fun isUnlocked(categoryId: Long): Boolean {
        checkTimeouts()
        return unlockedCategories.contains(categoryId)
    }

    /**
     * Marks the given category as unlocked for the current session and records the unlock timestamp.
     *
     * @param categoryId The ID of the category to unlock.
     */
    fun unlock(categoryId: Long) {
        unlockedCategories.add(categoryId)
        unlockTimestamps[categoryId] = System.currentTimeMillis()
    }

    /**
     * Locks the specified category for the current session by clearing its unlocked state.
     *
     * @param categoryId The ID of the category to lock.
     */
    fun lock(categoryId: Long) {
        unlockedCategories.remove(categoryId)
        unlockTimestamps.remove(categoryId)
    }

    /**
     * Clears all session unlock state for categories.
     *
     * Removes all unlocked category IDs and their associated unlock timestamps so all categories become locked for the session.
     */
    fun lockAll() {
        unlockedCategories.clear()
        unlockTimestamps.clear()
    }

    /**
     * Enforces category lock timeouts by re-locking categories whose unlock age exceeds the configured timeout.
     *
     * Reads the configured timeout in minutes from preferences:
     * - If the value is -1, clears all unlocked state (always require PIN).
     * - If the value is 0, leaves current unlocked state unchanged for the session.
     * - If positive, locks any categories whose unlock timestamp is older than the configured timeout.
     */
    private fun checkTimeouts() {
        val timeoutMinutes = securityPreferences.categoryLockTimeout().get()

        // -1 means always require PIN (immediately lock everything)
        if (timeoutMinutes == -1) {
            unlockedCategories.clear()
            unlockTimestamps.clear()
            return
        }

        // 0 means never re-lock during session
        if (timeoutMinutes == 0) return

        val timeoutMillis = timeoutMinutes.toLong() * 60 * 1000L
        val currentTime = System.currentTimeMillis()

        val categoriesToLock = unlockTimestamps.filter { (_, timestamp) ->
            currentTime - timestamp > timeoutMillis
        }.keys

        categoriesToLock.forEach { categoryId ->
            lock(categoryId)
        }
    }

    /**
     * Provides a snapshot of category IDs currently unlocked in this session.
     *
     * Invokes timeout enforcement before producing the snapshot.
     *
     * @return A set containing the unlocked category IDs at the time of the call.
     */
    fun getUnlockedCategories(): Set<Long> {
        checkTimeouts()
        return unlockedCategories.toSet()
    }
}
// SY <--
