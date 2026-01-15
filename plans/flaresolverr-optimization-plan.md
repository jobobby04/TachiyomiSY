# Flaresolverr Integration - Code Review and Optimization Plan

## Overview
This plan addresses import issues, security concerns, code optimization, and adherence to project standards in the Flaresolverr integration implementation.

## Critical Files
- `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/FlareSolverrInterceptor.kt`
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt`
- `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/AndroidCookieJar.kt`
- `i18n-sy/src/commonMain/moko-resources/base/strings.xml`
- `i18n/src/commonMain/moko-resources/base/strings.xml`

## Implementation Steps

### 1. Fix FlareSolverrInterceptor.kt

**Replace Log.d() with logcat():**
- Add import: `import tachiyomi.core.common.util.system.logcat`
- Add import: `import logcat.LogPriority`
- Remove import: `import android.util.Log`
- Replace all `Log.d(tag, message)` calls with `logcat(LogPriority.DEBUG) { message }`
- Replace `Log.e(tag, message, e)` with `logcat(LogPriority.ERROR, e) { message }`

**Remove sensitive cookie logging (SECURITY):**
- Line 157: Remove or redact cookie value logging
- Line 164: Remove cookie string logging
- Line 176: Remove cookie verification logging
- Line 180: Remove final cookies logging
- Keep only minimal logging for debugging (e.g., "Received N cookies", not actual values)

**Fix flareSolverrUrl initialization:**
- Line 66: Change from `private val flareSolverrUrl = networkPreferences.flareSolverrUrl().get()`
- To: Fetch URL on-demand in `resolveWithFlareSolverr()` function
- This ensures URL updates are reflected without restarting the app

**Reuse existing CloudflareBypassException:**
- Line 208: Remove the local `CloudflareBypassException` class definition
- Line 188: Update throw to use existing exception from CloudflareInterceptor
- Import: `import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException`
- Update call to: `throw CloudflareBypassException("Failed to solve challenge: ${flareSolverResponse.message}")`

**Add URL validation:**
- Before making FlareSolverr request, validate URL is not blank
- Add check: `require(flareSolverrUrl.isNotBlank()) { "FlareSolverr URL is not configured" }`

### 2. Fix SettingsAdvancedScreen.kt

**Add missing imports:**
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.network.interceptor.FlareSolverrInterceptor
```

**Replace custom OkHttpClient with networkHelper.client:**
- Line 887: Remove `val client = OkHttpClient.Builder().build()`
- Lines 885-886: Remove json and jsonMediaType declarations (use from injected dependencies)
- Line 893-906: Use `networkHelper.client` instead of `client`
- Add networkHelper dependency: Access via `val networkHelper = remember { Injekt.get<NetworkHelper>() }` at function level

**Simplify toast message calls:**
- Line 913-915: Change to `context.toast(SYMR.strings.flare_solver_user_agent_update_success)`
- Line 918-920: Change to `context.toast(SYMR.strings.flare_solver_update_user_agent_failed)`
- Remove intermediate `message` variable declarations

**Fix logcat spacing:**
- Line 925: Remove space between `logcat` and `(`
- Change from `logcat (LogPriority.ERROR, tag = "FlareSolverr")`
- To: `logcat(LogPriority.ERROR, tag = "FlareSolverr")`

**Optimize function structure:**
- Instead of creating new Json instance, use the one from NetworkHelper or inject it properly
- Consider making this a proper dependency-injected component rather than inline function

### 3. Fix AndroidCookieJar.kt

**Replace Log.d() with logcat():**
- Add import: `import tachiyomi.core.common.util.system.logcat`
- Add import: `import logcat.LogPriority`
- Remove import: `import android.util.Log`
- Replace all `Log.d("AndroidCookieJar", message)` with `logcat(LogPriority.DEBUG) { message }`

**Remove sensitive cookie logging (SECURITY):**
- Line 61: Remove or redact cookie value logging
- Line 74: Remove cookie value logging
- Line 80: Remove full cookie string logging
- Line 85: Remove cookie verification logging
- Keep only high-level logging like "Adding N cookies to URL" without actual cookie values

**Consider removing all debug logs:**
- Since this is production code and cookies shouldn't be logged, consider removing all debug logs in this file
- Keep only error logging if needed

### 4. Fix String Resources

**Fix i18n-sy/strings.xml formatting:**
- Line 160: Fix attribute spacing
- Change from `<string name= "flare_solver_error">`
- To: `<string name="flare_solver_error">`

**Consolidate string resources:**
- Review if FlareSolverr strings in `i18n/strings.xml` (lines 663-668) should be removed
- SY-specific features should only have strings in `i18n-sy/strings.xml`
- Keep: UI settings strings in main i18n (pref_enable_flare_solverr, pref_flare_solverr_url, etc.)
- Move: Toast message strings to i18n-sy only (flare_solver_user_agent_update_success, etc.) if they're not already there

### 5. Additional Optimizations

**FlareSolverrInterceptor improvements:**
- Consider adding timeout handling for FlareSolverr requests
- Add better error messages with specific failure reasons
- Consider making maxTimeout configurable via preferences

**Code style consistency:**
- Ensure all multiline function calls use consistent indentation
- Remove any trailing whitespace
- Ensure consistent import ordering (Android → Kotlin → Project)

## Verification Plan

### 1. Build Verification
- Run `./gradlew assembleDebug` to ensure no compilation errors
- Verify all imports resolve correctly
- Check for any missing dependencies

### 2. Functionality Testing
- Enable FlareSolverr in settings
- Test FlareSolverr bypass on a Cloudflare-protected site
- Verify "Test FlareSolverr and update user agent" button works
- Confirm cookies are set correctly after bypass
- Test with FlareSolverr disabled to ensure graceful fallback

### 3. Security Verification
- Review logs to confirm no cookie values are being logged
- Verify sensitive data is not exposed in debug builds
- Check that error messages don't leak sensitive information

### 4. Code Quality Checks
- Run linter/formatter if available
- Verify all logcat() calls follow project standards
- Confirm all uses of Kotlinx.Serialization instead of org.json
- Verify all network calls use .awaitSuccess() pattern

## Summary of Changes

**Security fixes:**
- Remove all cookie value logging (8+ locations)
- Replace Log.d() with logcat() for better control (15+ locations)

**Import fixes:**
- Add 10+ missing imports in SettingsAdvancedScreen.kt
- Add logcat imports to FlareSolverrInterceptor.kt and AndroidCookieJar.kt
- Remove duplicate CloudflareBypassException

**Code optimization:**
- Use networkHelper.client instead of creating new OkHttpClient
- Fetch FlareSolverr URL on-demand instead of at initialization
- Simplify toast message calls
- Fix string resource formatting

**Standards compliance:**
- All logging uses logcat() ✓
- All network calls use .awaitSuccess() ✓
- Uses Kotlinx.Serialization ✓
- SY strings in i18n-sy ✓
- No debug logs with sensitive data ✓
