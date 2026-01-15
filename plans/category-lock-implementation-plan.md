# Category-Specific Lock/Password Protection Implementation Plan

## Overview
Add the ability to lock specific categories with their own PIN. When accessing a locked category (by tapping tab or swiping), users must enter the correct PIN. Locked categories show a lock icon and display a "Private Categories" message until unlocked.

## Architecture

### Storage Strategy
Store category PINs in `SecurityPreferences` using encrypted StringSet format (`categoryId:encryptedPin`), following the existing `authenticatorTimeRanges()` pattern. This avoids database schema changes and keeps security data centralized.

### PIN Configuration
- Support flexible PIN length: 4-10 digits (default)
- Each category has its own unique PIN
- Users can set different PINs for different categories
- Show with lock icon on tabs by default (user-configurable)

### Session Management
Use an in-memory singleton (`CategoryLockManager`) to track unlocked categories for the session. Categories remain unlocked until app process terminates. Optionally support configurable timeout (user preference).

---

## Files to Create

### 1. `/core/common/src/main/kotlin/eu/kanade/tachiyomi/util/storage/CategoryLockCrypto.kt`
PIN encryption/verification utilities using Android KeyStore:
- `encryptPin(pin: String): String`
- `verifyPin(categoryId: Long, inputPin: String): Boolean`
- `setPinForCategory(categoryId: Long, pin: String)`
- `removePinForCategory(categoryId: Long)`
- `hasLock(categoryId: Long): Boolean`
- `getLockedCategoryIds(): Set<Long>`

### 2. `/app/src/main/java/eu/kanade/tachiyomi/ui/library/CategoryLockManager.kt`
Singleton for session-based unlock state:
- `isUnlocked(categoryId: Long): Boolean`
- `unlock(categoryId: Long)`
- `lock(categoryId: Long)`
- `lockAll()`

### 3. `/app/src/main/java/eu/kanade/presentation/library/components/CategoryPinDialog.kt`
Compose dialog for PIN entry:
- Numeric PIN pad with flexible length (4-10 digits, configurable per category)
- Visual dot feedback for entered digits
- Shake animation on wrong PIN
- Support for both unlock and set-PIN modes
- PIN length indicator/requirement display

### 4. `/app/src/main/java/eu/kanade/presentation/library/components/LockedCategoryOverlay.kt`
Overlay shown instead of locked category content:
- Simple placeholder with "Private Categories" message
- Lock icon
- "Tap to unlock" button

### 5. `/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsCategoryLockScreen.kt`
Settings screen to manage category locks:
- List of categories with lock toggle
- Set/change/remove PIN per category (each category can have unique PIN)
- PIN length configuration per category (4-10 digits)
- Lock timeout preference (until app closes, or configurable timeout)
- Show/hide locked categories preference (show with lock icon vs hide completely)

---

## Files to Modify

### 1. `/core/common/src/main/kotlin/eu/kanade/tachiyomi/core/security/SecurityPreferences.kt`
Add preferences:
```kotlin
fun categoryLockPins() = preferenceStore.getStringSet(
    Preference.privateKey("category_lock_pins"),
    emptySet()
)
fun categoryLockTimeout() = preferenceStore.getInt("category_lock_timeout", 0)
fun showLockedCategories() = preferenceStore.getBoolean("show_locked_categories", true)
```

### 2. `/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt`
- Add `Dialog.UnlockCategory(category: Category)` dialog type
- Add `lockedCategoryIds: Set<Long>` to State
- Add `requestCategoryAccess(category: Category): Boolean` method
- Add `unlockCategory(categoryId: Long, pin: String): Boolean` method

### 3. `/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt`
- Handle `Dialog.UnlockCategory` to show PIN dialog
- Pass locked state to `LibraryContent`

### 4. `/app/src/main/java/eu/kanade/presentation/library/components/LibraryContent.kt`
- Add `lockedCategoryIds` and `onRequestUnlock` parameters
- Intercept tab clicks to check lock status before navigating
- Pass lock state to `LibraryTabs` and `LibraryPager`

### 5. `/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt`
- Add `isLocked: (Category) -> Boolean` parameter
- Show lock icon on tabs for locked categories

### 6. `/app/src/main/java/eu/kanade/presentation/library/components/LibraryPager.kt`
- Add `isLocked: (Category) -> Boolean` and `onUnlockRequest: (Category) -> Unit` parameters
- Show `LockedCategoryOverlay` instead of content for locked pages
- Intercept swipe to locked categories

### 7. `/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreen.kt`
- Add link to Category Lock Settings screen

### 8. `/i18n-sy/src/commonMain/moko-resources/base/strings.xml`
Add string resources for category lock UI

---

## Implementation Order

1. **SecurityPreferences** - Add new preference methods
2. **CategoryLockCrypto** - PIN encryption/storage utilities
3. **CategoryLockManager** - Session unlock state singleton
4. **CategoryPinDialog** - PIN input UI component
5. **LockedCategoryOverlay** - Locked content placeholder
6. **LibraryScreenModel** - Lock state and dialog handling
7. **LibraryContent/LibraryTabs/LibraryPager** - UI integration
8. **LibraryTab** - Wire up dialog handling
9. **SettingsCategoryLockScreen** - Settings UI
10. **SettingsSecurityScreen** - Link to new settings
11. **String resources** - Localization

---

## Verification

1. Build the app: `./gradlew assembleDebug`
2. Test PIN setting: Settings > Security > Category Lock Settings > Select category > Set PIN
3. Test locking: Go to Library > Verify locked category shows lock icon
4. Test unlock: Tap locked category > Enter PIN > Verify content shows
5. Test wrong PIN: Enter wrong PIN > Verify error feedback
6. Test session persistence: Unlock category > Navigate away > Return > Should still be unlocked
7. Test app restart: Force close app > Reopen > Locked categories should require PIN again
