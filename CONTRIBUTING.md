Looking to report an issue/bug or make a feature request? Please refer to the [README file](/README.md#issues-feature-requests-and-contributing).

---

Thanks for your interest in contributing to Tachiyomi!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/jobobby04/TachiyomiSY/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.

## Getting help

- Join [the Discord server](https://discord.gg/mihon) for online help and to ask questions while developing.

# Translations

Translations are done externally via Weblate. See [our website](https://mihon.app/docs/contribute#translation) for more details.


# Forks

Forks are allowed so long as they abide by [the project's LICENSE](/LICENSE).

When creating a fork, remember to:

- To avoid confusion with the main app:
    - Change the app name
    - Change the app icon
    - Change or disable the [app update checker](/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
- To avoid installation conflicts:
    - Change the `applicationId` in [`build.gradle.kts`](/app/build.gradle.kts)



# SY Information
### Supporting Cloud Sync - Google Drive Implementation
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project
3. Go to API & Services -> Library -> Google Drive API and click enable
4. Go to API & Services -> Oauth consent screen
5. Create it, fill in the app name, user support email, and developer contact information
6. In the next screen, click add or remove scopes, and add the `.../auth/drive.appdata` and `.../auth/drive.file` scopes
7. Don't add any test users and go back to the dashboard
8. Click publish
9. Go to API & Services -> Credentials
10. Click Create credentials -> Oauth client ID
11. Select Android, give it a name, and set `eu.kanade.google.oauth` as the package name
12. To get the SHA-1 key, run `keytool -printcert -jarfile app-standard-universal-release.apk` on your apk, and copy the listed SHA-1
13. Expand advanced settings, and enable Custom URL scheme
14. After that just download the json, name it to `client_secrets.json` and put it in `app/src/main/assets/`


## SY code markers

TachiyomiSY is a fork of [Mihon](https://github.com/mihonapp/mihon). Upstream commits are brought in by **cherry-picking**, so all SY-specific code must be wrapped in markers to survive merge conflicts.

**Any code not present in upstream Mihon** — new features, fields, branches, dependencies, whole files — must be marked. This applies to code outside of imports.

### Block markers (entire lines are SY-specific)

```kotlin
// SY -->
val historyUpdates = mutableListOf<HistoryUpdate>()
// SY <--
```

### Inline markers (part of a line is SY-specific)

```kotlin
private val /* SY --> */ newName /* SY <-- */: Test
restoreRepository.restoreManga(entries, /* SY --> */ isSync /* SY <-- */) { ... }
```

### Rules

- **Be as narrow as possible.** Mark only the SY-specific token, parameter, or statement — not the surrounding Mihon code. If only one argument in a call is SY-specific, mark that argument, not the whole call.
- **Indentation.** Block markers must be indented to match the code they surround — not left-aligned to column zero.
- **Language-aware comments.** Use the comment syntax of the language: `//` for Kotlin, `/* */` for inline Kotlin, `--` for SQL, `#` for shell/Python, `<!-- -->` for XML/HTML.

### SY-specific files

Whole files with no upstream Mihon counterpart do **not** use inline markers. Instead, place a file marker directly below the imports (after the last `import`, or after `package` if there are no imports), separated by a blank line:

```kotlin
import foo.bar

/**
 * SY-specific file
 */
class Baz { ... }
```

**Existing KDoc.** If the first declaration already has a KDoc, merge the marker into it as the first content line. Two adjacent KDoc blocks trigger a ktlint `dangling toplevel KDoc` error:

```kotlin
/**
 * SY-specific file
 * Existing description of the class.
 */
class Baz { ... }
```
