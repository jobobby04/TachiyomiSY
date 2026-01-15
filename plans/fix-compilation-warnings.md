# Plan: Fix Kotlin Compilation Warnings

## Overview
Fix 29+ compilation warnings across multiple categories that will become errors in future Kotlin/Compose releases. Warnings fall into three priority tiers based on urgency and impact.

## Priority Classification

### CRITICAL (Will Become Errors)
1. Context receivers → context parameters migration (3 files, 4 usages)
2. Deprecated Compose Material3 APIs (7 files)

### MEDIUM (Code Quality & Future-Proofing)
3. Exhaustive when with redundant else (2 files)
4. Annotation target warnings (5 files)

### LOW (Minor Issues)
5. Redundant casts/conversions (4 occurrences)
6. Miscellaneous deprecations (3 files)

---

## Phase 1: Context Receivers → Context Parameters

**Affected Files:**
- `app/src/main/java/eu/kanade/presentation/util/ExceptionFormatter.kt:12`
- `app/src/main/java/eu/kanade/presentation/util/FastScrollAnimateItem.kt:7`
- `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/OkHttpExtensions.kt:137,142`

### Decision Point: Skip or Migrate?

**Recommendation: SKIP for now, add TODO comments**

**Rationale:**
1. **Breaking changes**: Context parameters have different syntax than context receivers - this is a major API change
2. **Caller impact**: All call sites would need updating (potentially 100+ locations)
3. **Not yet stabilized**: Context parameters are still experimental in Kotlin
4. **Low urgency**: Warnings won't become errors until Kotlin removes `-Xcontext-receivers` flag

**Action:**
- Add TODO comments noting migration needed when context parameters stabilize
- Document that `-Xcontext-receivers` flag in `build.gradle.kts` should be changed to `-Xcontext-parameters` when ready
- Keep monitoring Kotlin releases for stabilization timeline

---

## Phase 2: Deprecated Compose Material3 APIs

### 2.1 Auto-Mirrored Icons (High Priority - RTL Support)

**Issue:** Icons need auto-mirrored variants for proper RTL language support

#### File: `CategoryPinDialog.kt:176`
**Change:**
```kotlin
// Before
import androidx.compose.material.icons.filled.Backspace
Icon(imageVector = Icons.Default.Backspace, ...)

// After
import androidx.compose.material.icons.automirrored.filled.Backspace
Icon(imageVector = Icons.AutoMirrored.Filled.Backspace, ...)
```

#### File: `CalendarHeader.kt:57,60`
**Changes:**
```kotlin
// Before
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
Icons.Default.KeyboardArrowLeft
Icons.Default.KeyboardArrowRight

// After
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
Icons.AutoMirrored.Filled.KeyboardArrowLeft
Icons.AutoMirrored.Filled.KeyboardArrowRight
```

### 2.2 ClipboardManager → Clipboard

**File:** `TrackerSearch.kt:58,243`

**Issue:** `ClipboardManager` is deprecated in favor of suspend-function-supporting `Clipboard`

**Current usage:**
```kotlin
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
val clipboardManager: ClipboardManager = LocalClipboardManager.current
```

**Investigation needed:** Check if code uses any clipboard operations - if so, they'll need to become suspend functions or use rememberCoroutineScope()

### 2.3 Tooltip Position Provider

**File:** `AppBar.kt:198,223,352,372` (4 occurrences)

**Issue:** `rememberPlainTooltipPositionProvider()` deprecated

**Decision Point:** Need to verify current Material3 version and replacement API. Two scenarios:
1. If using Material3 1.2.0+: Can simply remove the `positionProvider` parameter (uses default)
2. If older: May need to update Material3 dependency first

**Approach:** Try removing `positionProvider` parameter entirely and test tooltip positioning

### 2.4 ColorScheme Constructor

**File:** `MonetColorScheme.kt:85`

**Issue:** Constructor deprecated in favor of version with additional 'fixed' container roles

**Current:** Uses 36-parameter constructor
**New:** Needs additional fixed color parameters (fixedPrimary, fixedDimPrimary, onFixed, etc.)

**Action:** Update ColorScheme constructor to include new fixed color role parameters (Material3 1.3.0+)

---

## Phase 3: Code Quality Improvements

### 3.1 Exhaustive When with Redundant Else

**Files:**
- `SyncFavoritesProgressDialog.kt:173`
- `AboutScreen.kt:248`

**Fix:** Remove redundant `else` branches from exhaustive when expressions

### 3.2 Annotation Target Warnings

**Files with `-Xannotation-default-target=param-property` warnings:**
- `Preference.kt:61`
- `ReaderViewModel.kt:1343`
- `ReadingMode.kt:16`
- `RecommendationSearchHelper.kt:231`
- `MigrationListScreenModel.kt:390`

**Fix:** Add `@param:` target prefix to annotations on value parameters

---

## Phase 4: Minor Cleanups

### 4.1 Redundant Casts/Conversions
- `EhLoginWebViewScreen.kt:83` - Remove unnecessary cast
- `WebViewScreenContent.kt:276` - Remove unnecessary cast
- `Kavita.kt:140` - Remove redundant conversion
- `ReaderViewModel.kt:646,839` - Remove redundant conversions

### 4.2 Deprecated APIs (Low Priority)
- `DebugInfoScreen.kt:81` - `RESULT_CODE_NO_PROFILE` (Java deprecation)
- `OpenSourceLicensesScreen.kt:30` - LibrariesContainer variant
- `EditTextPreferenceExtensions.kt:8` - Shadowed extension

---

## Implementation Order

### Batch 1: Auto-Mirrored Icons (Quick Wins)
1. CategoryPinDialog.kt - Backspace icon
2. CalendarHeader.kt - Arrow icons

**Estimated time:** 5 minutes
**Risk:** Low - simple import and reference changes

### Batch 2: Code Quality Fixes
3. Remove exhaustive when else clauses (2 files)
4. Fix annotation targets (5 files)
5. Remove redundant casts (4 files)

**Estimated time:** 10 minutes
**Risk:** Very low - compiler-verified mechanical changes

### Batch 3: Compose API Migrations
6. ClipboardManager → Clipboard (TrackerSearch.kt)
7. Tooltip position provider (AppBar.kt)
8. ColorScheme constructor (MonetColorScheme.kt)

**Estimated time:** 20-30 minutes
**Risk:** Medium - may require testing UI behavior

### Batch 4: Context Parameters (Deferred)
9. Add TODO comments for context receiver migration
10. Document flag change needed in build files

**Estimated time:** 5 minutes
**Risk:** None - documentation only

---

## Critical Files to Modify

**High Priority:**
1. `app/src/main/java/eu/kanade/presentation/library/components/CategoryPinDialog.kt`
2. `app/src/main/java/mihon/feature/upcoming/components/calendar/CalendarHeader.kt`
3. `app/src/main/java/eu/kanade/presentation/components/AppBar.kt`
4. `app/src/main/java/eu/kanade/presentation/track/TrackerSearch.kt`
5. `app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt`

**Medium Priority:**
6. `app/src/main/java/eu/kanade/presentation/library/components/SyncFavoritesProgressDialog.kt`
7. `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt`
8. Files with annotation warnings (5 files)

**Low Priority:**
9. Files with redundant casts (4 files)
10. Misc deprecations (3 files)

---

## Verification Plan

### After Each Batch:
1. **Compile check:** Run `./gradlew assembleDebug` to verify no new errors introduced
2. **Warning count:** Verify warning count decreases as expected

### After All Changes:
1. **Full build:** `./gradlew clean assembleDebug`
2. **Warning audit:** Confirm all targeted warnings resolved
3. **UI testing:** Test affected UI components:
   - CategoryPinDialog - verify backspace button works
   - Calendar navigation - verify arrow buttons work (test RTL if possible)
   - AppBar tooltips - verify tooltips display correctly
   - Tracker search - verify clipboard operations work

### Regression Testing:
- Library screen with category locks
- Calendar navigation in upcoming section
- Tracker search and copy functionality
- Settings screens (for annotation changes)

---

## Notes & Considerations

1. **Material3 version dependency:** Some fixes require specific Material3 versions - verify `libs.versions.toml`
2. **Context parameters:** Major breaking change - defer until Kotlin stabilizes feature
3. **RTL testing:** Auto-mirrored icons should be tested with RTL locale if possible
4. **Clipboard migration:** May require adding coroutine scopes - needs careful review of usage patterns
5. **ColorScheme fixed colors:** May need design input on appropriate values for new fixed color roles

---

## Success Criteria

- [ ] Zero compilation warnings in target categories
- [ ] All UI components function identically
- [ ] No regression in existing functionality
- [ ] Code follows modern Compose/Kotlin best practices
- [ ] Changes documented in commit messages
